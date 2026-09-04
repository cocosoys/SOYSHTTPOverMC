package com.github.cocosoys.mc.soyshttpovermc.util;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.UUID;

/**
 * 玩家身份工具：离线 UUID 获取 / 身份标识归一。
 *
 * <p>设计原则：<b>不重复造轮子、不写死算法</b>。</p>
 * <ul>
 *   <li>玩家名 → 离线 UUID：复用 MC 自带 {@link Bukkit#getOfflinePlayer(String)} →
 *       {@link org.bukkit.OfflinePlayer#getUniqueId()}，算法由服务器版本决定（该算法在部分高版本有变动，
 *       如对名字做 lowercase 归一），插件不做任何硬编码复制，天然跟随版本；</li>
 *   <li>UUID 解析：复用 JDK 标准 {@link UUID#fromString(String)}（宽容封装带/不带横线），无自造算法；</li>
 *   <li>身份归一 {@link #keyOf(String)}：MC 无现成，属业务判定逻辑——已识别为 UUID 则归一格式，
 *       否则视为玩家名取 MC 自带离线 UUID。</li>
 * </ul>
 */
public final class UuidUtil {

    private UuidUtil() {
    }

    /**
     * 由玩家名取 MC 自带离线 UUID（复用 {@link Bukkit#getOfflinePlayer(String)}，算法跟随服务器版本）。
     * 空白输入返回 null。
     */
    public static UUID uuidOf(String name) {
        if (name == null || name.trim().isEmpty()) return null;
        OfflinePlayer player=Bukkit.getOfflinePlayer(name);
        if(player==null) return null;
        return player.getUniqueId();
    }

    /**
     * 由玩家名UUID取 MC 玩家名称（复用 {@link Bukkit#getOfflinePlayer(UUID)}，算法跟随服务器版本）。
     * 空白输入返回 null。
     */
    public static String nameOf(UUID uuid) {
        if (uuid == null) return null;
        OfflinePlayer player=Bukkit.getOfflinePlayer(uuid);
        if(player==null) return null;
        return player.getName();
    }


    /**
     * 宽容解析 UUID：基于 JDK 标准 {@link UUID#fromString(String)}；带横线 / 无横线均可；非法返回 null。
     */
    public static UUID parseUuid(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        try {
            if (t.length() == 32 && t.indexOf('-') < 0) {
                // 无横线 32 位：补回标准 8-4-4-4-12 横线位置后再交给 JDK 解析
                return UUID.fromString(t.replaceFirst(
                        "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
            }
            return UUID.fromString(t);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 是否为合法 UUID 字符串（带/不带横线）。
     */
    public static boolean isUuid(String s) {
        return parseUuid(s) != null;
    }

    /**
     * 身份标识 → 主键 uuid 字符串（标准小写带横线）。
     * 已识别为 UUID（带/不带横线）→ 原样归一；否则视为玩家名 → 取 MC 自带离线 UUID。
     *
     * @return 标准 uuid 字符串；空白输入返回空串
     */
    public static String keyOf(String identity) {
        if (identity == null || identity.trim().isEmpty()) return "";
        String s = identity.trim();
        UUID u = parseUuid(s);
        if (u != null) return u.toString();
        UUID offline = uuidOf(s);
        return offline == null ? "" : offline.toString();
    }
}
