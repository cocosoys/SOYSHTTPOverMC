package com.github.cocosoys.mc.soyshttpovermc.api.event;

import com.github.cocosoys.mc.soyshttpovermc.api.ReloadHttpConfigHandler;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * /soyshttp reload 完成事件：SOYSHTTPOverMC 自身配置（网关策略/TLS/存储/语言/日志/web.home）已热重载后广播，
 * 监听此事件的插件可借此刷新自身与 HTTP 相关的配置。
 *
 * <p>等价于注册 {@link ReloadHttpConfigHandler} 钩子——二者都会被
 * {@code /soyshttp reload} 自动触发（即“自动检测”）。本事件适合不想显式注册钩子的插件。</p>
 */
public class HttpConfigReloadEvent extends Event {

    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
