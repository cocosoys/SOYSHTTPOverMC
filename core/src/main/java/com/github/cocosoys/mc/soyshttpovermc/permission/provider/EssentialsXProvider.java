package com.github.cocosoys.mc.soyshttpovermc.permission.provider;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * EssentialsX 权限提供者：EssentialsX 是 Essentials 的活跃维护分支，功能与 Essentials 一致，
 * 同样不存储权限数据，权限判断基于 Bukkit 原生权限系统。
 *
 * <p>EssentialsX 与 Essentials 的区别主要在于 bug 修复、新版本兼容和部分功能增强，
 * 权限判断逻辑完全相同（都走 {@code player.hasPermission()}）。
 *
 * <p>本提供者与 {@link EssentialsProvider} 逻辑一致，仅插件检测名称不同。
 * 两者不会同时启用（EssentialsX 安装时会替换 Essentials），组合服务会自动选择可用的那个。
 *
 * @see EssentialsProvider
 */
public class EssentialsXProvider implements PermissionProvider {

    private static final String PLUGIN_NAME = "EssentialsX";
    private volatile Boolean availableCache = null;

    @Override
    public String name() {
        return "essentialx";
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
            // EssentialsX 权限判断走 Bukkit 原生（同 Essentials）
            return player.hasPermission(permission);
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean hasOfflinePermission(String playerName, String permission) {
        // EssentialsX 不支持离线权限查询
        return false;
    }
}
