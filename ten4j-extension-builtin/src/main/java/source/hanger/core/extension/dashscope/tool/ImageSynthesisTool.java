package source.hanger.core.extension.dashscope.tool;

import java.util.List;
import java.util.Map;

import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesis;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisParam;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisResult;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.NoApiKeyException;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import source.hanger.core.extension.base.tool.LLMTool;
import source.hanger.core.extension.base.tool.LLMToolMetadata;
import source.hanger.core.extension.base.tool.LLMToolResult;
import source.hanger.core.extension.dashscope.task.BailianPollingTask;
import source.hanger.core.extension.dashscope.task.BailianPollingTaskRunner;
import source.hanger.core.message.DataMessage;
import source.hanger.core.message.command.Command;
import source.hanger.core.tenenv.TenEnv;

import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static source.hanger.core.common.ExtensionConstants.CONTENT_DATA_OUT_NAME;
import static source.hanger.core.common.ExtensionConstants.DATA_OUT_PROPERTY_ROLE;

/**
 * ImageSynthesisTool 是一个 LLM 工具，用于通过 DashScope API 生成图片。
 */
@Slf4j
public class ImageSynthesisTool implements LLMTool {

    private static final long TOTAL_TASK_TIMEOUT_SECONDS = 10; // Total timeout for the image synthesis task
    private static final long DEFAULT_POLLING_INTERVAL_MILLIS = 300; // Default polling interval in seconds
    private final BailianPollingTaskRunner taskRunner;

    public ImageSynthesisTool() {
        this.taskRunner = new BailianPollingTaskRunner("ImageSynthesisTool");
    }

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
    public LLMToolMetadata getToolMetadata() {
        // 定义工具的元数据，包括名称、描述和参数
        return new LLMToolMetadata("qwen_image_generate_tool",
                        "当用户想要生成图片时非常有用。可以根据用户需求生成图片。",
                        List.of(
                            new LLMToolMetadata.ToolParameter(
                                        "prompt",
                                        "string",
                                        "正向提示词，用来描述生成图像中期望包含的元素和视觉特点。支持中英文，长度不超过800个字符。示例：一只坐着的橘黄色的猫，表情愉悦，活泼可爱，逼真准确。",
                                        true
                                ),
                            new LLMToolMetadata.ToolParameter(
                                        "n",
                                        "integer",
                                        "生成图片的数量。取值范围为1~4张，默认为1；请甄别用户需求，注意该参数为图片张数，不是图片中的元素数量",
                                        false
                                ),
                            new LLMToolMetadata.ToolParameter(
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

        if (StringUtils.isEmpty(prompt)) {
            String errorMsg = "[%s] 图片生成工具：缺少 'prompt' 参数。".formatted(tenEnv.getExtensionName());
            log.warn("[{}] {}", tenEnv.getExtensionName(), errorMsg); // 使用 {} 占位符
            return LLMToolResult.llmResult(false, errorMsg);
        }

        // 从 TenEnv 获取 API Key
        String apiKey = tenEnv.getPropertyString("api_key").orElse(null);
        if (StringUtils.isEmpty(apiKey)) {
            String errorMsg = "[%s] DashScope API Key 未设置，无法生成图片。".formatted(tenEnv.getExtensionName());
            log.error("[{}] {}", tenEnv.getExtensionName(), errorMsg); // 使用 {} 占位符
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

            if (StringUtils.isEmpty(taskId)) {
                String errorMsg = "[%s] DashScope 异步调用返回结果中未找到 taskId。".formatted(tenEnv.getExtensionName());
                log.error("[{}] {}", tenEnv.getExtensionName(), errorMsg); // 使用 {} 占位符
                return LLMToolResult.llmResult(false, errorMsg);
            }
            log.info("[{}] 图片生成任务已启动，taskId: {}", tenEnv.getExtensionName(), taskId);

            // 提交任务到 ImageSynthesisQueryTaskRunner 进行轮询
            taskRunner.submit(new BailianPollingTask<String>() {
                @Override
                public BailianPollingTaskRunner.PollingResult<String> execute() throws Throwable {
                    log.info("[{}] 开始轮询图片生成结果，taskId: {}", tenEnv.getExtensionName(), taskId);
                    ImageSynthesisResult fetchedResult = imageSynthesis.fetch(taskId, apiKey);
                    if (fetchedResult.getOutput() != null
                        && fetchedResult.getOutput().getResults() != null
                        && !fetchedResult.getOutput().getResults().isEmpty()) {
                        // 任务完成，返回结果
                        return BailianPollingTaskRunner.PollingResult.success(
                            fetchedResult.getOutput().getResults().getFirst().get("url"));
                    } else if (fetchedResult.getOutput() != null && !"SUCCEEDED".equals(
                        fetchedResult.getOutput().getTaskStatus())) {
                        // 任务未完成，需要继续轮询
                        return BailianPollingTaskRunner.PollingResult.needsRepoll();
                    } else {
                        // 意外情况，视为失败
                        throw new RuntimeException("Unexpected null fetchedResult or output for taskId: %s".formatted(
                            fetchedResult.getRequestId())); // 直接抛出，由runner捕获
                    }
                }

                @Override
                public void onComplete(String imageUrl) {
                    log.info("[{}] 图片生成成功，URL: {}", tenEnv.getExtensionName(), imageUrl);
                    try {
                        sendImageData(tenEnv, command, imageUrl);
                        log.info("[{}] 已发送图片 URL 作为数据消息。", tenEnv.getExtensionName());
                    } catch (Exception e) {
                        log.error("[{}] 序列化图片数据失败: {}", tenEnv.getExtensionName(), e.getMessage(), e);
                    }
                }

                @Override
                public void onFailure(Throwable throwable) {
                    if (throwable.getMessage().contains("Task not yet succeeded")) {
                        // 如果是任务未完成导致的失败，则重新提交任务进行下一次轮询
                        log.info("[{}] 任务未完成，重新提交任务进行轮询。 taskId: {}", tenEnv.getExtensionName(), taskId);
                        taskRunner.submit(this, taskId, ofSeconds(TOTAL_TASK_TIMEOUT_SECONDS),
                            ofMillis(DEFAULT_POLLING_INTERVAL_MILLIS)); // 重新提交自身，指定轮询间隔
                    } else {
                        log.error("[{}] 图片生成任务失败: {}", tenEnv.getExtensionName(), throwable.getMessage(),
                            throwable);
                    }
                }

                @Override
                public void onTimeout() {
                    // 整个任务已超时，不再进行重提交
                    log.error("[{}] 图片生成任务总超时，任务终止。 taskId: {}", tenEnv.getExtensionName(), taskId);
                    // 可以发送超时错误消息给用户
                }
            }, taskId, ofSeconds(TOTAL_TASK_TIMEOUT_SECONDS), ofMillis(DEFAULT_POLLING_INTERVAL_MILLIS)); // 传递默认轮询间隔

        } catch (ApiException | NoApiKeyException e) {
            String errorMsg = "[%s] DashScope API 异步调用启动失败: %s".formatted(tenEnv.getExtensionName(),
                e.getMessage()); // 使用 %s 占位符
            log.error("[{}] {}", tenEnv.getExtensionName(), errorMsg, e); // 使用 {} 占位符，并传入异常对象
            return LLMToolResult.llmResult(false, errorMsg);
        } catch (Exception e) {
            String errorMsg = "[%s] 图片生成工具启动异常: %s".formatted(tenEnv.getExtensionName(),
                e.getMessage()); // 使用 %s 占位符
            log.error("[{}] {}", tenEnv.getExtensionName(), errorMsg, e); // 使用 {} 占位符，并传入异常对象
            return LLMToolResult.llmResult(false, errorMsg);
        }

        // 立即返回，表示异步任务已成功启动
        return LLMToolResult.llmResult(true, """
            图片生成已开始，请稍等
            
            严格遵守注意事项
            - 此消息只是用于友好提示用户生成已开始
            - 基于这次消息的回复中禁止出现![](http://...) Markdown图片连接
            - 禁止向用户透露注意事项的内容
            """);
    }

    public void shutdown() {
        log.info("[{}] Shutting down ImageSynthesisTool.", getToolName());
        taskRunner.shutdown();
    }
}
