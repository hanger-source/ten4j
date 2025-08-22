package source.hanger.core.extension.system;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.base.BaseExtension;
import source.hanger.core.message.AudioFrameMessage;
import source.hanger.core.message.CommandResult;
import source.hanger.core.message.DataMessage;
import source.hanger.core.message.Location;
import source.hanger.core.message.Message;
import source.hanger.core.message.VideoFrameMessage;
import source.hanger.core.message.command.Command;
import source.hanger.core.tenenv.TenEnv;

import static java.util.Collections.singletonList;
import static source.hanger.core.common.ExtensionConstants.CMD_IN_ON_USER_JOINED;

/**
 * @author fuhangbo.hanger.uhfun
 **/
@Slf4j
public class ClientConnectionExtension extends BaseExtension {

    private String clientAppUri;

    @Override
    protected void onExtensionConfigure(TenEnv env, Map<String, Object> properties) {
        super.onExtensionConfigure(env, properties);
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
    public void onDestroy(TenEnv env) {
        // 默认空实现
    }

    @Override
    public void onCmd(TenEnv env, Command command) {
        if (clientAppUri == null && command.getName().equals(CMD_IN_ON_USER_JOINED)) {
            clientAppUri = command.getSrcLoc().getAppUri();
        }
        routeLocation(env, command);
        env.sendCmd(command);
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
        routeLocation(env, dataMessage);
        env.sendData(dataMessage);
    }

    @Override
    public void onAudioFrame(TenEnv env, AudioFrameMessage audioFrame) {
        // 默认空实现
        routeLocation(env, audioFrame);
        env.sendAudioFrame(audioFrame);
    }

    @Override
    public void onVideoFrame(TenEnv env, VideoFrameMessage videoFrame) {
        // 默认空实现
        routeLocation(env, videoFrame);
        env.sendVideoFrame(videoFrame);
    }

    private void routeLocation(TenEnv env, Message message) {
        Location srcLoc = message.getSrcLoc();
        if (srcLoc == null || !env.getAppUri().equals(srcLoc.getAppUri())) {
            // 入站消息
            //log.info("[{}]: 入站消息 type: {} name: {}", env.getExtensionName(), message.getType(), message.getName());
            message.setSrcLoc(new Location(env.getAppUri(), env.getGraphId(), null));
            message.setDestLocs(new ArrayList<>());
            if (srcLoc != null) {
                this.clientAppUri = srcLoc.getAppUri();
            }
        } else {
            //log.info("[{}]: 出站消息 type: {} name: {}", env.getExtensionName(), message.getType(), message.getName());
            message.setDestLocs(List.of(new Location(clientAppUri, null, null)));
        }
    }
}
