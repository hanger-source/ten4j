package source.hanger.core.extension.base.tool;

import java.util.Map;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Singular;
import lombok.experimental.SuperBuilder;
import source.hanger.core.extension.base.tool.ToolCallPayload.FinalPayload.FinalPayloadBuilder;
import source.hanger.core.extension.base.tool.ToolCallPayload.SegmentPayload.SegmentPayloadBuilder;

@SuperBuilder
@NoArgsConstructor
public abstract class ToolCallPayload {

    public static SegmentPayloadBuilder<?, ?> segmentPayload() {
        return SegmentPayload.builder();
    }

    public static FinalPayloadBuilder<?, ?> finalPayload() {
        return FinalPayload.builder();
    }

    public static FinalPayloadBuilder<?, ?> errorPayload() {
        return FinalPayload.builder()
            .secondRound(true);
    }

    @EqualsAndHashCode(callSuper = true)
    @SuperBuilder
    @Data
    static class ProgressUpdate extends ToolCallPayload {
        private String content;
    }

    @EqualsAndHashCode(callSuper = true)
    @SuperBuilder
    @Data
    public static class FinalPayload extends ToolCallPayload {
        /**
         * 提供给模型 进行二次输出，为空则不输出
         */
        private String assistantMessage;
        private String toolCallContext;
        private Boolean secondRound;
        @Singular
        private Map<String, Object> properties;
    }

    @EqualsAndHashCode(callSuper = true)
    @SuperBuilder
    @Data
    public static class SegmentPayload extends ToolCallPayload {
        /**
         * 提供给模型 进行二次输出，为空则不输出
         */
        private String assistantMessage;
        private String toolCallContext;
        private Boolean secondRound;
        @Singular
        private Map<String, Object> properties;
    }

    @EqualsAndHashCode(callSuper = true)
    @Data
    @SuperBuilder
    public static class ErrorPayload extends ToolCallPayload {
        private String toolCallContext;
        private Boolean secondRound;
        private Map<String, Object> properties;
    }
}
