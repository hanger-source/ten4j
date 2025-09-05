package source.hanger.core.extension.system;

import java.util.ArrayList;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.base.BaseExtension;
import source.hanger.core.extension.component.flush.FlushOperationCoordinator;
import source.hanger.core.message.DataMessage;
import source.hanger.core.message.command.Command;
import source.hanger.core.message.command.GenericCommand;
import source.hanger.core.tenenv.TenEnv;

import static source.hanger.core.common.ExtensionConstants.ASR_DATA_OUT_NAME;
import static source.hanger.core.common.ExtensionConstants.CMD_IN_FLUSH;
import static source.hanger.core.common.ExtensionConstants.CMD_OUT_FLUSH;
import static source.hanger.core.common.ExtensionConstants.DATA_OUT_PROPERTY_IS_FINAL;
import static source.hanger.core.common.ExtensionConstants.DATA_OUT_PROPERTY_TEXT;
import static source.hanger.core.common.ExtensionConstants.TEXT_DATA_OUT_NAME;
import static source.hanger.core.extension.system.BaseTurnDetectionExtension.TurnDetectorDecision.Finished;
import static source.hanger.core.extension.system.BaseTurnDetectionExtension.TurnDetectorDecision.Wait;

@Slf4j
public abstract class BaseTurnDetectionExtension<MESSAGE> extends BaseExtension {

    protected FlushOperationCoordinator flushOperationCoordinator;
    private TurnDetector<MESSAGE> turnDetector;
    private String cachedText = "";
    private boolean newTurnStarted = false;

    protected abstract TurnDetector<MESSAGE> createTurnDetector(TenEnv env);

    @Override
    protected void onExtensionConfigure(TenEnv env, Map<String, Object> properties) {
        log.info("[{}] TurnDetection Extension configuring", env.getExtensionName());
        this.turnDetector = createTurnDetector(env);
    }

    @Override
    public void onStart(TenEnv env) {
        super.onStart(env);
        turnDetector.start(env);
        log.info("[{}] TurnDetection Extension starting", env.getExtensionName());
    }

    @Override
    public void onStop(TenEnv env) {
        if (this.turnDetector != null) {
            this.turnDetector.stop();
            this.turnDetector = null;
        }
        super.onStop(env);
        log.info("[{}] TurnDetection Extension stopping", env.getExtensionName());
    }

    @Override
    public void onDeinit(TenEnv env) {
        super.onDeinit(env);
        log.info("[{}] TurnDetection Extension de-initialized", env.getExtensionName());
    }

    @Override
    public void onCmd(TenEnv env, Command command) {
        String cmdName = command.getName();
        log.info("[{}] Received command: {}", env.getExtensionName(), cmdName);
        if (CMD_OUT_FLUSH.equals(cmdName)) {
            handleFlushCmd(env, command);
        } else {
            // Forward the original command to downstream
            env.sendCmd(command);
        }
    }

    private void handleFlushCmd(TenEnv env, Command command) {
        log.debug("[{}] Handling flush command", env.getExtensionName());
        this.cachedText = "";
        this.newTurnStarted = false;
        // Potentially also reset turnDetector internal state if needed
        if (this.turnDetector != null) {
            this.turnDetector.cancelEval();
        }
        // 处理 CMD_FLUSH 命令
        if (CMD_IN_FLUSH.equals(command.getName())) {
            log.info("[{}] 收到来自 {} CMD_FLUSH 命令，执行刷新操作并重置历史。", env.getExtensionName(),
                command.getSrcLoc().getExtensionName());
            flushOperationCoordinator.triggerFlush(env);
            env.sendCmd(GenericCommand.create(CMD_OUT_FLUSH, command.getId()));
        }
    }

    @Override
    public void onDataMessage(TenEnv env, DataMessage data) {
        log.info("[{}] Received data message: {}", env.getExtensionName(), data.getName());

        if (!TEXT_DATA_OUT_NAME.equals(data.getName())
            && !ASR_DATA_OUT_NAME.equals(data.getName())) {
            env.sendMessage(data);
            return;
        }

        if (ASR_DATA_OUT_NAME.equals(data.getName())) {
            // Change the name to TEXT_DATA_OUT_NAME
            data.setDestLocs(new ArrayList<>());
            data.setName(TEXT_DATA_OUT_NAME);
        }

        String inputText = data.getPropertyString(DATA_OUT_PROPERTY_TEXT).orElse("");
        Boolean isFinal = data.getPropertyBoolean(DATA_OUT_PROPERTY_IS_FINAL).orElse(false);

        log.info("[{}] on_data text: {} is_final: {}", env.getExtensionName(), inputText, isFinal);

        if (this.turnDetector != null) {
            this.turnDetector.cancelEval();
        }

        String outText = this.cachedText + inputText;

        if (!newTurnStarted) {
            if (isFinal || outText.length() >= 2) {
                this.newTurnStarted = true;
                sendFlushCmd(env);
            }
        }

        if (!isFinal) {
            env.sendMessage(data);
            return;
        }

        this.cachedText += inputText;

        // Decide to chat or not
        evalDecision(env);
    }

    private void sendFlushCmd(TenEnv env) {
        log.debug("[{}] [send_cmd:flush]", env.getExtensionName());
        Command flushCmd = GenericCommand.create(CMD_OUT_FLUSH);
        env.sendCmd(flushCmd);
        log.debug("[{}] cmd flush done", env.getExtensionName());
    }

    private void evalDecision(TenEnv env) {
        if (this.turnDetector == null || this.cachedText.isEmpty()) {
            log.warn("[{}] no cached transcription or turnDetector not initialized, skip eval decision",
                env.getExtensionName());
            return;
        }

        this.turnDetector.cancelEval();

        //TurnDetectorDecision decision = this.turnDetector.eval(this.cachedText, env);
        TurnDetectorDecision decision = Finished;
        if (decision == Finished) {
            // TODO: Handle force chat task cancellation if implemented
            processNewTurn(env, Finished);
        } else if (decision == Wait) {
            // TODO: Handle force chat task cancellation if implemented
            processNewTurn(env, Wait);
        } else {
            // TODO: Recreate force chat task if implemented
        }
    }

    private void processNewTurn(TenEnv env, TurnDetectorDecision decision) {
        String text = this.cachedText;
        this.cachedText = "";
        if (text.isEmpty()) {
            log.warn("[{}] no cached transcription, creating empty one", env.getExtensionName());
            text = "";
        }

        this.newTurnStarted = false;

        if (decision == Wait) {
            log.debug("[{}] end_of_turn by wait, no new turn to send", env.getExtensionName());
            return;
        }

        log.debug("[{}] end_of_turn, send new turn {}", env.getExtensionName(), text);

        DataMessage outData = DataMessage.createBuilder(TEXT_DATA_OUT_NAME)
            .property(DATA_OUT_PROPERTY_TEXT, text)
            .property(DATA_OUT_PROPERTY_IS_FINAL, true)
            .build();
        env.sendMessage(outData);
    }

    public enum TurnDetectorDecision {
        Unfinished, // 用户还没说完，继续听
        Finished,   // 用户说完了，可以开始处理
        Wait        // 用户停顿了，等待继续
    }
}
