package com.github.cocosoys.mc.soyshttpovermc.adapter.compat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 版本中立 Bukkit 兼容工具：抹平跨版本 API 差异。
 *
 * <p><b>核心原则</b>：凡在目标版本段（1.6.4 ~ 1.12.2）签名不一致的方法，一律反射调用，
 * 禁止编译期直接绑定，避免低版本服务器运行时 {@code NoSuchMethodError} / {@code NoClassDefFoundError}。</p>
 *
 * <p>已覆盖差异：</p>
 * <ul>
 *   <li>{@link #onlinePlayers()}：1.8+ 返回 {@code Collection}，1.6/1.7 返回 {@code Player[]}；反射优先取 Collection 重载。</li>
 * </ul>
 */
public final class BukkitCompat {

    private static final Logger LOG = Logger.getLogger(BukkitCompat.class.getName());

    /** 缓存的 getOnlinePlayers 反射句柄（选择返回 Collection 的重载；1.6/1.7 仅 Player[] 时取其唯一重载）。 */
    private static volatile Method onlinePlayersMethod;

    private BukkitCompat() {
    }

    /**
     * 获取全部在线玩家（跨 1.6/1.7/1.12 均可用）。
     *
     * @return 在线玩家列表；反射失败时回退 {@link Collections#emptyList()} 并告警
     */
    public static List<Player> onlinePlayers() {
        try {
            Method m = resolveOnlinePlayersMethod();
            if (m == null) {
                return fallback();
            }
            Object result = m.invoke(null);
            if (result instanceof Collection) {
                List<Player> list = new ArrayList<>();
                for (Object o : (Collection<?>) result) {
                    if (o instanceof Player) {
                        list.add((Player) o);
                    }
                }
                return list;
            }
            if (result instanceof Player[]) {
                return new ArrayList<>(Arrays.asList((Player[]) result));
            }
            return fallback();
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException e) {
            LOG.log(Level.WARNING, "[adapter] getOnlinePlayers 反射调用失败，回退空列表", e);
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
        for (Method m : Bukkit.class.getDeclaredMethods()) {
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

    private static List<Player> fallback() {
        // 极端回退：反射不可用时返回空列表（编译基线为 1.6.4，不可直接调用会随版本变化的 Bukkit API）
        return Collections.emptyList();
    }
}
