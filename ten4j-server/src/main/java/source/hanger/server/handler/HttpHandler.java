package source.hanger.server.handler;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.util.CharsetUtil;
import lombok.extern.slf4j.Slf4j;
import source.hanger.server.controller.HttpDispatcher;

import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * HttpHandler 类用于处理 Netty 管道中的 HTTP 请求。
 * 它会检查请求的 URI，如果请求是 WebSocket 升级请求，它将不处理并让 WebSocketServerProtocolHandler 接管。
 * 否则，它将处理常规的 HTTP GET 请求，并根据路径返回不同的响应。
 */
@Slf4j
public class HttpHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private final HttpDispatcher dispatcher;

    public HttpHandler(String controllerPackage) {
        this.dispatcher = new HttpDispatcher(controllerPackage);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) {
        if (msg.uri().startsWith("/websocket")) {
            // 如果是 WebSocket 升级请求，不处理并传递给下一个 handler
            ctx.fireChannelRead(msg);
            return;
        }

        // 使用 Dispatcher 处理 HTTP 请求
        String responseContent = dispatcher.dispatch(msg);
        HttpResponseStatus status = OK;
        if ("Not Found".equals(responseContent)) {
            status = NOT_FOUND;
        } else if ("Method Not Allowed".equals(responseContent)) {
            status = METHOD_NOT_ALLOWED;
        } else if (responseContent.startsWith("Internal Server Error")) {
            status = INTERNAL_SERVER_ERROR;
        }
        sendHttpResponse(ctx, msg, responseContent, status);

    }

    private void sendHttpResponse(ChannelHandlerContext ctx, FullHttpRequest request, String content) {
        sendHttpResponse(ctx, request, content, OK);
    }

    private void sendHttpResponse(ChannelHandlerContext ctx, FullHttpRequest request, String content,
        io.netty.handler.codec.http.HttpResponseStatus status) {
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, status,
            Unpooled.copiedBuffer(content, CharsetUtil.UTF_8));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        HttpUtil.setContentLength(response, response.content().readableBytes());

        ctx.writeAndFlush(response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("HttpHandler 发生异常: {}", cause.getMessage(), cause);
        ctx.close();
    }
}
