package com.github.cocosoys.mc.soyshttpovermc.permission.provider;

import com.github.cocosoys.mc.soyshttpovermc.permission.local.LocalPermissionStore;
import org.bukkit.entity.Player;

/**
 * 本地内置权限提供者（name={@code local}，配套 {@code permission.offline-fallback: local}）。
 *
 * <p>读取插件内置的 4 张本地权限表（组 / 用户 / 用户-组 / 权限），在线与离线玩家统一走
 * {@link LocalPermissionStore#check(String, String)}。加入组合链后，在线 / 离线权限判断都会查本地表，
 * 与其它权限插件提供者按"或"逻辑组合。</p>
 *
 * <p>内置提供者：{@link #isAvailable()} 恒为 true（不依赖外部插件安装）。</p>
 */
public class LocalPermissionProvider implements PermissionProvider {

    private final LocalPermissionStore store;

    public LocalPermissionProvider(LocalPermissionStore store) {
        this.store = store;
    }

    @Override
    public String name() {
        return "local";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public boolean hasPermission(Player player, String permission) {
        if (player == null || store == null) return false;
        try {
            return store.check(player.getName(), permission);
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean hasOfflinePermission(String playerName, String permission) {
        if (store == null || playerName == null) return false;
        try {
            return store.check(playerName, permission);
        } catch (Throwable t) {
            return false;
        }
    }
}
