package com.github.cocosoys.mc.soyshttpovermc.api.event;

import org.bukkit.event.HandlerList;

/**
 * 网关访问拒绝事件：某条请求被安全策略链拒绝（401/403/426/429/500 等）时触发。
 * 可用来做安全告警、封禁联动、审计日志等。
 */
public class GatewayAccessDeniedEvent extends GatewayEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String method;
    private final String path;
    private final String ip;
    private final boolean tls;
    private final String policyName;
    private final int statusCode;
    private final String reason;

    public GatewayAccessDeniedEvent(String method, String path, String ip, boolean tls,
                                    String policyName, int statusCode, String reason) {
        this.method = method == null ? "" : method;
        this.path = path == null ? "/" : path;
        this.ip = ip == null ? "0.0.0.0" : ip;
        this.tls = tls;
        this.policyName = policyName == null ? "" : policyName;
        this.statusCode = statusCode;
        this.reason = reason == null ? "" : reason;
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

    /** 拒绝该请求的策略名（如 tls / auth / rate-limit / ip-allowlist） */
    public String getPolicyName() {
        return policyName;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getReason() {
        return reason;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
