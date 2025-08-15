package source.hanger.server.controller;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;
import org.reflections.scanners.MethodAnnotationsScanner;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code HttpDispatcher} 负责扫描和分发 HTTP 请求到相应的控制器方法。
 * 它通过反射机制查找带有 {@code @HttpRequestController} 和 {@code @HttpRequestMapping}
 * 注解的类和方法，
 * 并将请求路由到正确的方法进行处理。
 */
@Slf4j
public class HttpDispatcher {

    private final Map<String, Map<HttpMethod, MethodInvoker>> routes = new ConcurrentHashMap<>();
    private final Map<Class<?>, Object> controllerInstances = new ConcurrentHashMap<>();

    public HttpDispatcher(String basePackage) {
        log.info("Initializing HttpDispatcher, scanning package: {}", basePackage);
        Reflections reflections = new Reflections(new ConfigurationBuilder()
                .setUrls(ClasspathHelper.forPackage(basePackage))
                .setScanners(new SubTypesScanner(false), new TypeAnnotationsScanner(), new MethodAnnotationsScanner()));

        Set<Class<?>> controllerClasses = reflections.getTypesAnnotatedWith(HttpRequestController.class);
        log.info("Found {} controller classes.", controllerClasses.size());

        for (Class<?> controllerClass : controllerClasses) {
            try {
                HttpRequestController controllerAnnotation = controllerClass.getAnnotation(HttpRequestController.class);
                String controllerBasePath = (controllerAnnotation != null) ? controllerAnnotation.value() : "/";

                Object controllerInstance = controllerClass.getDeclaredConstructor().newInstance();
                controllerInstances.put(controllerClass, controllerInstance);

                for (Method method : controllerClass.getMethods()) {
                    if (method.isAnnotationPresent(HttpRequestMapping.class)) {
                        HttpRequestMapping mapping = method.getAnnotation(HttpRequestMapping.class);
                        String routePath = mapping.path();

                        // 将字符串方法名解析为 HttpMethod 枚举
                        HttpMethod httpMethod = HttpMethod.valueOf(mapping.method());

                        // Combine controller base path and route path
                        String fullPath = combinePaths(controllerBasePath, routePath);

                        routes.computeIfAbsent(fullPath, k -> new HashMap<>()).put(httpMethod,
                                new MethodInvoker(controllerInstance, method));
                        log.info("Mapped: [{} {}] to {}.{}", httpMethod, fullPath, controllerClass.getName(),
                                method.getName());
                    }
                }
            } catch (InstantiationException | IllegalAccessException | NoSuchMethodException
                    | InvocationTargetException e) {
                log.error("Failed to create controller instance or register methods for {}: {}",
                        controllerClass.getName(), e.getMessage());
            }
        }
    }

    private String combinePaths(String basePath, String routePath) {
        if (basePath.endsWith("/")) {
            basePath = basePath.substring(0, basePath.length() - 1);
        }
        if (!routePath.startsWith("/")) {
            routePath = "/" + routePath;
        }
        return basePath + routePath;
    }

    /**
     * 分发 HTTP 请求到对应的处理方法。
     *
     * @param request 请求对象
     * @return 处理结果，通常是响应内容
     */
    public String dispatch(FullHttpRequest request) {
        String uri = request.uri();
        HttpMethod method = request.method();

        // 移除查询参数，只保留路径
        int questionMarkIndex = uri.indexOf('?');
        String path = questionMarkIndex > -1 ? uri.substring(0, questionMarkIndex) : uri;

        Map<HttpMethod, MethodInvoker> methodInvokers = routes.get(path);

        // Try with trailing slash if not found and path doesn't end with slash
        if (methodInvokers == null && !path.endsWith("/")) {
            methodInvokers = routes.get(path + "/");
        }
        // Try without trailing slash if not found and path ends with slash
        if (methodInvokers == null && path.endsWith("/") && path.length() > 1) {
            methodInvokers = routes.get(path.substring(0, path.length() - 1));
        }

        if (methodInvokers != null) {
            MethodInvoker invoker = methodInvokers.get(method);
            if (invoker != null) {
                try {
                    // 这里假设方法返回 String，且只接受 FullHttpRequest 作为参数
                    // 未来可以扩展为更复杂的参数解析和返回值处理
                    return (String) invoker.method.invoke(invoker.instance, request);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    log.error("Error invoking controller method for [{} {}]: {}", method, path, e.getMessage());
                    return "Internal Server Error: " + e.getMessage();
                }
            } else {
                return "Method Not Allowed"; // 405 Method Not Allowed
            }
        } else {
            return "Not Found"; // 404 Not Found
        }
    }

    private static class MethodInvoker {
        private final Object instance;
        private final Method method;

        public MethodInvoker(Object instance, Method method) {
            this.instance = instance;
            this.method = method;
        }
    }
}
