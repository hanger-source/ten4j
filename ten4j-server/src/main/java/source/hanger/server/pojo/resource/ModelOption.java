package source.hanger.server.pojo.resource;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class ModelOption {

    @JsonProperty("name")
    private String name;

    @JsonProperty("model")
    private String model;

    @JsonProperty("type")
    private List<String> type;

    @JsonProperty("tag")
    private List<String> tag;

    @JsonProperty("vendor")
    private String vendor;

    @JsonProperty("description")
    private String description;
}
