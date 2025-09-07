package source.hanger.server.controller;

import io.netty.handler.codec.http.FullHttpRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * 示例 HTTP 控制器，演示如何使用 {@code @HttpRequestController} 和
 * {@code @HttpRequestMapping} 注解。
 */
@HttpRequestController
@Slf4j
public class HelloWorldController {

    @HttpRequestMapping(path = "/hello")
    public String hello(FullHttpRequest request) {
        log.info("Received /hello request from: {}", request.headers().get("Host"));
        return "Hello from HelloWorld\nController! Your request method is: %s".formatted(request.method().name());
    }

    @HttpRequestMapping(path = "/greet", method = "POST")
    public String greet(FullHttpRequest request) {
        String content = request.content().toString(io.netty.util.CharsetUtil.UTF_8);
        log.info("Received /greet POST request with toolCallContext: {}", content);
        return "Greeting received: %s".formatted(content);
    }

    @HttpRequestMapping(path = "/info")
    public String info(FullHttpRequest request) {
        log.info("Received /info request.");
        return "This is an info page from the controller.";
    }
}
