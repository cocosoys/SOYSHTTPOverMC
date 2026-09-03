package com.github.cocosoys.mc.soyshttpovermc.adapter.compat;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * 玩家身份键兼容工具。
 *
 * <p>身份键的语义：跨版本稳定的玩家唯一标识。</p>
 * <ul>
 *   <li>1.6.4：无 UUID API（{@code Player}/{@code OfflinePlayer} 均无 {@code getUniqueId}），身份键 = 玩家名；
 *       （离线服玩家名即身份）。</li>
 *   <li>1.7.10+：{@code OfflinePlayer#getUniqueId()} 存在，优先取 UUID，缺失时回退玩家名。</li>
 * </ul>
 *
 * <p>编译基线为 1.6.4，UUID 一律反射获取，禁止编译期直接调用。</p>
 */
public final class PlayerIdentity {

    private static final Logger LOG = Logger.getLogger(PlayerIdentity.class.getName());

    /** 缓存的 getUniqueId 反射句柄（取 OfflinePlayer 声明处，Player 为子接口）。 */
    private static volatile Method uniqueIdMethod;

    private PlayerIdentity() {
    }

    /**
     * 玩家身份键：1.7.10+ 返回 UUID 字符串；1.6.4 或反射失败返回玩家名。
     *
     * @param player 在线玩家
     * @return 身份键（永不为 null；玩家名为空时返回空串）
     */
    public static String key(Player player) {
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
     * 离线玩家身份键（语义同上）。
     */
    public static String key(OfflinePlayer player) {
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
     * 玩家名（低版本安全）。
     */
    public static String nameOf(Player player) {
        return player == null ? "" : player.getName();
    }

    /**
     * 离线玩家名（低版本安全）。
     */
    public static String nameOf(OfflinePlayer player) {
        return player == null ? "" : player.getName();
    }

    private static UUID uuidOf(Player player) {
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

    private static UUID uuidOf(OfflinePlayer player) {
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
            found = OfflinePlayer.class.getMethod("getUniqueId");
            found.setAccessible(true);
        } catch (NoSuchMethodException e) {
            LOG.fine("[adapter] 当前版本无 OfflinePlayer#getUniqueId（1.6.4），身份键降级为玩家名");
        }
        uniqueIdMethod = found;
        return found;
    }
}
