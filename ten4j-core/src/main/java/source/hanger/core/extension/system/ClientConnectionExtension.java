package source.hanger.core.extension.system;

import java.util.ArrayList;
import java.util.Map;

import source.hanger.core.extension.BaseExtension;
import source.hanger.core.message.AudioFrameMessage;
import source.hanger.core.message.CommandResult;
import source.hanger.core.message.DataMessage;
import source.hanger.core.message.Location;
import source.hanger.core.message.VideoFrameMessage;
import source.hanger.core.message.command.Command;
import source.hanger.core.tenenv.TenEnv;

import static java.util.Collections.singletonList;

/**
 * @author fuhangbo.hanger.uhfun
 **/
public class ClientConnectionExtension extends BaseExtension {

    private String clientAppUri;

    @Override
    public void onConfigure(TenEnv env, Map<String, Object> properties) {
        this.env = env; // 初始化 env 字段
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
        if (env.getAppUri().equals(command.getSrcLoc().getAppUri())) {
            clientAppUri = command.getSrcLoc().getAppUri();
        }
    }

    @Override
    public void onCmdResult(TenEnv env, CommandResult commandResult) {
        if (commandResult.getDestLocs() == null) {
            commandResult.setDestLocs(
                singletonList(new Location(clientAppUri, env.getGraphId(), env.getExtensionName())));
        }
        env.sendResult(commandResult);
        // 默认空实现
    }

    @Override
    public void onDataMessage(TenEnv env, DataMessage dataMessage) {
        // 默认空实现
        if (dataMessage.getDestLocs() == null) {
            dataMessage.setDestLocs(
                singletonList(new Location(clientAppUri, env.getGraphId(), env.getExtensionName())));
        } else {
            dataMessage.setDestLocs(new ArrayList<>());
        }
        env.sendData(dataMessage);
    }

    @Override
    public void onAudioFrame(TenEnv env, AudioFrameMessage audioFrame) {
        // 默认空实现
        if (audioFrame.getDestLocs() == null) {
            audioFrame.setDestLocs(singletonList(new Location(clientAppUri, env.getGraphId(), env.getExtensionName())));
        }
        env.sendAudioFrame(audioFrame);
    }

    @Override
    public void onVideoFrame(TenEnv env, VideoFrameMessage videoFrame) {
        // 默认空实现
        if (videoFrame.getDestLocs() == null) {
            videoFrame.setDestLocs(singletonList(new Location(clientAppUri, env.getGraphId(), env.getExtensionName())));
        }
        env.sendVideoFrame(videoFrame);
    }
}
