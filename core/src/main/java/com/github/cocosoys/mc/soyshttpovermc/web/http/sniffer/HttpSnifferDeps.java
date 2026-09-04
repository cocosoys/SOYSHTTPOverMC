package com.github.cocosoys.mc.soyshttpovermc.web.http.sniffer;

import com.github.cocosoys.mc.soyshttpovermc.web.RequestStats;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.GatewayFilter;
import com.github.cocosoys.mc.soyshttpovermc.web.http.HttpRequestHandler;
import org.bukkit.plugin.java.JavaPlugin;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLContext;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * 版本兼容嗅探器依赖容器：把 core {@link SocketSniffer} 装配所需的全部依赖打包，
 * 通过 {@link HttpSnifferInstaller} 传给各版本 adapter（v1_7x / v1_6x）自实现嗅探器。
 *
 * <p>字段语义与 {@link SocketSniffer} 构造参数一一对应，adapter 无需了解 core 装配细节。</p>
 */
public final class HttpSnifferDeps {

    private final JavaPlugin plugin;
    private final HttpRequestHandler handler;
    private final BooleanSupplier ready;
    private final int maxBody;
    private final RequestStats stats;
    private final GatewayFilter gateway;
    private final Supplier<SSLEngine> tlsEngineSupplier;
    private final Supplier<SSLContext> sslContextSupplier;
    private final boolean trustProxy;
    private final int httpConcurrency;
    private final int httpQueue;
    private final int keepAliveIdleSeconds;

    public HttpSnifferDeps(JavaPlugin plugin, HttpRequestHandler handler, BooleanSupplier ready,
                           int maxBody, RequestStats stats, GatewayFilter gateway,
                           Supplier<SSLEngine> tlsEngineSupplier, Supplier<SSLContext> sslContextSupplier,
                           boolean trustProxy,
                           int httpConcurrency, int httpQueue, int keepAliveIdleSeconds) {
        this.plugin = plugin;
        this.handler = handler;
        this.ready = ready;
        this.maxBody = maxBody;
        this.stats = stats;
        this.gateway = gateway;
        this.tlsEngineSupplier = tlsEngineSupplier;
        this.sslContextSupplier = sslContextSupplier;
        this.trustProxy = trustProxy;
        this.httpConcurrency = httpConcurrency;
        this.httpQueue = httpQueue;
        this.keepAliveIdleSeconds = keepAliveIdleSeconds;
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    public HttpRequestHandler handler() {
        return handler;
    }

    public BooleanSupplier ready() {
        return ready;
    }

    public int maxBody() {
        return maxBody;
    }

    public RequestStats stats() {
        return stats;
    }

    public GatewayFilter gateway() {
        return gateway;
    }

    public Supplier<SSLEngine> tlsEngineSupplier() {
        return tlsEngineSupplier;
    }

    public Supplier<SSLContext> sslContextSupplier() {
        return sslContextSupplier;
    }

    public boolean trustProxy() {
        return trustProxy;
    }

    public int httpConcurrency() {
        return httpConcurrency;
    }

    public int httpQueue() {
        return httpQueue;
    }

    public int keepAliveIdleSeconds() {
        return keepAliveIdleSeconds;
    }
}
