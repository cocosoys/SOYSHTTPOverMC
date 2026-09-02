package com.github.cocosoys.mc.soyshttpovermc.permission.provider;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Essentials 权限提供者：Essentials 本身不存储权限数据，其权限判断基于 Bukkit 原生权限系统。
 *
 * <p>Essentials 是功能插件（家、传送、经济等），不是权限管理插件。它的权限判断
 * 最终都走 {@code player.hasPermission()}，由底层权限插件（LuckPerms / PermissionsEx 等）
 * 或 Bukkit 自带的权限文件（permissions.yml / ops.json）处理。
 *
 * <p>因此本提供者：
 * <ul>
 *   <li>在线玩家：直接调用 {@code player.hasPermission()}（与 Bukkit 原生一致）；</li>
 *   <li>离线玩家：Essentials 不支持离线权限查询，返回 false（由组合服务的 OP 降级兜底）。</li>
 * </ul>
 *
 * <p>保留此提供者的意义：当用户显式配置只使用 Essentials 时，确保权限判断走 Essentials
 * 兼容的 Bukkit 原生路径，且与其他提供者（如 LuckPerms）的"或"逻辑组合时，
 * 即使 LuckPerms 判断失败，Bukkit 原生（如 ops.json）仍可作为兜底。
 */
public class EssentialsProvider implements PermissionProvider {

    private static final String PLUGIN_NAME = "Essentials";
    private volatile Boolean availableCache = null;

    @Override
    public String name() {
        return "essentials";
    }

    @Override
    public boolean isAvailable() {
        if (availableCache != null) return availableCache;
        try {
            Plugin plugin = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
            availableCache = plugin != null && plugin.isEnabled();
        } catch (Throwable t) {
            availableCache = false;
        }
        return availableCache;
    }

    @Override
    public boolean hasPermission(Player player, String permission) {
        if (!isAvailable() || player == null) return false;
        try {
            // Essentials 权限判断走 Bukkit 原生（Essentials 不存储权限，由底层权限系统处理）
            return player.hasPermission(permission);
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean hasOfflinePermission(String playerName, String permission) {
        // Essentials 不支持离线权限查询（无独立权限存储）
        return false;
    }
}
