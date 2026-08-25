package com.github.cocosoys.mc.soyshttpovermc.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import com.github.cocosoys.mc.soyshttpovermc.api.SoysHttpOverMcApi;

/**
 * SOYSHTTPOverMC 就绪事件：宿主插件 onEnable 完成全部初始化（API 门面 / 网页 / 网关 / Bot / 前端）
 * 后触发。
 *
 * <p><b>解决「第三方插件先于 SOYS 加载」的延迟注册问题</b>：第三方插件若在自身 onEnable 中
 * 发现 {@code HttpOverMcPlugin.getInstance()} 或 {@code getApi()} 尚不可用，可监听本事件，
 * 在事件回调里执行 {@code HttpOverMcPlugin.getInstance().getApi()} 的注册动作。
 * 若第三方插件加载晚于 SOYS（更常见），则直接在其 onEnable 中注册即可（门面已就绪），
 * 无需等待本事件。
 *
 * <p>事件为同步事件（主线程触发）；监听器异常不影响触发方。
 */
public class SoysReadyEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final SoysHttpOverMcApi api;

    public SoysReadyEvent(SoysHttpOverMcApi api) {
        this.api = api;
    }

    /** 就绪后的对外门面（注册 API / 网页 / 凭证 / Bot / 拦截器等）。 */
    public SoysHttpOverMcApi getApi() {
        return api;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
