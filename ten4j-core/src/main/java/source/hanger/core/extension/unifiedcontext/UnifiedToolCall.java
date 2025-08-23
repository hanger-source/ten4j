package source.hanger.core.extension.unifiedcontext;

import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UnifiedToolCall {
    private String id;
    private String functionName;
    private String functionArguments;
}
