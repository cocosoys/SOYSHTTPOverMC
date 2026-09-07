package com.github.cocosoys.mc.soyshttpovermc.adapter.compat;

import lombok.CustomLog;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * 玩家身份键兼容工具（零 Bukkit 编译期依赖，全部反射）。
 *
 * <p>身份键的语义：跨版本稳定的玩家唯一标识。</p>
 * <ul>
 *   <li>1.6.4：无 UUID API（{@code Player}/{@code OfflinePlayer} 均无 {@code getUniqueId}），身份键 = 玩家名；
 *       （离线服玩家名即身份）。</li>
 *   <li>1.7.10+：{@code OfflinePlayer#getUniqueId()} 存在，优先取 UUID，缺失时回退玩家名。</li>
 * </ul>
 *
 * <p>本模块不 import 任何 org.bukkit 类：{@code Player} / {@code OfflinePlayer} 一律以
 * {@code Object} 视图传入，UUID 与玩家名均经反射获取，禁止编译期直接调用。</p>
 */
@CustomLog
public final class PlayerIdentity {

    /** 缓存的 getUniqueId 反射句柄（取 OfflinePlayer 声明处，Player 为子接口）。 */
    private static volatile Method uniqueIdMethod;

    private PlayerIdentity() {
    }

    /**
     * 玩家身份键：1.7.10+ 返回 UUID 字符串；1.6.4 或反射失败返回玩家名。
     *
     * @param player 在线玩家（服务端 {@code Player} / {@code OfflinePlayer} 实例的 Object 视图）
     * @return 身份键（永不为 null；玩家名为空时返回空串）
     */
    public static String key(Object player) {
        if (player == null) {
            return "";
        }
        UUID uuid = uuidOf(player);
        if (uuid != null) {
            return uuid.toString();
        }
        return nameOf(player);
    }

    /**
     * 玩家名（低版本安全，反射调用 {@code Player#getName()} / {@code OfflinePlayer#getName()}）。
     *
     * @param player 玩家实例的 Object 视图（可为 null）
     * @return 玩家名；null / 反射失败返回空串
     */
    public static String nameOf(Object player) {
        if (player == null) {
            return "";
        }
        try {
            Object name = player.getClass().getMethod("getName").invoke(player);
            return name == null ? "" : name.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    private static UUID uuidOf(Object player) {
        try {
            Method m = resolveUniqueIdMethod();
            if (m == null) {
                return null;
            }
            Object v = m.invoke(player);
            return v instanceof UUID ? (UUID) v : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static Method resolveUniqueIdMethod() {
        Method cached = uniqueIdMethod;
        if (cached != null) {
            return cached;
        }
        Method found = null;
        try {
            Class<?> offlinePlayerClass = Class.forName("org.bukkit.OfflinePlayer");
            found = offlinePlayerClass.getMethod("getUniqueId");
            found.setAccessible(true);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            log.debug("[adapter] 当前版本无 OfflinePlayer#getUniqueId（1.6.4），身份键降级为玩家名");
        }
        uniqueIdMethod = found;
        return found;
    }
}
