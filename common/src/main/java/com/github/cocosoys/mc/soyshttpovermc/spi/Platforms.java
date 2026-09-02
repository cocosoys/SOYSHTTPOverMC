package com.github.cocosoys.mc.soyshttpovermc.spi;

import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * 平台门面：common 包通过 {@link #get()} 获取当前平台实现，不直接依赖任何平台类型。
 *
 * <p>解析优先级：</p>
 * <ol>
 *   <li><b>版本模块覆盖</b>（ServiceLoader）：其他版本模块在 META-INF/services 注册的 Platform 实现优先；
 *       该实现通常继承 core 的 {@code PlatformBukkitImpl} 并仅覆写需要差异化的方法（部分覆盖）。</li>
 *   <li><b>core 兜底</b>：{@link #bind} 显式绑定的默认实现（PlatformBukkitImpl）。</li>
 * </ol>
 */
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
            try {
                ServiceLoader<Platform> loader = ServiceLoader.load(Platform.class);
                Iterator<Platform> it = loader.iterator();
                if (it.hasNext()) {
                    serviceResolved = it.next();
                }
            } catch (Throwable ignored) {
                // ServiceLoader 失败不阻断，回退到 bound
            }
        }
        if (serviceResolved != null) return serviceResolved;
        return bound;
    }
}