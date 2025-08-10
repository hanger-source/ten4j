package com.tenframework.core.graph;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tenframework.core.message.Location;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Map;

@Data
@NoArgsConstructor
@Accessors(chain = true)
public class ExtensionGroupInfo {

    public ExtensionGroupInfo(String extensionGroupInstanceName, Map<String, Object> property) {
        this.extensionGroupInstanceName = extensionGroupInstanceName;
        this.property = property;
    }

    @JsonProperty("extension_group_addon_name")
    private String extensionGroupAddonName;

    @JsonProperty("extension_group_instance_name")
    private String extensionGroupInstanceName;

    @JsonProperty("loc")
    private Location loc;

    @JsonProperty("property")
    private Map<String, Object> property; // Corresponds to ten_value_t *property in C
}