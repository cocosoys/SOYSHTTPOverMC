package soys.soyshttpovermc.event;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;

import soys.soyshttpovermc.api.ApiRegistry;

/**
 * API 自动生命周期监听器（从 {@code HttpOverMcPlugin} 抽离）：
 * 任一插件（非本插件）被禁用时，自动卸载其名下全部注解式 API
 * （触发 {@code ApiUnregisteredEvent}），避免第三方插件残留导致内存泄漏/路由污染。
 */
public class ApiLifecycleListener implements Listener {

    private final ApiRegistry apiRegistry;
    private final Plugin hostPlugin;

    public ApiLifecycleListener(ApiRegistry apiRegistry, Plugin hostPlugin) {
        this.apiRegistry = apiRegistry;
        this.hostPlugin = hostPlugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent e) {
        if (apiRegistry == null) return;
        if (e.getPlugin() == hostPlugin) return; // 本插件卸载由 onDisable 统一处理
        apiRegistry.unregisterPlugin(e.getPlugin().getName());
    }
}
