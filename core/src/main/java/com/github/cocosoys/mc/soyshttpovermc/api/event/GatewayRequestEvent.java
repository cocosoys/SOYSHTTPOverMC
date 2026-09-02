package com.github.cocosoys.mc.soyshttpovermc.api.event;

import org.bukkit.event.HandlerList;

import java.util.Collections;
import java.util.Map;

/**
 * 网关请求进入事件：一条 HTTP(S) 请求进入网关（安全策略判定之前）触发。
 * 可用来做访问审计、统计、自定义拦截提示等。
 */
public class GatewayRequestEvent extends GatewayEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String method;
    private final String path;
    private final String ip;
    private final boolean tls;
    private final Map<String, String> headers;

    public GatewayRequestEvent(String method, String path, String ip, boolean tls, Map<String, String> headers) {
        this.method = method == null ? "" : method;
        this.path = path == null ? "/" : path;
        this.ip = ip == null ? "0.0.0.0" : ip;
        this.tls = tls;
        this.headers = headers == null ? Collections.<String, String>emptyMap() : headers;
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public String getIp() {
        return ip;
    }

    public boolean isTls() {
        return tls;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
