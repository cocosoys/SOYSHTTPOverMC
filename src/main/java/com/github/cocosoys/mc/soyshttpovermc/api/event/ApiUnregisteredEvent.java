package com.github.cocosoys.mc.soyshttpovermc.api.event;

import org.bukkit.event.HandlerList;

import java.util.Collections;
import java.util.List;

/**
 * API 卸载事件：某插件注册的注解式 API 端点被移除后触发——两种来源：
 * <ul>
 *   <li>插件卸载：监听 {@code PluginDisableEvent}，网关<b>自动卸载</b>该插件名下全部 API；</li>
 *   <li>显式调用：插件在 {@code onDisable} 中调用 {@code ApiRegistry.unregister(instance) / unregisterPlugin(name)}。</li>
 * </ul>
 * 携带<b>注册插件名</b>与被移除端点的清单。其他插件可监听此事件清理关联资源、撤销缓存等。
 *
 * <p>本事件为<b>同步事件</b>：通常在插件 {@code onDisable}（主线程）触发，
 * 1.12.2 不允许从主线程触发异步事件（会抛 IllegalStateException），故强制同步。</p>
 */
public class ApiUnregisteredEvent extends GatewayEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String ownerPlugin;
    private final List<ApiInfo> apis;

    public ApiUnregisteredEvent(String ownerPlugin, List<ApiInfo> apis) {
        super(false); // 同步：卸载通常在主线程（onDisable）触发
        this.ownerPlugin = ownerPlugin == null ? "" : ownerPlugin;
        this.apis = apis == null ? Collections.<ApiInfo>emptyList() : apis;
    }

    /** 卸载这批 API 的插件名（由网关自动标记） */
    public String getOwnerPlugin() {
        return ownerPlugin;
    }

    /** 本次卸载的端点清单 */
    public List<ApiInfo> getApis() {
        return apis;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
