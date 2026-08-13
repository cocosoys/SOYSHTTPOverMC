package soys.soyshttpovermc.event;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import soys.soyshttpovermc.api.event.ApiInfo;
import soys.soyshttpovermc.api.event.ApiRegisteredEvent;
import soys.soyshttpovermc.api.event.ApiUnregisteredEvent;
import soys.soyshttpovermc.api.event.GatewayAccessDeniedEvent;
import soys.soyshttpovermc.api.event.GatewayCredentialIssuedEvent;
import soys.soyshttpovermc.api.event.GatewayRequestEvent;
import soys.soyshttpovermc.api.event.GatewayRequestServedEvent;
import soys.soyshttpovermc.log.LogKit;

/**
 * 内置网关事件调试监听器（从 {@code HttpOverMcPlugin} 抽离）：
 * {@code gateway/config.yml} 的 {@code debug-events: true} 时打印网关各类事件日志；
 * 同时作为「其他插件如何监听网关事件」的范例。
 *
 * <p>事件调试开关由插件在启动/热重载后通过 {@link #setDebugEnabled(boolean)} 同步。</p>
 */
public class GatewayEventListener implements Listener {

    private volatile boolean debugEnabled = false;

    public void setDebugEnabled(boolean enabled) {
        this.debugEnabled = enabled;
    }

    @EventHandler
    public void onRequest(GatewayRequestEvent e) {
        if (!debugEnabled) return;
        LogKit.info("[EVENT] request " + e.getMethod() + " " + e.getPath()
                + " ip=" + e.getIp() + (e.isTls() ? " (TLS)" : ""));
    }

    @EventHandler
    public void onDenied(GatewayAccessDeniedEvent e) {
        if (!debugEnabled) return;
        LogKit.info("[EVENT] denied " + e.getMethod() + " " + e.getPath()
                + " ip=" + e.getIp() + " policy=" + e.getPolicyName() + " code=" + e.getStatusCode()
                + " reason=" + e.getReason());
    }

    @EventHandler
    public void onServed(GatewayRequestServedEvent e) {
        if (!debugEnabled) return;
        LogKit.info("[EVENT] served " + e.getMethod() + " " + e.getPath()
                + " code=" + e.getStatusCode() + " " + e.getLatencyMs() + "ms");
    }

    @EventHandler
    public void onIssued(GatewayCredentialIssuedEvent e) {
        if (!debugEnabled) return;
        LogKit.info("[EVENT] credential issued subject=" + e.getSubject()
                + " issuer=" + e.getIssuerName());
    }

    @EventHandler
    public void onApiRegistered(ApiRegisteredEvent e) {
        if (!debugEnabled) return;
        StringBuilder sb = new StringBuilder("[EVENT] api registered plugin=").append(e.getOwnerPlugin())
                .append(" count=").append(e.getApis().size());
        for (ApiInfo a : e.getApis()) {
            sb.append("\n    ").append(a.toString());
        }
        LogKit.info(sb.toString());
    }

    @EventHandler
    public void onApiUnregistered(ApiUnregisteredEvent e) {
        if (!debugEnabled) return;
        LogKit.info("[EVENT] api unregistered plugin=" + e.getOwnerPlugin()
                + " count=" + e.getApis().size());
    }
}
