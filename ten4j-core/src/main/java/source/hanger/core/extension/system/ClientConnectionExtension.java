package source.hanger.core.extension.system;

import java.util.Map;
import java.util.Optional;

import source.hanger.core.extension.Extension;
import source.hanger.core.message.command.Command;
import source.hanger.core.message.CommandResult;
import source.hanger.core.message.DataMessage;
import source.hanger.core.message.AudioFrameMessage;
import source.hanger.core.message.VideoFrameMessage;
import source.hanger.core.tenenv.TenEnv;

/**
 * @author fuhangbo.hanger.uhfun
 **/
public class ClientConnectionExtension implements Extension {
    private TenEnv env; // 新增TenEnv字段

    // @Override // 移除此注解
    public String getExtensionName() {
        return env != null ? env.getExtensionName() : ""; // 从env获取名称
    }

    // @Override // 移除此注解
    public String getAppUri() {
        return env != null ? env.getAppUri() : ""; // 从env获取appUri
    }

    @Override
    public void onConfigure(TenEnv env, Map<String, Object> properties) {
        // 默认空实现
    }

    @Override
    public void onInit(TenEnv env) {
        // 默认空实现
    }

    @Override
    public void onStart(TenEnv env) {
        // 默认空实现
    }

    @Override
    public void onStop(TenEnv env) {
        // 默认空实现
    }

    @Override
    public void onDeinit(TenEnv env) {
        // 默认空实现
    }

    @Override
    public void destroy(TenEnv env) {
        // 默认空实现
    }

    @Override
    public void onCmd(TenEnv env, Command command) {
        // command.getProperties()
    }

    @Override
    public void onCmdResult(TenEnv env, CommandResult commandResult) {
        // 默认空实现
    }

    @Override
    public void onDataMessage(TenEnv env, DataMessage dataMessage) {
        // 默认空实现
    }

    @Override
    public void onAudioFrame(TenEnv env, AudioFrameMessage audioFrame) {
        // 默认空实现
    }

    @Override
    public void onVideoFrame(TenEnv env, VideoFrameMessage videoFrame) {
        // 默认空实现
    }
}
