package source.hanger.server.controller;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@code @HttpRequestController} 用于标记一个类为 HTTP 请求处理器。
 * 被此注解标记的类可以包含使用 {@code @HttpRequestMapping} 标记的方法，以处理特定的 HTTP 请求。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface HttpRequestController {
    String value() default "/";
}
