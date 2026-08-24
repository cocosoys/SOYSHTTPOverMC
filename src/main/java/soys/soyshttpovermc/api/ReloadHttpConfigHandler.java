package soys.soyshttpovermc.api;

/**
 * 热重载钩子（函数式接口）：其它插件（或提供 /soyshttp 子指令的模块）实现此接口并经由
 * {@link SoysHttpOverMcApi#registerReloadHook(ReloadHttpConfigHandler)} 注册后，执行
 * {@code /soyshttp reload} 时会随 SOYSHTTPOverMC 一同刷新自身与 HTTP 相关的配置，无需重启服务端。
 *
 * <p>典型用法（其它插件 onEnable 中）：</p>
 * <pre>
 *   SoysHttpOverMcApi api = HttpOverMcPlugin.getInstance().getApi();
 *   api.registerReloadHook(() -> myPlugin.reloadMyHttpConfig());
 * </pre>
 *
 * <p>等价机制：SOYSHTTPOverMC 在每次 {@code /soyshttp reload} 完成时还会广播
 * {@code HttpConfigReloadEvent}（Bukkit 事件），不想显式注册钩子的插件可直接监听该事件，
 * 二者均为“自动检测”——{@code /soyshttp reload} 会统一触发它们。</p>
 */
@FunctionalInterface
public interface ReloadHttpConfigHandler {

    /** 热重载回调：在此重新加载本插件与 HTTP 相关的配置（勿在此再次调用 /soyshttp reload，避免递归）。 */
    void onReload();
}
