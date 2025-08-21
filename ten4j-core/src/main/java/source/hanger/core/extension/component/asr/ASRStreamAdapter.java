package source.hanger.core.extension.component.asr;

import source.hanger.core.message.AudioFrameMessage;
import source.hanger.core.tenenv.TenEnv;

/**
 * ASR 流适配器接口。
 * 负责 ASR 原始输出的复杂解析、文本聚合、通用工具调用片段的生成，并将其转换为更高级的“逻辑块”推送到主管道。
 */
public interface ASRStreamAdapter {

    /**
     * 启动 ASR 流处理。
     *
     * @param env 当前的 TenEnv 环境。
     */
    void startASRStream(TenEnv env);

    /**
     * 处理接收到的音频帧。
     *
     * @param env        当前的 TenEnv 环境。
     * @param audioFrame 接收到的音频帧消息。
     */
    void onAudioFrame(TenEnv env, AudioFrameMessage audioFrame);
    /**
     * 处理 ASR 重连逻辑。
     *
     * @param env 当前的 TenEnv 环境。
     */
    void onReconnect(TenEnv env);
}
