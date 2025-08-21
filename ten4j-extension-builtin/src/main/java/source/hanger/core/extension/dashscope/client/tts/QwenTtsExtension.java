package source.hanger.core.extension.dashscope.client.tts;

import java.util.Map;

import io.reactivex.Flowable;
import lombok.extern.slf4j.Slf4j;
import net.fellbaum.jemoji.EmojiManager;
import source.hanger.core.common.ExtensionConstants;
import source.hanger.core.extension.api.BaseTTSExtension;
import source.hanger.core.message.CommandResult;
import source.hanger.core.message.DataMessage;
import source.hanger.core.message.command.Command;
import source.hanger.core.tenenv.TenEnv;

/**
 * Qwen TTS 扩展实现。
 * 继承 BaseTTSExtension 并实现具体的 TTS 逻辑。
 */
@Slf4j
public class QwenTtsExtension extends BaseTTSExtension {

    private QwenTtsClient qwenTtsClient;
    private String voiceName; // 语音名称，例如 "CHERRY"

    @Override
    protected void onExtensionConfigure(TenEnv env, Map<String, Object> properties) {
        log.info("[qwen_tts] Extension configuring: {}", env.getExtensionName());

        String apiKey = (String)properties.get("api_key");
        voiceName = (String)properties.get("voice_name"); // 从配置中获取语音名称

        if (apiKey == null || voiceName == null || apiKey.isEmpty() || voiceName.isEmpty()) {
            log.error("[qwen_tts] API Key or Voice Name is not set. Please configure in manifest.json/property.json.");
        }

        qwenTtsClient = new QwenTtsClient(apiKey);
    }

    @Override
    protected Flowable<byte[]> onRequestTTS(TenEnv env, DataMessage data) {
        String inputText = (String)data.getProperty(ExtensionConstants.DATA_OUT_PROPERTY_TEXT); // 假设文本属性名为 "text"
        Boolean isQuiet = (Boolean)data.getProperty(ExtensionConstants.DATA_IN_PROPERTY_QUIET); // 假设 quiet 属性名为 "quiet"
        if (isQuiet == null) {
            isQuiet = false; // 默认不静音
        }

        // 使用 EmojiManager 过滤掉 inputText 中的 emoji
        String filteredInputText = EmojiManager.removeAllEmojis(inputText)
            // 移除换行符和空格
            .replace("\n", "").strip();

        if (filteredInputText.isEmpty()) {
            log.warn("[qwen_tts] Received empty text for TTS, ignoring.");
            return Flowable.empty();
        }

        log.info("[qwen_tts] Received TTS request for text: \"{}\"", filteredInputText);

        // 使用 QwenTtsClient 进行流式 TTS 调用
        Boolean finalIsQuiet = isQuiet;
        // 返回一个 Flowable，用于流式处理音频数据（冷流）
        return qwenTtsClient.streamTextToSpeech(filteredInputText, voiceName);
    }

    @Override
    public void flushInputItems(TenEnv env, Command command) {
        super.flushInputItems(env, command);
        CommandResult cmdResult = CommandResult.success(command, "TTS input flushed.");
        env.sendResult(cmdResult);
    }

    @Override
    protected void onCancelTTS(TenEnv env) {
        log.info("[qwen_tts] TTS request cancelled.");
        // 在这里实现取消当前 TTS 生成的逻辑，如果 QwenTtsClient 支持取消
        // DashScope SDK 目前没有直接的取消方法，这里主要用于标记状态和清理
        interrupted.set(true);
    }

    @Override
    public void onCmd(TenEnv env, Command command) {
        String cmdName = command.getName();
        switch (cmdName) {
            case ExtensionConstants.CMD_IN_ON_USER_JOINED:
                // 处理用户加入事件（如果需要）
                CommandResult joinResult = CommandResult.success(command, "User joined.");
                env.sendResult(joinResult);
                break;
            case ExtensionConstants.CMD_IN_ON_USER_LEFT:
                // 处理用户离开事件（如果需要）
                CommandResult leftResult = CommandResult.success(command, "User left.");
                env.sendResult(leftResult);
                break;
            default:
                // 将其他未处理的命令传递给父类
                super.onCmd(env, command);
                break;
        }
    }

    @Override
    public void onStart(TenEnv env) {
        super.onStart(env);
        interrupted.set(false); // 在启动时重置中断标志
    }
}