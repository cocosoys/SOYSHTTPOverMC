package com.github.cocosoys.mc.soyshttpovermc.web.http.handler.netty;

import com.github.cocosoys.mc.soyshttpovermc.proxy.ServerRegistry;
import com.github.cocosoys.mc.soyshttpovermc.web.WebFrontendHandler;
import com.github.cocosoys.mc.soyshttpovermc.web.http.AbstractHttpRequestHandler;
import com.github.cocosoys.mc.soyshttpovermc.web.proto.FrameProto;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Netty EventLoop 模式（默认）：将请求提交到独立的 Netty EventLoop 线程处理。
 *
 * <p>请求被提交到独立的 NioEventLoopGroup（默认 2 线程），在 EventLoop 线程中调用
 * WebFrontendHandler.handle()，通过 CompletableFuture 同步等待结果。
 *
 * <p>延迟极低（~1-3ms），且不阻塞网关的 Netty IO 线程，是推荐的默认模式。支持跨服路由。</p>
 */
public class NettyEventLoopRequestHandler extends AbstractHttpRequestHandler {

    private final EventLoopGroup eventLoop;

    public NettyEventLoopRequestHandler(WebFrontendHandler web, ServerRegistry registry, String localServerName) {
        this(web, registry, localServerName, 2);
    }

    public NettyEventLoopRequestHandler(WebFrontendHandler web, ServerRegistry registry,
                                          String localServerName, int threads) {
        super(web, registry, localServerName);
        this.eventLoop = new NioEventLoopGroup(Math.max(1, threads));
    }

    @Override
    protected FrameProto.HttpResponseFrame handleLocal(String method, String path,
                                                          Map<String, String> headers, byte[] body)
            throws Exception {
        CompletableFuture<FrameProto.HttpResponseFrame> future = new CompletableFuture<>();
        eventLoop.execute(() -> {
            try {
                FrameProto.HttpResponseFrame resp = web.handle(method, path, headers, body);
                future.complete(resp);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } catch (ExecutionException e) {
            throw (Exception) e.getCause();
        }
    }

    @Override
    public void shutdown() {
        eventLoop.shutdownGracefully();
    }

    @Override
    public String name() {
        return "netty-eventloop";
    }
}
