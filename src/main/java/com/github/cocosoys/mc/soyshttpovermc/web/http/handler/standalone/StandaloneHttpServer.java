package com.github.cocosoys.mc.soyshttpovermc.web.http.handler.standalone;

import com.github.cocosoys.mc.soyshttpovermc.web.RequestStats;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.Credential;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.GatewayContext;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.GatewayFilter;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.PolicyResult;
import com.github.cocosoys.mc.soyshttpovermc.web.http.HttpRequestHandler;
import com.github.cocosoys.mc.soyshttpovermc.web.proto.FrameProto;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import org.bukkit.plugin.java.JavaPlugin;

import javax.net.ssl.SSLEngine;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 独立 HTTP 服务器模式：在独立端口启动 Netty HTTP 服务器，不占用 MC 端口，无需嗅探器。
 *
 * <p>与同端口嗅探模式（SocketSniffer）的区别：
 * <ul>
 *   <li>独立端口：HTTP 和 MC 完全分离，互不干扰</li>
 *   <li>无需嗅探：不修改 Spigot 的 Netty pipeline，稳定性更高</li>
 *   <li>延迟更低：直接 HTTP 编解码，无需首包嗅探判断协议类型</li>
 *   <li>不支持同端口：访问网页需使用独立端口（如 http://127.0.0.1:25565/）</li>
 * </ul>
 *
 * <p>支持 TLS（HTTPS）、网关策略链、跨服路由（通过 HttpRequestHandler）。
 */
public class StandaloneHttpServer {

    private final JavaPlugin plugin;
    private final HttpRequestHandler handler;
    private final GatewayFilter gateway;
    private final RequestStats stats;
    private final int port;
    private final String host;
    private final Supplier<SSLEngine> tlsEngineSupplier;
    private final int maxBodyBytes;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    private volatile boolean running = false;

    public StandaloneHttpServer(JavaPlugin plugin, HttpRequestHandler handler,
                                GatewayFilter gateway, RequestStats stats,
                                String host, int port,
                                Supplier<SSLEngine> tlsEngineSupplier,
                                int maxBodyBytes) {
        this.plugin = plugin;
        this.handler = handler;
        this.gateway = gateway;
        this.stats = stats;
        this.host = host == null || host.isEmpty() ? "0.0.0.0" : host;
        this.port = port;
        this.tlsEngineSupplier = tlsEngineSupplier;
        this.maxBodyBytes = Math.max(1024, maxBodyBytes);
    }

    /** 启动独立 HTTP 服务器。 */
    public void start() {
        if (running) return;
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup(2);
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            if (tlsEngineSupplier != null) {
                                SSLEngine engine = tlsEngineSupplier.get();
                                engine.setUseClientMode(false);
                                ch.pipeline().addLast("ssl", new SslHandler(engine));
                            }
                            ch.pipeline().addLast("codec", new HttpServerCodec());
                            ch.pipeline().addLast("aggregator", new HttpObjectAggregator(maxBodyBytes));
                            ch.pipeline().addLast("handler", new StandaloneHandler());
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            ChannelFuture f = b.bind(host, port).sync();
            serverChannel = f.channel();
            running = true;
            plugin.getLogger().info("[HTTP-Over-MC] 独立 HTTP 服务器已启动: " + host + ":" + port
                    + (tlsEngineSupplier != null ? " (TLS/HTTPS)" : " (HTTP)"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            shutdown();
            throw new RuntimeException("启动独立 HTTP 服务器失败", e);
        } catch (Exception e) {
            shutdown();
            throw new RuntimeException("启动独立 HTTP 服务器失败", e);
        }
    }

    /** 关闭独立 HTTP 服务器。 */
    public void shutdown() {
        running = false;
        if (serverChannel != null) {
            try {
                serverChannel.close().sync();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            serverChannel = null;
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
            bossGroup = null;
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
            workerGroup = null;
        }
        plugin.getLogger().info("[HTTP-Over-MC] 独立 HTTP 服务器已关闭");
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return port;
    }

    /** 独立服务器的请求处理器。 */
    private class StandaloneHandler extends ChannelInboundHandlerAdapter {

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (!(msg instanceof FullHttpRequest)) {
                ReferenceCountUtil.release(msg);
                return;
            }
            FullHttpRequest req = (FullHttpRequest) msg;
            long t0 = System.nanoTime();
            try {
                String method = req.method().name();
                String path = req.uri();
                Map<String, String> headers = convertHeaders(req.headers());
                byte[] body = readBody(req.content());
                String clientIp = getClientIp(ctx, headers);
                boolean tls = ctx.pipeline().get(SslHandler.class) != null;

                // 网关策略链
                if (gateway != null) {
                    Credential cred = gateway.resolveCredential(headers);
                    GatewayContext gctx = new GatewayContext(method, handler.policyPath(path),
                            headers, clientIp, tls, cred, path);
                    GatewayFilter.Outcome oc = gateway.filterDetailed(gctx);
                    PolicyResult res = oc.result;
                    if (!res.isAllow()) {
                        String denyContentType = res.getHeaders().get("Content-Type");
                        if (denyContentType == null) denyContentType = "application/json; charset=utf-8";
                        writeResponse(ctx, res.getStatusCode(), res.getBodyBytes(),
                                denyContentType, true);
                        return;
                    }
                }

                // 业务处理
                FrameProto.HttpResponseFrame resp = handler.handle(method, path, headers, body);
                int statusCode = resp.getStatusCode();
                byte[] respBody = resp.getBody().toByteArray();
                String contentType = resp.getHeadersMap().get("Content-Type");
                if (contentType == null) contentType = "application/octet-stream";

                writeResponse(ctx, statusCode, respBody, contentType, true);

                // 统计
                if (stats != null) {
                    long elapsedUs = (System.nanoTime() - t0) / 1000;
                    stats.recordRequest(method, path, statusCode, elapsedUs);
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("[HTTP-Over-MC] 独立服务器处理请求异常: " + t);
                writeResponse(ctx, 500, "{\"msg\":\"Internal Server Error\",\"code\":500}".getBytes(CharsetUtil.UTF_8),
                        "application/json; charset=utf-8", false);
            } finally {
                ReferenceCountUtil.release(req);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            plugin.getLogger().warning("[HTTP-Over-MC] 独立服务器连接异常: " + cause);
            ctx.close();
        }

        private Map<String, String> convertHeaders(HttpHeaders headers) {
            Map<String, String> map = new HashMap<>();
            for (Map.Entry<String, String> e : headers) {
                map.put(e.getKey(), e.getValue());
            }
            return map;
        }

        private byte[] readBody(ByteBuf buf) {
            if (buf == null || buf.readableBytes() == 0) return new byte[0];
            byte[] bytes = new byte[buf.readableBytes()];
            buf.getBytes(buf.readerIndex(), bytes);
            return bytes;
        }

        private String getClientIp(ChannelHandlerContext ctx, Map<String, String> headers) {
            // 优先使用 X-Forwarded-For（如果配置了信任代理）
            String xff = headers.get("X-Forwarded-For");
            if (xff != null && !xff.isEmpty()) {
                int comma = xff.indexOf(',');
                return comma > 0 ? xff.substring(0, comma).trim() : xff.trim();
            }
            if (ctx.channel().remoteAddress() instanceof InetSocketAddress) {
                return ((InetSocketAddress) ctx.channel().remoteAddress()).getAddress().getHostAddress();
            }
            return "0.0.0.0";
        }

        private void writeResponse(ChannelHandlerContext ctx, int statusCode, byte[] body,
                                   String contentType, boolean keepAlive) {
            ByteBuf content = Unpooled.wrappedBuffer(body);
            FullHttpResponse resp = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.valueOf(statusCode), content);
            resp.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
            resp.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
            if (keepAlive) {
                resp.headers().set(HttpHeaderNames.CONNECTION, "keep-alive");
            } else {
                resp.headers().set(HttpHeaderNames.CONNECTION, "close");
            }
            ctx.writeAndFlush(resp);
            if (!keepAlive) {
                ctx.close();
            }
        }
    }
}
