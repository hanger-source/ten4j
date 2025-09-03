package source.hanger.core.extension.base;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.base.tool.LLMTool;
import source.hanger.core.extension.base.tool.LLMToolResult;
import source.hanger.core.extension.base.tool.ParameterlessLLMTool;
import source.hanger.core.message.Message;
import source.hanger.core.message.VideoFrameMessage;
import source.hanger.core.message.command.Command;
import source.hanger.core.tenenv.TenEnv;
import source.hanger.core.util.ImageUtils;
import source.hanger.core.util.LatestNBuffer;

import static source.hanger.core.common.ExtensionConstants.DATA_OUT_PROPERTY_TEXT;

/**
 * 基于视觉的LLM扩展，使用RingBuffer进行高效的视频帧处理。
 *
 * @param <MESSAGE>       LLM消息类型
 * @param <TOOL_FUNCTION> LLM工具函数类型
 */
@Slf4j
public abstract class BaseVisionExtension<MESSAGE, TOOL_FUNCTION> extends BaseLLMToolExtension<MESSAGE, TOOL_FUNCTION> {

    private static final int MAX_VIDEO_FRAME_COUNT = 10;
    private static final int VIDEO_FRAME_COUNT = 4;
    // 定义每帧图像的最大字节大小，例如 2MB
    private static final int MAX_FRAME_BYTE_SIZE = 2 * 1024 * 1024;

    // 使用 LatestNBuffer 作为帧缓冲，直接管理最新N帧
    private LatestNBuffer latestNBuffer;
    // 生产者线程池，使用虚拟线程处理帧转换
    private ExecutorService producerExecutor;

    @Override
    protected List<LLMTool> initTools(TenEnv env) {
        return List.of(new VisionTool(env));
    }

    @Override
    protected void onExtensionConfigure(TenEnv env, Map<String, Object> properties) {
        super.onExtensionConfigure(env, properties);
        producerExecutor = Executors.newVirtualThreadPerTaskExecutor();
        // 初始化 LatestNBuffer
        this.latestNBuffer = new LatestNBuffer(MAX_VIDEO_FRAME_COUNT, MAX_FRAME_BYTE_SIZE);
        log.info("[{}] LatestNBuffer 初始化完成，容量：{} 帧，每帧最大 {} 字节", env.getExtensionName(), MAX_VIDEO_FRAME_COUNT, MAX_FRAME_BYTE_SIZE);
    }

    @Override
    public void onStop(TenEnv env) {
        super.onStop(env);
        // 优雅地关闭生产者线程池
        producerExecutor.shutdown();
        log.info("[{}] 生产者线程池已关闭", env.getExtensionName());
    }

    @Override
    protected void onUserTextInput(TenEnv env, String userText, Message originalMessage) {
        // 将用户输入添加到上下文
        if (!userText.isEmpty()) {
            llmContextManager.onUserVideoMsg(userText, latestNBuffer.getLatest(VIDEO_FRAME_COUNT));

            List<MESSAGE> messagesForLlm = llmContextManager.getMessagesForLLM();
            List<TOOL_FUNCTION> registeredTools = LLMToolOrchestrator.getRegisteredToolFunctions();
            // 请求 LLM 并处理流
            llmStreamAdapter.onRequestLLMAndProcessStream(env, messagesForLlm, registeredTools, originalMessage);
        }
    }

    @Override
    public void onVideoFrame(TenEnv env, VideoFrameMessage videoFrame) {
        // 使用虚拟线程处理，避免阻塞主线程
        producerExecutor.submit(() -> {
            try {
                // 将视频帧转换为 Base64 JPEG 字符串
                String base64Image = ImageUtils.convertVideoFrameToJpegBase64(videoFrame.getData(),
                    videoFrame.getWidth(), videoFrame.getHeight(), videoFrame.getPixelFormat());

                if (base64Image != null) {
                    // 将 Base64 图像添加到 LatestNBuffer
                    latestNBuffer.add(base64Image);
                    log.info("[{}] 成功将 Base64 图片添加到 LatestNBuffer。", env.getExtensionName());
                } else {
                    log.error("[{}] 转换视频帧到 JPEG Base64 失败。", env.getExtensionName());
                }
            } catch (Exception e) {
                log.error("[{}] 处理视频帧时出错：{}", env.getExtensionName(), e.getMessage());
            }
        });
    }

    protected String visionToolDescription(TenEnv env) {
        return """
          - 用于分析用户摄像头捕获的实时画面，以增强模型对用户当前环境的感知。
          - 工具本身无需输出给用户，调用与否仅用于辅助模型决策。
          回答规范：
          1. 当调用后拿到画面信息时，回答时应自然承认自己“能看到”或“注意到”这些视觉细节，
             不要说“我看不到”或“我无法看到”，以避免违和感。
          2. 如果没有触发调用，不要主动声称“我能看到”或“我看不到”，只需正常对话。
          调用规则：
          1. 仅在用户的当前输入中明确或隐含提及视觉内容时调用：
             - 明确请求：
               - “你能看到我吗？”
               - “画面里有什么？”
               - “我的背景如何？”
               - “你能看到摄像头吗？”
             - 隐含请求：
               - “描述一下场景”
               - “你看到了什么？”
               - “我现在的环境怎么样？”
             - 连续追问：
               - 仅当用户在短时间内连续提出与画面直接相关的问题，才继续调用。
          2. OCR识别规则：
               - 当用户明确请求识别文字时，立即调用OCR：
                 - “帮我读一下这份文件。”
                 - “这个网页上写了什么？”
               - 当画面包含大量可读文字（如文档、网页截图）且用户问题暗示与文字内容相关时，优先调用OCR。
          2. 禁止调用场景：
             - 用户的问题与视觉完全无关（如文本、代码、新闻、天气等）。
             - 用户未涉及查看画面或感知环境的需求。
             - 根据上下文，无法明确判断用户是否在询问视觉内容时，默认不调用。
             - 如果上下文已经有画面的描述，可以不调用，除非用户明确重新分析画面

          """.stripIndent();
    }

    protected String visionToolName(TenEnv env) {
        return "vision";
    }

    @AllArgsConstructor
    class VisionTool implements ParameterlessLLMTool {
        private TenEnv env;

        @Override
        public LLMToolResult runTool(TenEnv env, Command command, Map<String, Object> args) {
            String prompt = (String)command.getProperty(DATA_OUT_PROPERTY_TEXT);
            onUserTextInput(env, prompt, command);
            return LLMToolResult.noop("已分析摄像头实时画面");
        }

        @Override
        public String getToolName() {
            return visionToolName(env);
        }

        @Override
        public String getDescription() {
            return visionToolDescription(env);
        }
    }
}
