package com.github.cocosoys.mc.soyshttpovermc.permission.provider;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * PermissionsEx（PEX）权限提供者：老牌权限插件，支持离线用户权限数据查询。
 *
 * <p>PermissionsEx 使用独立的数据存储（文件 / SQL），不依赖玩家在线即可查询权限。
 * 通过 {@code PermissionsEx.getPermissionManager().getUser(playerName)} 可获取离线用户，
 * 再经 {@code PermissionUser.hasPermission(permission)} 判断权限。
 *
 * <p>本实现使用反射调用 PEX API，避免编译时硬依赖。PEX 有多个大版本（1.x / 2.x / 3.x），
 * API 略有差异，本实现优先尝试通用入口，失败则回退 Bukkit 原生。
 */
public class PermsExProvider implements PermissionProvider {

    private static final String PLUGIN_NAME = "PermissionsEx";
    private volatile Boolean availableCache = null;

    @Override
    public String name() {
        return "permsex";
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
            // 优先使用 PEX API
            Object user = getUserByName(player.getName());
            if (user != null) {
                Boolean result = checkPermissionViaUser(user, permission);
                if (result != null) return result;
            }
            // 回退 Bukkit 原生
            return player.hasPermission(permission);
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean hasOfflinePermission(String playerName, String permission) {
        if (!isAvailable() || playerName == null || playerName.isEmpty()) return false;
        try {
            Object user = getUserByName(playerName);
            if (user == null) return false;
            Boolean result = checkPermissionViaUser(user, permission);
            return result != null && result;
        } catch (Throwable t) {
            return false;
        }
    }

    // ===== 反射工具方法 =====

    /**
     * 获取 PEX PermissionUser 对象（支持在线和离线玩家）。
     * 尝试多种 PEX 版本的 API 入口。
     */
    private Object getUserByName(String playerName) {
        // 尝试 PEX 3.x / 2.x：PermissionsEx.getPermissionManager().getUser(name)
        try {
            Class<?> pexClass = Class.forName("ru.tehkode.permissions.bukkit.PermissionsEx");
            Method getManagerMethod = pexClass.getMethod("getPermissionManager");
            Object manager = getManagerMethod.invoke(null);
            if (manager != null) {
                Method getUserMethod = manager.getClass().getMethod("getUser", String.class);
                Object user = getUserMethod.invoke(manager, playerName);
                if (user != null) return user;
            }
        } catch (Throwable ignored) {
        }
        // 尝试 PEX 1.x：PermissionsEx.getUser(player)
        try {
            Class<?> pexClass = Class.forName("ru.tehkode.permissions.bukkit.PermissionsEx");
            Method getUserMethod = pexClass.getMethod("getUser", String.class);
            Object user = getUserMethod.invoke(null, playerName);
            if (user != null) return user;
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * 通过 PEX PermissionUser 判断权限。
     * 返回 Boolean（true/false），null 表示判断失败。
     */
    private Boolean checkPermissionViaUser(Object user, String permission) {
        // 尝试 hasPermission(String)
        try {
            Method method = user.getClass().getMethod("hasPermission", String.class);
            Object result = method.invoke(user, permission);
            if (result instanceof Boolean) return (Boolean) result;
        } catch (Throwable ignored) {
        }
        // 尝试 getPermissions() 后遍历（部分旧版本 PEX）
        try {
            Method method = user.getClass().getMethod("getPermissions");
            Object perms = method.invoke(user);
            if (perms instanceof java.util.Collection) {
                return ((java.util.Collection<?>) perms).contains(permission);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
