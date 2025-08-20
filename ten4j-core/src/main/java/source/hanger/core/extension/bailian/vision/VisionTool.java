package source.hanger.core.extension.bailian.vision;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;

import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.system.tool.LLMTool;
import source.hanger.core.extension.system.tool.LLMToolResult;
import source.hanger.core.extension.system.tool.ToolMetadata;
import source.hanger.core.extension.system.tool.ToolMetadata.ToolParameter;
import source.hanger.core.message.command.Command;
import source.hanger.core.tenenv.TenEnv;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;

@Slf4j
public class VisionTool implements LLMTool {

    private static final String MODEL_NAME = "qwen-vl-max-latest";
    private static final int MAX_FRAMES_TO_COLLECT = 10; // Collect up to 10 frames
    private static final long FRAME_COLLECTION_TIMEOUT_MS = 1000; // Collect frames for up to 1 second
    private final BlockingQueue<String> imageQueue;

    public VisionTool(BlockingQueue<String> imageQueue) {
        this.imageQueue = imageQueue;
    }

    @Override
    public String getToolName() {
        return "get_vision_tool";
    }

    @Override
    public ToolMetadata getToolMetadata() {
        ToolParameter promptParameter = ToolParameter.builder()
            .name("prompt")
            .type("string")
            .description("The prompt to describe the image, e.g., 'describe the image' or 'what is in the image'.")
            .required(true)
            .build();

        return ToolMetadata.builder()
            .name(getToolName())
            .description(
                "Get the image from camera and describe it using a vision model. Call this whenever you need to "
                    + "understand the input camera image, for example when user asks 'What can you see?' or 'Can you "
                    + "see me?'")
            .parameters(singletonList(promptParameter))
            .build();
    }

    @Override
    public LLMToolResult runTool(TenEnv env, Command command, Map<String, Object> args) {
        log.info("[VisionTool] runTool called.");

        // 从 TenEnv 获取 API Key
        String apiKey = env.getPropertyString("api_key").orElse(null);
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("[VisionTool] DashScope API Key is not set in TenEnv. Please configure it.");
            return LLMToolResult.requery("DashScope API Key 未设置，请联系管理员。");
        }

        // 从队列获取图像数据列表
        List<String> base64Images = new ArrayList<>();
        long startTime = System.currentTimeMillis();
        while (base64Images.size() < MAX_FRAMES_TO_COLLECT
            && (System.currentTimeMillis() - startTime) < FRAME_COLLECTION_TIMEOUT_MS) {
            try {
                String image = imageQueue.poll(10, TimeUnit.MILLISECONDS); // Short poll to collect multiple frames
                if (image != null) {
                    base64Images.add("data:image/jpeg;base64,%s".formatted(image)); // Add prefix
                } else {
                    // If no more images are available immediately, break
                    break;
                }
            } catch (InterruptedException e) {
                log.error("[VisionTool] Interrupted while collecting images from queue: {}", e.getMessage());
                Thread.currentThread().interrupt();
                return LLMToolResult.requery("收集图像数据时中断，请重试。");
            }
        }

        if (base64Images.isEmpty()) {
            log.warn("[VisionTool] No image data available from queue for get_vision_tool.");
            return LLMToolResult.requery("队列中无图像数据，请稍后重试或确保已发送视频帧。");
        }

        String prompt = command.getPropertyString("prompt").orElse(null); // Get prompt from command properties
        if (prompt == null || prompt.isEmpty()) {
            log.warn("[VisionTool] Prompt is missing for get_vision_tool.");
            return LLMToolResult.requery("Prompt is required for get_vision_tool.");
        }

        MultiModalConversation conv = new MultiModalConversation();

        try {
            MultiModalMessage systemMessage = MultiModalMessage.builder()
                .role(Role.SYSTEM.getValue())
                .content(List.of(singletonMap("text", "You are a helpful assistant who can describe images.")))
                .build();

            // 构建视频内容
            Map<String, Object> videoContent = new HashMap<>();
            videoContent.put("video", base64Images);
            videoContent.put("fps", 1); // Set fps based on frontend sending rate (1 FPS)

            MultiModalMessage userMessage = MultiModalMessage.builder()
                .role(Role.USER.getValue())
                .content(Arrays.asList(videoContent, singletonMap("text", prompt)))
                .build();

            MultiModalConversationParam param = MultiModalConversationParam.builder()
                .apiKey(apiKey)
                .model(MODEL_NAME)
                .messages(Arrays.asList(systemMessage, userMessage)) // Use messages instead of message
                .build();

            MultiModalConversationResult result = conv.call(param);

            if (result != null && result.getOutput() != null && result.getOutput().getChoices() != null
                && !result.getOutput().getChoices().isEmpty()) {
                String description = result.getOutput().getChoices().get(0).getMessage().getContent().get(0).get("text")
                    .toString();
                log.info("[VisionTool] DashScope Vision Model response: {}", description);
                return LLMToolResult.llmResult(true, description);
            } else {
                log.warn("[VisionTool] No valid response from DashScope Vision Model.");
                return LLMToolResult.llmResult(false, "未能从视觉模型获取有效描述。");
            }

        } catch (ApiException | NoApiKeyException | UploadFileException e) {
            log.error("[VisionTool] Error calling DashScope Vision Model: {}", e.getMessage());
            return LLMToolResult.llmResult(false, "调用视觉模型时发生错误: {}", e.getMessage());
        } catch (Exception e) {
            log.error("[VisionTool] Unexpected error in VisionTool: {}", e.getMessage());
            return LLMToolResult.llmResult(false, "视觉工具内部发生未知错误。");
        }
    }
}
