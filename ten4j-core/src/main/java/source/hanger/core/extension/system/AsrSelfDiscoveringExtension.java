package source.hanger.core.extension.system;

import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.base.BaseExtension;
import source.hanger.core.message.AudioFrameMessage;
import source.hanger.core.message.Location;
import source.hanger.core.message.command.GenericCommand;
import source.hanger.core.tenenv.TenEnv;

import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.*;
import static source.hanger.core.common.ExtensionConstants.*;

/**
 * @author fuhangbo.hanger.uhfun
 **/
@Slf4j
public class AsrSelfDiscoveringExtension extends BaseExtension {

    private String destASRExtension;
    @Override
    public void onStart(TenEnv env) {
        super.onStart(env);
        env.submitCommandWithResultHandle(GenericCommand.create(CMD_ASR_DISCOVERY)).toCompletedFuture().thenAccept(
            commandResults -> {
                commandResults.forEach(commandResult -> {
                    String availableExtensionName = commandResult
                        .getPropertyString(CMD_RESULT_ASR_AVAILABLE).orElse( "");

                    String unAvailableExtensionName = commandResult
                        .getPropertyString(CMD_RESULT_ASR_AVAILABLE).orElse( "");

                    if (isNotBlank(availableExtensionName)) {
                        if (isBlank(destASRExtension)) {
                            destASRExtension = availableExtensionName;
                            log.info("[{}] 发现ASR destTTSExtension={}", env.getExtensionName(), destASRExtension);
                        } else {
                            log.info("[{}] 已有ASR destTTSExtension={} availableExtension={} unAvailableExtension={}",
                                env.getExtensionName(), destASRExtension, availableExtensionName, unAvailableExtensionName);
                        }
                    }
                });
                if (isBlank(destASRExtension)) {
                    log.error("[{}] 未发现ASR", env.getExtensionName());
                }
            });
    }

    @Override
    public void onAudioFrame(TenEnv env, AudioFrameMessage audioFrame) {
        if (isBlank(destASRExtension)) {
            log.warn("[{}] 未发现ASR，忽略音频帧", env.getExtensionName());
        }
        env.sendMessage(audioFrame.toBuilder()
            .destLocs(singletonList(new Location(env.getAppUri(), env.getGraphId(), destASRExtension)))
            .build());
    }

}
