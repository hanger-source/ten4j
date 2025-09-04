package source.hanger.core.extension.system;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.StopWatch;
import source.hanger.core.extension.component.context.LLMContextManager;
import source.hanger.core.tenenv.TenEnv;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static source.hanger.core.extension.system.BaseTurnDetectionExtension.TurnDetectorDecision;

@Slf4j
public abstract class TurnDetector<MESSAGE> {

    private static final Pattern PUNCTUATION_PATTERN = Pattern.compile("[\\p{P}\\p{S}]+");
    protected final LLMContextManager<MESSAGE> llmContextManager;
    private CompletableFuture<TurnDetectorDecision> currentEvalTask;
    private Duration forceThresholdMs;

    public TurnDetector(LLMContextManager<MESSAGE> llmContextManager) {
        this.llmContextManager = llmContextManager;
    }

    public void start(TenEnv env) {
        forceThresholdMs = env.getProperty("force_threshold_ms")
            .map(String::valueOf)
            .map(Long::parseLong)
            .map(Duration::ofMillis)
            .orElse(Duration.ofMillis(1000));
    }

    public void stop() {
        cancelEval();
    }

    public final TurnDetectorDecision eval(String text, TenEnv env) {
        StopWatch stopWatch = StopWatch.createStarted(); // 启动 StopWatch
        TurnDetectorDecision decision = TurnDetectorDecision.Finished;
        Throwable throwable = null;
        try {
            currentEvalTask = CompletableFuture.supplyAsync(() -> doEval(text));
            decision = currentEvalTask.get(forceThresholdMs.toMillis(), MILLISECONDS);
            return decision;
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throwable = e;
            return decision;
        } finally {
            stopWatch.stop(); // 停止计时
            long shutdownDuration = stopWatch.getTime();
            if (throwable != null) {
                log.error("[{}] turn detector text={} decision={} elapsed_time={}ms", env.getExtensionName(), text, decision, shutdownDuration, throwable);
            } else {
                log.info("[{}] turn detector text={} decision={} elapsed_time={}ms", env.getExtensionName(), text, decision, shutdownDuration);
            }
        }
    }

    public abstract TurnDetectorDecision doEval(String text);

    public void cancelEval() {
        if (currentEvalTask != null && !currentEvalTask.isDone()) {
            currentEvalTask.cancel(true);
            log.info("cancel eval task");
        }
    }

    protected String removePunctuation(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        Matcher matcher = PUNCTUATION_PATTERN.matcher(text);
        return matcher.replaceAll("");
    }
}
