package source.hanger.core.extension.system;

import java.util.ArrayList;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import source.hanger.core.common.ExtensionConstants;
import source.hanger.core.extension.base.BaseExtension;
import source.hanger.core.message.CommandResult;
import source.hanger.core.message.DataMessage;
import source.hanger.core.message.command.Command;
import source.hanger.core.message.command.GenericCommand;
import source.hanger.core.tenenv.TenEnv;
import source.hanger.core.util.MessageUtils;

import static source.hanger.core.common.ExtensionConstants.ASR_DATA_OUT_NAME;
import static source.hanger.core.common.ExtensionConstants.DATA_OUT_PROPERTY_IS_FINAL;
import static source.hanger.core.common.ExtensionConstants.DATA_OUT_PROPERTY_TEXT;
import static source.hanger.core.common.ExtensionConstants.TEXT_DATA_OUT_NAME;

@Slf4j
public class InterruptDetectorExtension extends BaseExtension {

    @Override
    protected void onExtensionConfigure(TenEnv env, Map<String, Object> properties) {
        log.info("[{}] Extension configuring", env.getExtensionName());
    }

    @Override
    public void onInit(TenEnv env) {
        super.onInit(env);
        log.info("[{}] Extension initialized", env.getExtensionName());
    }

    @Override
    public void onStart(TenEnv env) {
        super.onStart(env);
        log.info("[{}] Extension starting", env.getExtensionName());
    }

    @Override
    public void onStop(TenEnv env) {
        super.onStop(env);
        log.info("[{}] Extension stopping", env.getExtensionName());
    }

    @Override
    public void onDeinit(TenEnv env) {
        super.onDeinit(env);
        log.info("[{}] Extension de-initialized", env.getExtensionName());
    }

    /**
     * Helper method to send a flush command.
     */
    private void sendFlushCmd(TenEnv env, String originalCommandId, String parentCommandId) {
        // Use GenericCommand.create for flush command
        GenericCommand flushCmd = GenericCommand.create(ExtensionConstants.CMD_OUT_FLUSH, originalCommandId);
        env.sendMessage(flushCmd);
        log.info("[{}] Sent flush command to downstream. cmdId {} OriginalCmdId: {}", env.getExtensionName(), flushCmd.getId(),
            originalCommandId);
    }

    @Override
    public void onCmd(TenEnv env, Command command) {
        String cmdName = command.getName();
        log.info("[{}] Received command: {}",env.getExtensionName(), cmdName);

        // Flush whatever cmd incoming at the moment
        sendFlushCmd(env, command.getId(), command.getParentCommandId());

        // Then forward the original command to downstream
        try {
            // Use GenericCommand to forward the original command
            GenericCommand newCmd = new GenericCommand(
                MessageUtils.generateUniqueId(), // New ID for the forwarded command
                command.getType(),
                command.getSrcLoc(),
                command.getDestLocs(),
                command.getName(),
                command.getParentCommandId());
            // Copy properties from original command to new command
            if (command.getProperties() != null) {
                newCmd.setProperties(command.getProperties());
            }

            env.sendCmd(newCmd);
            log.info("[{}] Forwarded command: {}",env.getExtensionName(), newCmd.getName());
        } catch (Exception e) {
            log.error("[{}] Error forwarding command {}: {}",env.getExtensionName(), cmdName, e.getMessage(), e);
        }

        // Return success result for the incoming command
        CommandResult cmdResult = CommandResult.success(command, "success");
        env.sendResult(cmdResult);
    }

    @Override
    public void onDataMessage(TenEnv env, DataMessage data) {
        log.info("[{}] Received data message: {}", env.getExtensionName(), data.getName());

        if (TEXT_DATA_OUT_NAME.equals(data.getName())
            || ASR_DATA_OUT_NAME.equals(data.getName())) { // Check for text_data
            String text = (String)data.getProperty(DATA_OUT_PROPERTY_TEXT);
            Boolean isFinal = (Boolean)data.getProperty(DATA_OUT_PROPERTY_IS_FINAL);

            if (text == null) {
                text = "";
            }
            if (isFinal == null) {
                isFinal = false;
            }

            log.debug("[{}] {}: {} {}: {}", env.getExtensionName(), DATA_OUT_PROPERTY_TEXT, text,
                DATA_OUT_PROPERTY_IS_FINAL,
                isFinal);

            if (isFinal || text.length() >= 2) {
                // For DataMessage, use its own ID as originalCommandId
                sendFlushCmd(env, data.getId(), null); // parentCommandId is not directly applicable for data messages
            }
        }

        if (ASR_DATA_OUT_NAME.equals(data.getName())) {
            // Change the name to TEXT_DATA_OUT_NAME
            data.setDestLocs(new ArrayList<>());
            data.setName(TEXT_DATA_OUT_NAME);
        }

        // Forward the original data message to downstream
        env.sendMessage(data);
        log.info("[{}] Forwarded data message: {} with text: {} and final: {}", env.getExtensionName(),
            data.getName(), data.getProperty(DATA_OUT_PROPERTY_TEXT), data.getProperty(DATA_OUT_PROPERTY_IS_FINAL));
    }
}
