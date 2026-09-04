package com.github.cocosoys.mc.soyshttpovermc.spi;

import lombok.CustomLog;

import java.util.Iterator;
import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 平台门面：common 包通过 {@link #get()} 获取当前平台实现，不直接依赖任何平台类型。
 *
 * <p>解析优先级：</p>
 * <ol>
 *   <li><b>版本模块覆盖</b>（ServiceLoader）：其他版本模块在 META-INF/services 注册的 Platform 实现优先；
 *       该实现通常继承 core 的 {@code PlatformBukkitImpl} 并仅覆写需要差异化的方法（部分覆盖）。</li>
 *   <li><b>core 兜底</b>：{@link #bind} 显式绑定的默认实现（PlatformBukkitImpl）。</li>
 * </ol>
 *
 * <p><b>ServiceLoader 加载器约定</b>：必须用 {@code Platforms.class.getClassLoader()}
 * （即本类所在 jar 的插件 classloader）而非线程 context classloader——Spigot 在 onEnable 期间
 * Server thread 的 context classloader 是服务端加载器，看不到插件 fat jar 内 META-INF/services 的
 * 版本 Platform 注册（低版本 UTF-8 覆盖等版本差异将因此静默失效）。</p>
 */
@CustomLog
public final class Platforms {
    private static volatile Platform bound;
    private static volatile Platform serviceResolved;
    private static volatile boolean serviceSearched = false;

    private Platforms() {
    }

    /**
     * 绑定默认实现（core 在 onEnable 时调用，作为兜底）。
     */
    public static void bind(Platform p) {
        if (p != null) bound = p;
    }

    /**
     * 获取当前平台实现；未绑定且无 ServiceLoader 实现时抛出异常。
     */
    public static Platform get() {
        Platform p = find();
        if (p == null) {
            throw new IllegalStateException("未找到 Platform 实现：请先调用 Platforms.bind(...) 或提供 ServiceLoader 注册");
        }
        return p;
    }

    /**
     * 获取当前平台实现；不存在时返回 null。
     */
    public static Platform getOrNull() {
        return find();
    }

    private static Platform find() {
        if (!serviceSearched) {
            serviceSearched = true;
            // 必须用本类所在 jar 的插件 classloader：线程 context classloader（Spigot onEnable 期间为服务端
            // 加载器）看不到插件 jar 内 META-INF/services 的版本 Platform 注册，会导致版本模块覆盖静默失效。
            ClassLoader cl = Platforms.class.getClassLoader();
            try {
                ServiceLoader<Platform> loader = ServiceLoader.load(Platform.class, cl);
                Iterator<Platform> it = loader.iterator();
                if (it.hasNext()) {
                    serviceResolved = it.next();
                    log.info("ServiceLoader 解析 Platform 实现: " + serviceResolved.getClass().getName()
                            + " (classloader=" + cl + ")");
                } else {
                    log.info("ServiceLoader 未发现 Platform 实现 (classloader=" + cl + ")，将回退 core 默认绑定");
                }
            } catch (Throwable t) {
                // ServiceLoader 失败不阻断，回退到 bound；记录日志便于排障（原实现静默吞异常，失败零日志）
                log.warn("ServiceLoader 加载 Platform 实现失败，将回退 core 默认绑定", t);
            }
        }
        if (serviceResolved != null) return serviceResolved;
        return bound;
    }
}