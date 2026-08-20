package soys.soyshttpovermc.gateway.policy.auth.bridge.spi;
import lombok.CustomLog;

import soys.soyshttpovermc.i18n.I18n;
import soys.soyshttpovermc.log.LogKit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 登录插件提供者工厂（抽象工厂）：维护登录插件 SPI 注册表，网关统一从这里取"当前可用提供者"。
 *
 * <h3>接入新登录插件</h3>
 * <ol>
 *   <li>实现 {@link LoginProvider}（如 {@code XAuthLoginProvider}）；</li>
 *   <li>检测到对应插件已加载时（主线程、软依赖延迟加载防 NoClassDefFoundError）：
 *       {@code LoginProviderFactory.register(new XAuthLoginProvider());}</li>
 *   <li>重启或 /soyshttp reload 后，网关自动经 {@link #active()} 选取并使用。</li>
 * </ol>
 */
@CustomLog
public final class LoginProviderFactory {

    private static final Map<String, LoginProvider> REGISTRY = new LinkedHashMap<>();
    private static volatile LoginProviderContext context;

    private LoginProviderFactory() {
    }

    /** 配置运行上下文（宿主插件实例；onEnable 时调用一次）。 */
    public static void configure(LoginProviderContext ctx) {
        context = ctx;
    }

    /** 当前运行上下文（可能为 null：onEnable 前）。 */
    public static LoginProviderContext context() {
        return context;
    }

    /** 注册提供者（同 name 覆盖）。 */
    public static void register(LoginProvider provider) {
        if (provider == null) return;
        REGISTRY.put(provider.name(), provider);
        log.infoT("log.auth.registry.registered", "登录插件提供者已注册: {0} ({1}) - {2}", provider.name(),
                provider.displayName(), provider.description());
    }

    /** 按名字取提供者。 */
    public static LoginProvider get(String name) {
        return name == null ? null : REGISTRY.get(name);
    }

    /** 卸载提供者（插件禁用时由宿主调用）。 */
    public static void unregister(String name) {
        if (name != null) {
            REGISTRY.remove(name);
        }
    }

    /** 全部已注册提供者（按注册顺序）。 */
    public static List<LoginProvider> all() {
        return Collections.unmodifiableList(new ArrayList<>(REGISTRY.values()));
    }

    /**
     * 当前可用的登录插件提供者（第一个 isAvailable 的）。
     * <b>必须在主线程调用</b>（isAvailable 内部访问插件管理器）。
     */
    public static LoginProvider active() {
        for (LoginProvider p : REGISTRY.values()) {
            if (p.isAvailable()) {
                return p;
            }
        }
        return null;
    }

    /** 关闭全部提供者（插件卸载时由宿主调用）。 */
    public static void shutdownAll() {
        for (LoginProvider p : REGISTRY.values()) {
            try {
                p.shutdown();
            } catch (Throwable ignored) {
            }
        }
    }
}
