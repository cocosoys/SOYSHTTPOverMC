package com.github.cocosoys.mc.soyshttpovermc.adapter.compat;

import lombok.CustomLog;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 版本中立 Bukkit 兼容工具：抹平跨版本 API 差异（零 Bukkit 编译期依赖，全部反射）。
 *
 * <p><b>核心原则</b>：本模块不 import 任何 org.bukkit 类，对 Bukkit 的访问一律经
 * {@code Class.forName} + {@code Method.invoke} 反射完成；凡在目标版本段
 * （1.6.4 ~ 1.12.2）签名不一致的方法，反射调用，禁止编译期直接绑定，
 * 避免低版本服务器运行时 {@code NoSuchMethodError} / {@code NoClassDefFoundError}。</p>
 *
 * <p>已覆盖差异：</p>
 * <ul>
 *   <li>{@link #onlinePlayers()}：1.8+ 返回 {@code Collection}，1.6/1.7 返回 {@code Player[]}；
 *       反射优先取 Collection 重载；返回值统一为 {@code List<?>}（元素为 Player 的 Object 视图）。</li>
 * </ul>
 */
@CustomLog
public final class BukkitCompat {

    /** 缓存的 getOnlinePlayers 反射句柄（选择返回 Collection 的重载；1.6/1.7 仅 Player[] 时取其唯一重载）。 */
    private static volatile Method onlinePlayersMethod;

    private BukkitCompat() {
    }

    /**
     * 获取全部在线玩家（跨 1.6/1.7/1.12 均可用）。
     *
     * @return 在线玩家 Object 视图列表（元素即服务端 {@code Player} 实例）；
     *         反射失败时回退 {@link Collections#emptyList()} 并告警
     */
    public static List<?> onlinePlayers() {
        try {
            Method m = resolveOnlinePlayersMethod();
            if (m == null) {
                return fallback();
            }
            Object result = m.invoke(null);
            if (result instanceof Collection) {
                return new ArrayList<>((Collection<?>) result);
            }
            if (result != null && result.getClass().isArray()) {
                // 1.6/1.7：Player[]（对象数组），统一转 List<Object> 视图
                return new ArrayList<>(Arrays.asList((Object[]) result));
            }
            return fallback();
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException e) {
            log.warn("[adapter] getOnlinePlayers 反射调用失败，回退空列表", e);
            return Collections.emptyList();
        }
    }

    private static Method resolveOnlinePlayersMethod() {
        Method cached = onlinePlayersMethod;
        if (cached != null) {
            return cached;
        }
        Method selected = null;
        Method any = null;
        Class<?> bukkitClass;
        try {
            bukkitClass = Class.forName("org.bukkit.Bukkit");
        } catch (ClassNotFoundException e) {
            log.warn("[adapter] 无法加载 org.bukkit.Bukkit（非 Bukkit 服务端？）");
            return null;
        }
        for (Method m : bukkitClass.getDeclaredMethods()) {
            if (!"getOnlinePlayers".equals(m.getName()) || m.getParameterCount() != 0) {
                continue;
            }
            if (any == null) {
                any = m;
            }
            if (Collection.class.isAssignableFrom(m.getReturnType())) {
                selected = m;
                break;
            }
        }
        if (selected == null) {
            selected = any;
        }
        if (selected != null) {
            selected.setAccessible(true);
            onlinePlayersMethod = selected;
        }
        return selected;
    }

    private static List<?> fallback() {
        // 极端回退：反射不可用时返回空列表（本模块零 Bukkit 编译期依赖，不可直接调用随版本变化的 API）
        return Collections.emptyList();
    }
}
