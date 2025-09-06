package source.hanger.core.extension.system;

import java.util.Map;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import source.hanger.core.extension.base.BaseExtension;
import source.hanger.core.message.DataMessage;
import source.hanger.core.message.Location;
import source.hanger.core.message.command.Command;
import source.hanger.core.message.command.GenericCommand;
import source.hanger.core.tenenv.TenEnv;

import static java.util.Collections.*;
import static source.hanger.core.common.ExtensionConstants.*;

/**
 * @author fuhangbo.hanger.uhfun
 **/
@Slf4j
public class TTSAdapterExtension extends BaseExtension {

    private String destTTSExtension;

    @Override
    protected void onExtensionConfigure(TenEnv env, Map<String, Object> properties) {
        destTTSExtension = determineDestTTSExtension(env);
    }

    @Override
    public void onCmd(TenEnv env, Command command) {
        if (CMD_IN_FLUSH.equals(command.getName())) {
            log.info("[{}] 收到 来自 {} CMD_FLUSH 命令，将命令转发给 destTTSExtension={}", env.getExtensionName(),
                command.getSrcLoc().getExtensionName(), destTTSExtension);
            env.sendCmd(((GenericCommand)command).toBuilder()
                .destLocs(singletonList(new Location(env.getAppUri(), env.getGraphId(), destTTSExtension)))
                .build());
        }
    }

    @Override
    public void onDataMessage(TenEnv env, DataMessage dataMessage) {
        log.info("[{}] 适配器收到 DataMessage，将 DataMessage 转发给 destTTSExtension={}", env.getExtensionName(), destTTSExtension);
        env.sendMessage(dataMessage.toBuilder()
            .destLocs(singletonList(new Location(env.getAppUri(), env.getGraphId(), destTTSExtension)))
            .build());
    }

    private String determineDestTTSExtension(TenEnv tenEnv) {
        // 动态选择 TTS
        String destExtension = tenEnv.getPropertyString(DEST_TTS_EXTENSION_PROPERTY_NAME)
            .orElseThrow(() -> new RuntimeException("dest_tts is not set"));
        return Stream.of( destExtension.split(","))
            .filter(e -> {
                return tenEnv.getPropertyString(e).map(StringUtils::isNotBlank).orElse(false);
            }).findFirst().orElseThrow(() -> new RuntimeException("dest_tts is not set"));
    }
}
