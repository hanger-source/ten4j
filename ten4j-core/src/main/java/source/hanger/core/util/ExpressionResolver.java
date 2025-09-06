package source.hanger.core.util;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.mvel2.MVEL;
import lombok.extern.slf4j.Slf4j;
import org.mvel2.ParserContext;
import org.mvel2.integration.PropertyHandler;
import org.mvel2.integration.PropertyHandlerFactory;
import org.mvel2.integration.VariableResolver;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.integration.impl.CachingMapVariableResolverFactory;
import org.mvel2.integration.impl.SimpleValueResolver;
import org.mvel2.util.MethodStub;

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

        Matcher matcher = EXPRESSION_PATTERN.matcher(expressionString);

        if (matcher.matches()) {
            String mvelExpression = matcher.group(1).trim();
            try {
                Serializable compiledExpression = MVEL.compileExpression(mvelExpression);
                ParserContext parserContext = new ParserContext();
                return MVEL.executeExpression(compiledExpression, parserContext, new InnerVariableResolverFactory(combinedContext));
            } catch (Exception e) {
                throw new IllegalStateException(
                    "Error resolving MVEL expression {}. Returning original placeholder. Error: {}%s"
                        .formatted(mvelExpression), e);
            }
        }
        return expressionString;
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

    static class InnerVariableResolverFactory extends CachingMapVariableResolverFactory {
        static {
            PropertyHandlerFactory.registerPropertyHandler(ParserContext.class, new NullablePropertyHandler());
        }

        public InnerVariableResolverFactory(Map variables) {
            super(variables);
        }
        @Override
        public VariableResolver getVariableResolver(String name) {
            if (name.equals("env")) {
                return new SimpleValueResolver(new MethodStub(System.class, "getProperty")) {
                };
            }
            return super.getVariableResolver(name);
        }

        @Override
        public boolean isResolveable(String name) {
            return name.equals("env") || super.isResolveable(name);
        }
    }

    static class NullablePropertyHandler implements PropertyHandler {
        @Override
        public Object getProperty(String s, Object o, VariableResolverFactory variableResolverFactory) {
            return null;
        }

        @Override
        public Object setProperty(String s, Object o, VariableResolverFactory variableResolverFactory, Object o1) {
            return null;
        }
    }
}
