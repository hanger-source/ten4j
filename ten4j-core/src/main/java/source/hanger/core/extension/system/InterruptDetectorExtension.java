package source.hanger.core.extension.system;

import java.util.ArrayList;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.api.BaseExtension;
import source.hanger.core.message.CommandResult;
import source.hanger.core.message.DataMessage;
import source.hanger.core.message.command.Command;
import source.hanger.core.message.command.GenericCommand;
import source.hanger.core.tenenv.TenEnv;
import source.hanger.core.util.MessageUtils;

import static source.hanger.core.common.ExtensionConstants.ASR_DATA_OUT_NAME;
import static source.hanger.core.common.ExtensionConstants.TEXT_DATA_OUT_NAME;

@Slf4j
public class InterruptDetectorExtension extends BaseExtension {

    private static final String CMD_NAME_FLUSH = "flush"; // Corresponds to Python's CMD_IN_FLUSH
    private static final String TEXT_DATA_TEXT_FIELD = "text"; // Corresponds to Python's TEXT_DATA_TEXT_FIELD
    private static final String TEXT_DATA_FINAL_FIELD = "is_final"; // Corresponds to Python's TEXT_DATA_FINAL_FIELD

    @Override
    protected void onExtensionConfigure(TenEnv env, Map<String, Object> properties) {
        log.info("[InterruptDetector] Extension configuring: {}", env.getExtensionName());
    }

    @Override
    public void onInit(TenEnv env) {
        super.onInit(env);
        log.info("[InterruptDetector] Extension initialized: {}", env.getExtensionName());
    }

    @Override
    public void onStart(TenEnv env) {
        super.onStart(env);
        log.info("[InterruptDetector] Extension starting: {}", env.getExtensionName());
    }

    @Override
    public void onStop(TenEnv env) {
        super.onStop(env);
        log.info("[InterruptDetector] Extension stopping: {}", env.getExtensionName());
    }

    @Override
    public void onDeinit(TenEnv env) {
        super.onDeinit(env);
        log.info("[InterruptDetector] Extension de-initialized: {}", env.getExtensionName());
    }

    /**
     * Helper method to send a flush command.
     */
    private void sendFlushCmd(TenEnv env, String originalCommandId, String parentCommandId) {
        // Use GenericCommand.create for flush command
        GenericCommand flushCmd = GenericCommand.create(CMD_NAME_FLUSH, originalCommandId);
        env.sendMessage(flushCmd);
        log.info("[InterruptDetector] Sent flush command to downstream. cmdId {} OriginalCmdId: {}", flushCmd.getId(),
            originalCommandId);
    }

    @Override
    public void onCmd(TenEnv env, Command command) {
        super.onCmd(env, command);
        String cmdName = command.getName();
        log.info("[InterruptDetector] Received command: {}", cmdName);

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
            log.info("[InterruptDetector] Forwarded command: {}", newCmd.getName());
        } catch (Exception e) {
            log.error("[InterruptDetector] Error forwarding command {}: {}", cmdName, e.getMessage(), e);
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
            String text = (String)data.getProperty(TEXT_DATA_TEXT_FIELD);
            Boolean isFinal = (Boolean)data.getProperty(TEXT_DATA_FINAL_FIELD);

            if (text == null) {
                text = "";
            }
            if (isFinal == null) {
                isFinal = false;
            }

            log.debug("[{}] {}: {} {}: {}", env.getExtensionName(), TEXT_DATA_TEXT_FIELD, text, TEXT_DATA_FINAL_FIELD,
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
            data.getName(), data.getProperty(TEXT_DATA_TEXT_FIELD), data.getProperty(TEXT_DATA_FINAL_FIELD));
    }
}
