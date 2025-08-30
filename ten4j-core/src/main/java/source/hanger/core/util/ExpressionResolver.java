package source.hanger.core.util;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.mvel2.MVEL;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ExpressionResolver {

    // 匹配 {{expression}}，其中 expression 可以包含 MVEL 表达式，包括默认值和环境变量
    private static final Pattern EXPRESSION_PATTERN = Pattern.compile("\\{\\{(.+?)}}");

    public static Object resolve(String expressionString, Map<String, Object> context) {
        if (expressionString == null || expressionString.isEmpty()) {
            return expressionString;
        }

        Map<String, Object> combinedContext = new HashMap<>();
        if (context != null) {
            combinedContext.putAll(context);
        }

        StringBuilder sb = new StringBuilder();
        Matcher matcher = EXPRESSION_PATTERN.matcher(expressionString);
        boolean found = false;

        while (matcher.find()) {
            found = true;
            String mvelExpression = matcher.group(1).trim();

            // This allows MVEL to correctly interpret {{env:VAR_NAME}}
            if (mvelExpression.startsWith("env:")) {
                String varName = mvelExpression.substring(4);
                mvelExpression = "java.lang.System.getProperty('%s')".formatted(varName);
            }

            try {
                Serializable compiledExpression = MVEL.compileExpression(mvelExpression);
                Object result = MVEL.executeExpression(compiledExpression, combinedContext);
                log.debug("Resolved expression: {} -> {}", mvelExpression, result);
                matcher.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf(result)));
            } catch (Exception e) {
                log.warn("Error resolving MVEL expression {}. Returning original placeholder. Error: {}", mvelExpression, e.getMessage());
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        matcher.appendTail(sb);

        if (found) {
            return sb.toString();
        } else {
            return expressionString;
        }
    }

    public static Object resolveProperties(Object value, Map<String, Object> context) {
        if (value instanceof String) {
            return resolve((String) value, context);
        } else if (value instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) value;
            Map<String, Object> resolvedMap = new HashMap<>();
            map.forEach((k, v) -> resolvedMap.put(k, resolveProperties(v, context)));
            return resolvedMap;
        } else if (value instanceof java.util.List) {
            java.util.List<Object> list = (java.util.List<Object>) value;
            java.util.List<Object> resolvedList = new java.util.ArrayList<>();
            list.forEach(item -> resolvedList.add(resolveProperties(item, context)));
            return resolvedList;
        }
        return value;
    }
}
