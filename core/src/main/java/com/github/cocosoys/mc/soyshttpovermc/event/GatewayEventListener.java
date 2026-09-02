package com.github.cocosoys.mc.soyshttpovermc.event;

import com.github.cocosoys.mc.soyshttpovermc.api.event.*;
import lombok.CustomLog;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * 内置网关事件调试监听器（从 {@code HttpOverMcPlugin} 抽离）：
 * {@code gateway/config.yml} 的 {@code debug-events: true} 时打印网关各类事件日志；
 * 同时作为「其他插件如何监听网关事件」的范例。
 *
 * <p>事件调试开关由插件在启动/热重载后通过 {@link #setDebugEnabled(boolean)} 同步。</p>
 */
@CustomLog
public class GatewayEventListener implements Listener {

    private volatile boolean debugEnabled = false;

    public void setDebugEnabled(boolean enabled) {
        this.debugEnabled = enabled;
    }

    @EventHandler
    public void onRequest(GatewayRequestEvent e) {
        if (!debugEnabled) return;
        log.infoT("log.event.request", "[EVENT] request {0} {1} ip={2}{3}", e.getMethod(), e.getPath(), e.getIp(), e.isTls() ? " (TLS)" : "");
    }

    @EventHandler
    public void onDenied(GatewayAccessDeniedEvent e) {
        if (!debugEnabled) return;
        log.infoT("log.event.denied", "[EVENT] denied {0} {1} ip={2} policy={3} code={4} reason={5}",
                e.getMethod(), e.getPath(), e.getIp(), e.getPolicyName(), e.getStatusCode(), e.getReason());
    }

    @EventHandler
    public void onServed(GatewayRequestServedEvent e) {
        if (!debugEnabled) return;
        log.infoT("log.event.served", "[EVENT] served {0} {1} code={2} {3}ms", e.getMethod(), e.getPath(), e.getStatusCode(), e.getLatencyMs());
    }

    @EventHandler
    public void onIssued(GatewayCredentialIssuedEvent e) {
        if (!debugEnabled) return;
        log.infoT("log.event.issued", "[EVENT] credential issued subject={0} issuer={1}", e.getSubject(), e.getIssuerName());
    }

    @EventHandler
    public void onApiRegistered(ApiRegisteredEvent e) {
        if (!debugEnabled) return;
        StringBuilder sb = new StringBuilder("[EVENT] api registered plugin=").append(e.getOwnerPlugin())
                .append(" count=").append(e.getApis().size());
        for (ApiInfo a : e.getApis()) {
            sb.append("\n    ").append(a.toString());
        }
        log.info(sb.toString());
    }

    @EventHandler
    public void onApiUnregistered(ApiUnregisteredEvent e) {
        if (!debugEnabled) return;
        log.infoT("log.event.api-unregistered", "[EVENT] api unregistered plugin={0} count={1}", e.getOwnerPlugin(), e.getApis().size());
    }
}
