package source.hanger.server.controller;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.netty.handler.codec.http.HttpMethod;

/**
 * {@code @HttpRequestMapping} 用于标记处理 HTTP 请求的方法。
 * 它可以指定请求的路径和 HTTP 方法（例如 GET, POST）。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface HttpRequestMapping {
    /**
     * 请求的 URI 路径。
     */
    String path();

    /**
     * 请求的 HTTP 方法，默认为 GET。
     */
    String method() default "GET";
}
