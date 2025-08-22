package source.hanger.core.extension.base;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import lombok.extern.slf4j.Slf4j;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.ManyToOneRingBuffer;
import org.agrona.concurrent.ringbuffer.RingBuffer;
import org.agrona.concurrent.ringbuffer.RingBufferDescriptor;
import source.hanger.core.extension.base.tool.LLMTool;
import source.hanger.core.extension.base.tool.LLMToolMetadata;
import source.hanger.core.extension.base.tool.LLMToolMetadata.ToolParameter;
import source.hanger.core.extension.base.tool.LLMToolResult;
import source.hanger.core.extension.component.tool.ExtensionToolDelegate;
import source.hanger.core.message.DataMessage;
import source.hanger.core.message.Message;
import source.hanger.core.message.VideoFrameMessage;
import source.hanger.core.message.command.Command;
import source.hanger.core.tenenv.TenEnv;
import source.hanger.core.util.ImageUtils;

import static source.hanger.core.common.ExtensionConstants.CMD_TOOL_CALL;
import static source.hanger.core.common.ExtensionConstants.DATA_OUT_PROPERTY_IS_FINAL;
import static source.hanger.core.common.ExtensionConstants.DATA_OUT_PROPERTY_TEXT;

/**
 * 基于视觉的LLM扩展，使用RingBuffer进行高效的视频帧处理。
 *
 * @param <MESSAGE>       LLM消息类型
 * @param <TOOL_FUNCTION> LLM工具函数类型
 */
@Slf4j
public abstract class BaseVisionExtension<MESSAGE, TOOL_FUNCTION> extends BaseLLMExtension<MESSAGE, TOOL_FUNCTION> {

    private static final int VIDEO_FRAME_COUNT = 0;
    // 使用RingBuffer作为帧缓冲
    private RingBuffer imageRingBuffer;
    // 生产者线程池，使用虚拟线程处理帧转换
    private ExecutorService producerExecutor;

    private ExtensionToolDelegate extensionToolDelegate;

    @Override
    protected void onExtensionConfigure(TenEnv env, Map<String, Object> properties) {
        super.onExtensionConfigure(env, properties);
        producerExecutor = Executors.newVirtualThreadPerTaskExecutor();

        // 缓冲区容量为 16MB，确保足够存放多帧图像数据
        final int bufferCapacity = 16 * 1024 * 1024 + RingBufferDescriptor.TRAILER_LENGTH;
        // 使用直接缓冲区，性能更高
        final UnsafeBuffer unsafeBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(bufferCapacity));
        this.imageRingBuffer = new ManyToOneRingBuffer(unsafeBuffer);
        log.info("[{}] RingBuffer 初始化完成，容量：{} 字节", env.getExtensionName(), bufferCapacity);

        extensionToolDelegate = new ExtensionToolDelegate() {
            @Override
            public List<LLMTool> initTools() {
                return List.of(new LLMTool() {
                    @Override
                    public LLMToolMetadata getToolMetadata() {
                        return new LLMToolMetadata("vision", "查看用户视频画面", List.of(new ToolParameter[] {
                            new ToolParameter("prompt", "string", "用户提示词", true)
                        }));
                    }

                    @Override
                    public LLMToolResult runTool(TenEnv env, Command command, Map<String, Object> args) {
                        String prompt = (String)args.get("prompt");
                        onTextWithVideo(command, prompt);
                        return LLMToolResult.noop(true);
                    }

                    @Override
                    public String getToolName() {
                        return "vision";
                    }
                });
            }
        };
    }

    @Override
    public void onStart(TenEnv env) {
        super.onStart(env);
        extensionToolDelegate.sendRegisterToolCommands(env);
    }

    @Override
    public void onCmd(TenEnv env, Command command) {
        if (CMD_TOOL_CALL.equals(command.getName())) {
            extensionToolDelegate.handleToolCallCommand(env, command);
        }
    }

    @Override
    public void onStop(TenEnv env) {
        super.onStop(env);
        // 优雅地关闭消费者线程
        producerExecutor.shutdown();
        log.info("[{}] 消费者线程和生产者线程池已关闭", env.getExtensionName());
    }

    @Override
    public void onDataMessage(TenEnv env, DataMessage dataMessage) {
        if (!isRunning()) {
            log.warn("[{}] Extension未运行，忽略 DataMessage。", env.getExtensionName());
            return;
        }

        String userText = dataMessage.getPropertyString(DATA_OUT_PROPERTY_TEXT).orElse("");
        if (!dataMessage.getPropertyBool(DATA_OUT_PROPERTY_IS_FINAL).orElse(false)) {
            log.info("[{}] LLM扩展收到非最终数据: text={}", env.getExtensionName(), userText);
            return;
        }

        onTextWithVideo(dataMessage, userText);

    }

    private void onTextWithVideo(Message message, String userText) {
        // 将用户输入添加到上下文
        if (!userText.isEmpty()) {
            llmContextManager.onUserVideoMsg(userText, readNMessagesFromRingBuffer(VIDEO_FRAME_COUNT));

            List<MESSAGE> messagesForLlm = llmContextManager.getMessagesForLLM();
            List<TOOL_FUNCTION> registeredTools = LLMToolOrchestrator.getRegisteredToolFunctions();
            // 请求 LLM 并处理流
            llmStreamAdapter.onRequestLLMAndProcessStream(env, messagesForLlm, registeredTools, message);
        }
    }

    @Override
    public void onVideoFrame(TenEnv env, VideoFrameMessage videoFrame) {
        // 使用虚拟线程处理，避免阻塞主线程
        producerExecutor.submit(() -> {
            try {
                // 将视频帧转换为 Base64 JPEG 字符串
                String base64Image = ImageUtils.convertVideoFrameToJpegBase64(videoFrame.getData(),
                    videoFrame.getWidth(), videoFrame.getHeight());

                if (base64Image != null) {
                    // 将字符串转为字节，以便写入RingBuffer
                    byte[] imageBytes = base64Image.getBytes(StandardCharsets.US_ASCII);
                    // 写入数据到 RingBuffer。注意：如果RingBuffer已满，将直接覆盖旧数据
                    boolean success = imageRingBuffer.write(1, new UnsafeBuffer(imageBytes), 0, imageBytes.length);
                    if (success) {
                        log.info("[{}] 成功写入最新 Base64 图片到 RingBuffer。", env.getExtensionName());
                    } else {
                        // 写入失败通常是由于RingBuffer容量已满且未及时消费，但在此设计中这正是预期行为（覆盖）
                        log.warn("[{}] 写入失败，可能因RingBuffer容量不足，旧数据将被覆盖。", env.getExtensionName());
                    }
                } else {
                    log.error("[{}] 转换视频帧到 JPEG Base64 失败。", env.getExtensionName());
                }
            } catch (Exception e) {
                log.error("[{}] 处理视频帧时出错：{}", env.getExtensionName(), e.getMessage());
            }
        });
    }

    /**
     * 从 RingBuffer 中读取指定数量的最新图像数据消息。
     * 此方法会读取并消费 RingBuffer 中最多 n 条类型为1（视频帧）的消息。
     * 如果 RingBuffer 中可用的消息少于 n 条，则读取所有可用消息。
     *
     * @param n 要读取的消息数量。
     * @return 包含最新图像数据字节数组的列表，如果无数据或 n <= 0 则返回空列表。
     */
    public List<String> readNMessagesFromRingBuffer(int n) {
        if (n <= 0) {
            return new ArrayList<>();
        }

        List<String> messages = new ArrayList<>(n);

        // 使用带 messageCountLimit 的 read 方法，只读取指定数量的消息
        // 同时指定 msgTypeId 为 1，确保只读取视频帧消息
        imageRingBuffer.read((msgTypeId, buffer, index, length) -> {
            byte[] data = new byte[length];
            buffer.getBytes(index, data); // 从 UnsafeBuffer 读取数据
            messages.add(new String(data));
        }, n);

        return messages;
    }

}
