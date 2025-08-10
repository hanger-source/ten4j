package source.hanger.server.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpRequestLogger extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final Logger logger = LoggerFactory.getLogger(HttpRequestLogger.class);

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) throws Exception {
        logger.info("HttpRequestLogger: Received HTTP Request - Method: {}, URI: {}", msg.method(), msg.uri());
        // Pass the message to the next handler in the pipeline
        ctx.fireChannelRead(msg.retain());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("HttpRequestLogger: Exception caught in HTTP request logger", cause);
        ctx.close();
    }
}