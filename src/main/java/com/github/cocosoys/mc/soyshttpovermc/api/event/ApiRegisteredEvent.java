package com.github.cocosoys.mc.soyshttpovermc.api.event;

import org.bukkit.event.HandlerList;

import java.util.Collections;
import java.util.List;

/**
 * API 注册事件：某插件通过 {@code ApiRegistry.register(...)} 注册一批注解式 API 端点后触发。
 * 携带<b>注册插件名</b>与该批端点的清单（方法 / 路径 / 端点名 / 权限 / 处理器类）。
 *
 * <p>其他插件可监听此事件做路由审计、自动生成 OpenAPI 文档、权限联动、灰度开关等。
 * 网关在注册时<b>自动标记</b>注册 API 的插件名（按处理器实例的 ClassLoader 归属），无需调用方手动传入。</p>
 *
 * <p>本事件为<b>同步事件</b>：通常在插件 {@code onEnable}（主线程）触发，
 * 1.12.2 不允许从主线程触发异步事件（会抛 IllegalStateException），故强制同步。</p>
 */
public class ApiRegisteredEvent extends GatewayEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String ownerPlugin;
    private final List<ApiInfo> apis;

    public ApiRegisteredEvent(String ownerPlugin, List<ApiInfo> apis) {
        super(false); // 同步：注册通常在主线程（onEnable）触发
        this.ownerPlugin = ownerPlugin == null ? "" : ownerPlugin;
        this.apis = apis == null ? Collections.<ApiInfo>emptyList() : apis;
    }

    /**
     * 注册这批 API 的插件名（由网关自动标记）
     */
    public String getOwnerPlugin() {
        return ownerPlugin;
    }

    /**
     * 本次注册的端点清单
     */
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
