package source.hanger.core.extension.bailian.image;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesis;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisParam;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisResult;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.NoApiKeyException;

import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.system.tool.LLMTool;
import source.hanger.core.extension.system.tool.LLMToolResult;
import source.hanger.core.extension.system.tool.ToolMetadata;
import source.hanger.core.message.DataMessage;
import source.hanger.core.message.command.Command;
import source.hanger.core.tenenv.TenEnv;

import static source.hanger.core.common.ExtensionConstants.CONTENT_DATA_OUT_NAME;
import static source.hanger.core.common.ExtensionConstants.DATA_OUT_PROPERTY_ROLE;

/**
 * ImageSynthesisTool 是一个 LLM 工具，用于通过 DashScope API 生成图片。
 */
@Slf4j
public class ImageSynthesisTool implements LLMTool {

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(); // For polling
    private static final int MAX_POLLING_ATTEMPTS = 30; // Max attempts to fetch result
    private static final long POLLING_INTERVAL_SECONDS = 2; // Poll every 2 seconds

    private static void sendImageData(TenEnv tenEnv, Command command, String imageUrl) {
        DataMessage dataMessage = DataMessage.create(CONTENT_DATA_OUT_NAME);
        dataMessage.setProperty(DATA_OUT_PROPERTY_ROLE, "assistant");
        dataMessage.setProperty("data", imageUrl);
        dataMessage.setProperty("type", "image_url");
        dataMessage.setProperty("group_timestamp", command.getTimestamp());
        tenEnv.sendData(dataMessage);
    }

    @Override
    public String getToolName() {
        return "qwen_image_generate_tool";
    }

    @Override
    public ToolMetadata getToolMetadata() {
        // 定义工具的元数据，包括名称、描述和参数
        return  new ToolMetadata("qwen_image_generate_tool",
                        "当用户想要生成图片时非常有用。可以根据用户需求生成图片。",
                        List.of(
                                new ToolMetadata.ToolParameter(
                                        "prompt",
                                        "string",
                                        "正向提示词，用来描述生成图像中期望包含的元素和视觉特点。支持中英文，长度不超过800个字符。示例：一只坐着的橘黄色的猫，表情愉悦，活泼可爱，逼真准确。",
                                        true
                                ),
                                new ToolMetadata.ToolParameter(
                                        "n",
                                        "integer",
                                        "生成图片的数量。取值范围为1~4张，默认为1；请甄别用户需求，注意该参数为图片张数，不是图片中的元素数量",
                                        false
                                ),
                                new ToolMetadata.ToolParameter(
                                        "size",
                                        "string",
                                        "输出图像的分辨率。默认值是512*512。图像宽高边长的像素范围为：[512, 1440]，单位像素。",
                                        false
                                )
                        )
                );
    }

    @Override
    public LLMToolResult runTool(TenEnv tenEnv, Command command, Map<String, Object> args) {
        log.info("[{}] 执行工具 qwen_image_generate_tool，参数: {}", tenEnv.getExtensionName(), args);

        String prompt = (String) args.get("prompt");
        Integer n = (Integer) args.getOrDefault("n", 1); // 默认生成1张图片
        String size = (String) args.getOrDefault("size", "1024*1024"); // 默认尺寸

        if (prompt == null || prompt.isEmpty()) {
            String errorMsg = "[%s] 图片生成工具：缺少 'prompt' 参数。".formatted(tenEnv.getExtensionName());
            log.warn(errorMsg);
            return LLMToolResult.llmResult(false, errorMsg);
        }

        // 从 TenEnv 获取 API Key
        String apiKey = tenEnv.getPropertyString("api_key").orElse(null);
        if (apiKey == null || apiKey.isEmpty()) {
            String errorMsg = "[%s] DashScope API Key 未设置，无法生成图片。".formatted(tenEnv.getExtensionName());
            log.error(errorMsg);
            return LLMToolResult.llmResult(false, errorMsg);
        }

        ImageSynthesisParam param = ImageSynthesisParam.builder()
                .apiKey(apiKey)
                .model("wan2.2-t2i-flash")
                .prompt(prompt)
                .n(n)
                .size(size)
                .build();

        ImageSynthesis imageSynthesis = new ImageSynthesis();
        log.info("[{}] 调用 DashScope 图像合成 API (同步启动异步任务)，prompt: {}", tenEnv.getExtensionName(), prompt);

        try {
            // 同步调用 asyncCall，获取初始结果（包含 taskId）
            ImageSynthesisResult initialResult = imageSynthesis.asyncCall(param);
            String taskId = initialResult.getOutput().getTaskId(); // 假设 requestId 即为 taskId

            if (taskId == null || taskId.isEmpty()) {
                String errorMsg = "[%s] DashScope 异步调用返回结果中未找到 taskId。".formatted(tenEnv.getExtensionName());
                log.error(errorMsg);
                return LLMToolResult.llmResult(false, errorMsg);
            }
            log.info("[{}] 图片生成任务已启动，taskId: {}", tenEnv.getExtensionName(), taskId);

            // 开始 Polling 结果
            pollImageSynthesisResult(taskId, apiKey, tenEnv, command, imageSynthesis, 0);

        } catch (ApiException | NoApiKeyException e) {
            String errorMsg = "[%s] DashScope API 异步调用启动失败: %s".formatted(tenEnv.getExtensionName(), e.getMessage());
            log.error(errorMsg, e);
            return LLMToolResult.llmResult(false, errorMsg);
        } catch (Exception e) {
            String errorMsg = "[%s] 图片生成工具启动异常: %s".formatted(tenEnv.getExtensionName(), e.getMessage());
            log.error(errorMsg, e);
            return LLMToolResult.llmResult(false, errorMsg);
        }

        // 立即返回，表示异步任务已成功启动
        return LLMToolResult.llmResult(true, "图片生成已开始。");
    }

    private void pollImageSynthesisResult(String taskId, String apiKey, TenEnv tenEnv, Command command, ImageSynthesis imageSynthesis, int attempt) {
        if (attempt >= ImageSynthesisTool.MAX_POLLING_ATTEMPTS) {
            log.error("[{}] 图片生成任务 Polling 达到最大尝试次数，任务可能失败或超时。taskId: {}", tenEnv.getExtensionName(), taskId);
            return;
        }

        ImageSynthesisTool.SCHEDULER.schedule(() -> {
            try {
                // Fetch the task result
                ImageSynthesisResult fetchedResult = imageSynthesis.fetch(taskId, apiKey);

                // 处理结果
                if (fetchedResult.getOutput() != null
                    && fetchedResult.getOutput().getResults() != null
                    && !fetchedResult.getOutput().getResults().isEmpty()) {
                    String imageUrl = fetchedResult.getOutput().getResults().getFirst().get("url");
                    log.info("[{}] 图片生成成功，URL: {}", tenEnv.getExtensionName(), imageUrl);

                    try {
                        sendImageData(tenEnv, command, imageUrl);
                        log.info("[{}] 已发送图片 URL 作为数据消息。", tenEnv.getExtensionName());
                    } catch (Exception e) {
                        String errorMsg = "[%s] 序列化图片数据失败: %s".formatted(tenEnv.getExtensionName(), e.getMessage());
                        log.error(errorMsg, e);
                    }
                } else if (fetchedResult.getOutput() == null || !"SUCCEEDED".equals(
                    fetchedResult.getOutput().getTaskStatus())) { // 检查 output 是否为 null 或未成功表示进行中
                    // 任务未完成或没有结果，继续 Polling
                    log.info("[{}] 图片生成任务进行中，继续Polling... taskId: {}, attempt: {}", tenEnv.getExtensionName(), fetchedResult.getOutput().getTaskId(), attempt + 1);
                    pollImageSynthesisResult(fetchedResult.getOutput().getTaskId(), apiKey, tenEnv, command,
                        imageSynthesis, attempt + 1);
                } else {
                    // 意外的 null 结果或其他问题
                    String errorDetail = fetchedResult.getOutput() != null && fetchedResult.getOutput().getResults()
                        .isEmpty() ?
                            "Fetched result output is empty" : "Unexpected null fetchedResult or output for taskId: %s".formatted(
                        fetchedResult.getRequestId());
                    String errorMsg = "[%s] 图片生成失败: %s".formatted(tenEnv.getExtensionName(), errorDetail);
                    log.error(errorMsg);
                }
            } catch (ApiException | NoApiKeyException e) {
                // 处理 Fetch 过程中的 API 错误
                String errorMsg = "[%s] DashScope API Fetch 调用失败: %s".formatted(tenEnv.getExtensionName(), e.getMessage());
                log.error(errorMsg, e);
            } catch (Exception e) {
                // 处理 Polling 过程中的其他异常
                String errorMsg = "[%s] 图片生成工具 Polling 异常: %s".formatted(tenEnv.getExtensionName(), e.getMessage());
                log.error(errorMsg, e);
            }
        }, ImageSynthesisTool.POLLING_INTERVAL_SECONDS, TimeUnit.SECONDS); // 下一次 Polling 之前的延迟
    }
}
