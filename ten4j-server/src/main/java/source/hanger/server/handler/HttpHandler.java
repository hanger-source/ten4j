package source.hanger.server.handler;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter; // Changed to ChannelInboundHandlerAdapter
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil; // Added for manual release
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
public class HttpHandler extends ChannelInboundHandlerAdapter { // Changed parent class

    private final HttpDispatcher dispatcher;

    public HttpHandler(String controllerPackage) {
        this.dispatcher = new HttpDispatcher(controllerPackage);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        log.debug("HttpHandler received message of type: {}", msg.getClass().getName());

        if (msg instanceof FullHttpRequest) {
            FullHttpRequest request = (FullHttpRequest) msg;
            log.debug("HttpHandler processing FullHttpRequest for URI: {}", request.uri());

            if (request.uri().startsWith("/websocket")) {
                log.debug("HttpHandler: WebSocket upgrade request detected. Passing to next handler.");
                // 如果是 WebSocket 升级请求，保留消息并传递给下一个 handler
                // 不在此处释放，因为 WebSocketServerProtocolHandler 会处理
                ctx.fireChannelRead(request.retain());
                return;
            }

            // 使用 Dispatcher 处理 HTTP 请求
            log.debug("HttpHandler: Handling regular HTTP request for URI: {}", request.uri());
            String responseContent = dispatcher.dispatch(request);
            HttpResponseStatus status = OK;
            if ("Not Found".equals(responseContent)) {
                status = NOT_FOUND;
            } else if ("Method Not Allowed".equals(responseContent)) {
                status = METHOD_NOT_ALLOWED;
            } else if (responseContent.startsWith("Internal Server Error")) {
                status = INTERNAL_SERVER_ERROR;
            }
            sendHttpResponse(ctx, request, responseContent, status);

            // 关键：手动释放 FullHttpRequest，因为不再是 SimpleChannelInboundHandler 自动释放
            ReferenceCountUtil.release(request);
            log.debug("HttpHandler: FullHttpRequest released for URI: {}. Processing stopped.", request.uri());

            // 不再调用 super.channelRead(msg); 以防止消息继续向下游传递
        } else {
            log.debug("HttpHandler: Non-FullHttpRequest message. Passing to next handler: {}",
                    msg.getClass().getName());
            // 如果不是 FullHttpRequest，则传递给下一个 handler
            ctx.fireChannelRead(msg);
        }
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

        // 写入并刷新响应。注意：这不会释放原始的 FullHttpRequest
        ctx.writeAndFlush(response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("HttpHandler 发生异常: {}", cause.getMessage(), cause);
        ctx.close();
    }
}
