package source.hanger.core.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@Accessors(chain = true)
@EqualsAndHashCode(of = {"appUri", "graphId", "extensionName"})
public class Location {
    @JsonProperty("app_uri")
    private String appUri;
    @JsonProperty("graph_id")
    private String graphId;
    @JsonProperty("extension_name")
    private String extensionName;

    public Location(String appUri, String graphId, String extensionName) {
        this.appUri = appUri;
        this.graphId = graphId;
        this.extensionName = extensionName;
    }
}