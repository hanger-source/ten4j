package source.hanger.core.extension.dashscope.util;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import source.hanger.core.extension.base.tool.LLMToolMetadata.ToolParameter;

/**
 * @author fuhangbo.hanger.uhfun
 **/
public class ToolConvertUtils {
    // 辅助方法：将 List<ToolParameter> 转换为 JsonObject (此处沿用旧 BaseLLMToolExtension 的逻辑)
    public static JsonObject convertParametersToJsonObject(List<ToolParameter> parameters) {
        JsonObject schemaObject = new JsonObject();
        schemaObject.addProperty("type", "object");

        JsonObject propertiesObject = new JsonObject();
        List<String> requiredList = new ArrayList<>();

        if (parameters != null) {
            for (ToolParameter param : parameters) {
                JsonObject paramProps = new JsonObject();
                paramProps.addProperty("type", param.getType());
                paramProps.addProperty("description", param.getDescription());
                propertiesObject.add(param.getName(), paramProps);
                if (param.isRequired()) {
                    requiredList.add(param.getName());
                }
            }
        }
        schemaObject.add("properties", propertiesObject);

        if (!requiredList.isEmpty()) {
            JsonArray jsonArray = new JsonArray();
            for (String requiredParam : requiredList) {
                jsonArray.add(requiredParam);
            }
            schemaObject.add("required", jsonArray);
        }
        return schemaObject;
    }
}
