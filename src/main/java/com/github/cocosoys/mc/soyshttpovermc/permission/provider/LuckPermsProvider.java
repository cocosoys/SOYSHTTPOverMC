package com.github.cocosoys.mc.soyshttpovermc.permission.provider;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * LuckPerms 权限提供者：使用 LuckPerms API 进行权限判断，支持离线玩家权限查询。
 *
 * <p>LuckPerms 是目前最主流的权限插件，数据存储在独立数据库中，不依赖玩家在线。
 * 通过 {@code LuckPermsProvider.get().getUserManager().loadUser(name/uuid)} 可异步加载
 * 离线用户数据，再经 {@code CachedData.getPermissionData().checkPermission()} 判断权限。
 *
 * <p>本实现使用反射调用 LuckPerms API，避免编译时硬依赖（插件未安装时不影响编译和运行）。
 */
public class LuckPermsProvider implements PermissionProvider {

    private static final String PLUGIN_NAME = "LuckPerms";
    private volatile Boolean availableCache = null;

    @Override
    public String name() {
        return "luckperms";
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
            // 优先使用 LuckPerms API（支持组继承、上下文等完整权限模型）
            Object lp = getLuckPermsInstance();
            if (lp != null) {
                Object user = getUserByPlayer(lp, player);
                if (user != null) {
                    return checkPermissionViaUser(user, permission);
                }
            }
            // 回退 Bukkit 原生（LuckPerms 已注入 Bukkit 权限系统，通常等价）
            return player.hasPermission(permission);
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean hasOfflinePermission(String playerName, String permission) {
        if (!isAvailable() || playerName == null || playerName.isEmpty()) return false;
        try {
            Object lp = getLuckPermsInstance();
            if (lp == null) return false;
            // 异步加载离线用户：userManager.loadUser(playerName).join()
            Object userManager = invokeMethod(lp, "getUserManager");
            if (userManager == null) return false;
            Object future = invokeMethod(userManager, "loadUser", new Class<?>[]{String.class}, playerName);
            if (future == null) return false;
            Object user = invokeMethod(future, "join");
            if (user == null) return false;
            return checkPermissionViaUser(user, permission);
        } catch (Throwable t) {
            return false;
        }
    }

    // ===== 反射工具方法 =====

    /** 获取 LuckPerms 单例实例（LuckPermsProvider.get()）。 */
    private Object getLuckPermsInstance() {
        try {
            Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
            Method getMethod = providerClass.getMethod("get");
            return getMethod.invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 从在线玩家获取 LuckPerms User 对象。 */
    private Object getUserByPlayer(Object lp, Player player) {
        try {
            Object userManager = invokeMethod(lp, "getUserManager");
            if (userManager == null) return null;
            // 优先按 UUID 获取（在线玩家）
            UUID uuid = player.getUniqueId();
            Object user = invokeMethod(userManager, "getUser", new Class<?>[]{UUID.class}, uuid);
            if (user != null) return user;
            // 回退按玩家名获取
            return invokeMethod(userManager, "getUser", new Class<?>[]{String.class}, player.getName());
        } catch (Throwable t) {
            return null;
        }
    }

    /** 通过 LuckPerms User 对象判断权限。 */
    private boolean checkPermissionViaUser(Object user, String permission) {
        try {
            // user.getCachedData().getPermissionData().checkPermission(permission).asBoolean()
            Object cachedData = invokeMethod(user, "getCachedData");
            if (cachedData == null) return false;
            Object permissionData = invokeMethod(cachedData, "getPermissionData");
            if (permissionData == null) return false;
            Object tristate = invokeMethod(permissionData, "checkPermission",
                    new Class<?>[]{String.class}, permission);
            if (tristate == null) return false;
            // Tristate.TRUE / FALSE / UNDEFINED
            String name = invokeMethod(tristate, "name").toString();
            return "TRUE".equals(name);
        } catch (Throwable t) {
            return false;
        }
    }

    /** 反射调用无参方法。 */
    private Object invokeMethod(Object target, String methodName) {
        return invokeMethod(target, methodName, new Class<?>[0], new Object[0]);
    }

    /** 反射调用单参方法。 */
    private Object invokeMethod(Object target, String methodName, Class<?>[] paramTypes, Object... args) {
        try {
            Method method = target.getClass().getMethod(methodName, paramTypes);
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (Throwable t) {
            return null;
        }
    }
}
