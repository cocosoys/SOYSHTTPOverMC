package com.github.cocosoys.mc.soyshttpovermc.api.event;

import org.bukkit.event.HandlerList;

/**
 * 网关请求处理完成事件：一条请求已处理完毕（含被拒绝与异常）时触发。
 * 携带最终状态码与端到端耗时，可用来做指标统计、监控告警。
 */
public class GatewayRequestServedEvent extends GatewayEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String method;
    private final String path;
    private final String ip;
    private final boolean tls;
    private final int statusCode;
    private final long latencyUs;

    public GatewayRequestServedEvent(String method, String path, String ip, boolean tls,
                                     int statusCode, long latencyUs) {
        this.method = method == null ? "" : method;
        this.path = path == null ? "/" : path;
        this.ip = ip == null ? "0.0.0.0" : ip;
        this.tls = tls;
        this.statusCode = statusCode;
        this.latencyUs = latencyUs;
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

    public int getStatusCode() {
        return statusCode;
    }

    /**
     * 端到端耗时（微秒，含隧道往返）
     */
    public long getLatencyUs() {
        return latencyUs;
    }

    public long getLatencyMs() {
        return latencyUs / 1000;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
