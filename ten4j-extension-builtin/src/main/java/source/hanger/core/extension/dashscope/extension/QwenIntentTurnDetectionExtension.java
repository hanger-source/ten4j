package source.hanger.core.extension.dashscope.extension;

import java.util.List;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;

import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.component.context.LLMContextManager;
import source.hanger.core.extension.dashscope.component.context.QwenChatLLMContextManager;
import source.hanger.core.extension.system.BaseTurnDetectionExtension;
import source.hanger.core.extension.system.TurnDetector;
import source.hanger.core.tenenv.TenEnv;

import static source.hanger.core.extension.system.BaseTurnDetectionExtension.TurnDetectorDecision.Finished;
import static source.hanger.core.extension.system.BaseTurnDetectionExtension.TurnDetectorDecision.Unfinished;
import static source.hanger.core.extension.system.BaseTurnDetectionExtension.TurnDetectorDecision.Wait;

/**
 * @author fuhangbo.hanger.uhfun
 **/
@Slf4j
public class QwenIntentTurnDetectionExtension extends BaseTurnDetectionExtension<Message> {

    private Generation generation;

    @Override
    protected TurnDetector<Message> createTurnDetector(TenEnv env) {
        String sysPrompt = """
            你是一个对话轮次检测器。根据用户的输入，判断用户是否说完了。如果没有明显的字词结构的断句，默认断定为用户已说完，输出A，避免过多停顿导致回复过慢。
            只输出以下三个词之一：
            - A: 用户说完了，可以开始处理
            - B: 用户要求停顿、等待
            - C: 用户还没说完，继续听
            输出要求：
             - 仅输出一个字母：A、B、C
             - 禁止输出其他任何字符、标点或解释。
            """.stripIndent();
        LLMContextManager<Message> llmContextManager = new QwenChatLLMContextManager(env, () -> sysPrompt);
        return new TurnDetector<>(llmContextManager) {

            @Override
            public void start(TenEnv env) {
                super.start(env);
                generation = new Generation();
            }

            @Override
            public TurnDetectorDecision doEval(String text) {
                String model = env.getPropertyString("model").orElseThrow(() -> new RuntimeException("model 为空"));

                List<Message> messages = llmContextManager.getMessagesForLLM();
                messages.add(Message.builder()
                    .role("user")
                    .content(text)
                    .build());

                GenerationParam.GenerationParamBuilder paramBuilder = GenerationParam.builder()
                    .apiKey(env.getPropertyString("api_key").orElseThrow(() -> new RuntimeException("api_key 为空")))
                    .model(model)
                    .messages(messages)
                    .resultFormat(GenerationParam.ResultFormat.MESSAGE);

                GenerationParam param = paramBuilder.build();

                // 使用注入的 DashScope Generation 客户端进行调用
                try {
                    GenerationResult result = generation.call(param);
                    String decision = result.getOutput().getChoices().getFirst().getMessage().getContent();
                    return switch (decision) {
                        case "A" -> Finished;
                        case "B" -> Wait;
                        case "C" -> Unfinished;
                        default -> {
                            log.error("[{}] Invalid decision: {}", env.getExtensionName(), decision);
                            yield Unfinished;
                        }
                    };
                } catch (NoApiKeyException | InputRequiredException e) {
                    log.error("[{}] Error calling DashScope: {}", env.getExtensionName(), e.getMessage());
                    return Finished;
                }

            }
        };
    }
}
