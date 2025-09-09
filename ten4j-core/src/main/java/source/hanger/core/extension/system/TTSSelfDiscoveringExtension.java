package source.hanger.core.extension.system;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.base.BaseExtension;
import source.hanger.core.message.DataMessage;
import source.hanger.core.message.Location;
import source.hanger.core.message.command.Command;
import source.hanger.core.message.command.GenericCommand;
import source.hanger.core.tenenv.TenEnv;

import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static source.hanger.core.common.ExtensionConstants.CMD_IN_FLUSH;
import static source.hanger.core.common.ExtensionConstants.CMD_RESULT_TTS_AVAILABLE;
import static source.hanger.core.common.ExtensionConstants.CMD_RESULT_TTS_UNAVAILABLE;
import static source.hanger.core.common.ExtensionConstants.CMD_TTS_DISCOVERY;
import static source.hanger.core.common.ExtensionConstants.DATA_OUT_PROPERTY_TEXT;
import static source.hanger.core.common.ExtensionConstants.GRAPH_PROPERTY_VOICE_MODEL;

/**
 * @author fuhangbo.hanger.uhfun
 **/
@Slf4j
public class TTSSelfDiscoveringExtension extends BaseExtension {

    private String destTTSExtension;
    private CompletableFuture<Void> discoveryFuture;

    @Override
    protected void onExtensionConfigure(TenEnv env, Map<String, Object> properties) {
        super.onExtensionConfigure(env, properties);
    }

    @Override
    public void onStart(TenEnv env) {
        String voiceModel = env.getPropertyString(GRAPH_PROPERTY_VOICE_MODEL)
            .orElseThrow(() -> new IllegalStateException("未配置语音模型"));
        Command command = GenericCommand.createBuilder(CMD_TTS_DISCOVERY)
            .property(GRAPH_PROPERTY_VOICE_MODEL, voiceModel)
            .build();
        discoveryFuture = env.submitCommandWithResultHandle(command).toCompletedFuture().thenAccept(commandResults -> {
            commandResults.forEach(commandResult -> {
                String availableExtensionName = commandResult
                    .getPropertyString(CMD_RESULT_TTS_AVAILABLE).orElse( "");

                String unAvailableExtensionName = commandResult
                    .getPropertyString(CMD_RESULT_TTS_UNAVAILABLE).orElse( "");

                if (isNotBlank(availableExtensionName)) {
                    if (isBlank(destTTSExtension)) {
                        destTTSExtension = availableExtensionName;
                        log.info("[{}] 发现TTS destTTSExtension={}", env.getExtensionName(), destTTSExtension);
                    } else {
                        log.info("[{}] 已有TTS destTTSExtension={} availableExtension={} unAvailableExtension={}",
                            env.getExtensionName(), destTTSExtension, availableExtensionName, unAvailableExtensionName);
                    }
                }
            });
            if (isBlank(destTTSExtension)) {
                log.error("[{}] 未发现TTS", env.getExtensionName());
            }
        });
    }

    private void waitForDiscovery() {
        try {
            discoveryFuture.get(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            log.error("[{}] 获取TTS失败", env.getExtensionName(), e);
        }
    }

    @Override
    public void onCmd(TenEnv env, Command command) {
        waitForDiscovery();
        if (CMD_IN_FLUSH.equals(command.getName())) {
            log.info("[{}] 收到 来自 {} CMD_FLUSH 命令，将命令转发给 destTTSExtension={}", env.getExtensionName(),
                command.getSrcLoc().getExtensionName(), destTTSExtension);
            if (isBlank(destTTSExtension)) {
                log.error("[{}] onCmd commandName={} 未配置TTS", env.getExtensionName(), command.getName());
            } else {
                env.sendCmd(((GenericCommand)command).toBuilder()
                    .destLocs(singletonList(new Location(env.getAppUri(), env.getGraphId(), destTTSExtension)))
                    .build());
            }
        }
    }

    @Override
    public void onDataMessage(TenEnv env, DataMessage dataMessage) {
        waitForDiscovery();
        log.info("[{}] 适配器收到 DataMessage，将 DataMessage 转发给 destTTSExtension={} text={}",
            env.getExtensionName(), destTTSExtension,
            dataMessage.getPropertyString(DATA_OUT_PROPERTY_TEXT).orElse(""));
        if (isBlank(destTTSExtension)) {
            log.error("[{}] onDataMessage 未配置TTS", env.getExtensionName());
        } else {
            env.sendMessage(dataMessage.toBuilder()
                .destLocs(singletonList(new Location(env.getAppUri(), env.getGraphId(), destTTSExtension)))
                .build());
        }
    }

}
