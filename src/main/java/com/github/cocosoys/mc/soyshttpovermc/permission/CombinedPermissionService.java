package com.github.cocosoys.mc.soyshttpovermc.permission;

import com.github.cocosoys.mc.soyshttpovermc.permission.provider.PermissionProvider;
import com.github.cocosoys.mc.soyshttpovermc.permission.provider.ProviderRegistry;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.GatewayFilter;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.issuer.CredentialPresentation;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * 组合权限服务：继承 {@link PlayerPermissionService}，在父类（Bukkit 原生权限 + 离线 OP 检查）
 * 判断失败后，继续遍历所有已配置的权限插件提供者（LuckPerms / PermissionsEx / Essentials / EssentialsX），
 * 任一返回 true 则权限认证通过（"或"逻辑）。
 *
 * <p>判断顺序：
 * <ol>
 *   <li>{@code super.hasPermission()} — Bukkit 原生权限（在线）+ 离线 OP 检查；
 *       返回 true 则直接通过，不再向下执行（性能优化，避免不必要的插件 API 调用）。</li>
 *   <li>遍历 {@link ProviderRegistry#getActiveProviders()} 中的所有权限提供者；
 *       在线玩家调用 {@code hasPermission(player, permission)}，
 *       离线玩家调用 {@code hasOfflinePermission(playerName, permission)}；
 *       任一返回 true 则通过。</li>
 *   <li>全部返回 false → 拒绝（403）。</li>
 * </ol>
 *
 * <p>离线玩家降级策略（config.yml 的 permission.offline-fallback）：
 * <ul>
 *   <li>op-only（默认）：仅 OP 玩家返回 true（从 ops.json 读取）；</li>
 *   <li>false：所有离线玩家返回 false（最严格）。</li>
 * </ul>
 *
 * <p>配置示例：
 * <pre>
 * permission:
 *   # 权限判断组合：留空=所有已安装的权限插件自动加入
 *   # 支持：luckperms, essentials, essentialx, permsex
 *   providers: []
 *   # 离线玩家降级策略：op-only / false
 *   offline-fallback: op-only
 * </pre>
 */
public class CombinedPermissionService extends PlayerPermissionService {

    private final JavaPlugin plugin;
    private final ProviderRegistry providerRegistry;

    public CombinedPermissionService(JavaPlugin plugin, GatewayFilter gateway) {
        super(gateway);
        this.plugin = plugin;
        this.providerRegistry = new ProviderRegistry(plugin);
        this.providerRegistry.reload();
    }

    @Override
    public boolean hasPermission(CredentialPresentation credential, String permission) {
        // 1. 先调用父类判断（Bukkit 原生 + 离线 OP 检查）
        //    返回 true 则直接通过，不再向下执行（性能优化）
        if (super.hasPermission(credential, permission)) {
            return true;
        }

        // 2. 解析凭证绑定的玩家名
        String playerName = subjectOf(credential);
        if (playerName == null || playerName.isEmpty()) {
            return false; // 无法解析玩家，拒绝
        }

        // 3. 遍历所有活跃的权限提供者，任一返回 true 则通过
        List<PermissionProvider> providers = providerRegistry.getActiveProviders();
        if (providers.isEmpty()) {
            return false; // 无活跃提供者，父类已判断过，直接拒绝
        }

        Player online = Bukkit.getPlayerExact(playerName);
        if (online != null) {
            // 在线玩家：调用各提供者的 hasPermission
            for (PermissionProvider provider : providers) {
                try {
                    if (provider.hasPermission(online, permission)) {
                        return true;
                    }
                } catch (Throwable t) {
                    // 单个提供者故障不影响整体，继续下一个
                }
            }
        } else {
            // 离线玩家：调用各提供者的 hasOfflinePermission
            for (PermissionProvider provider : providers) {
                try {
                    if (provider.hasOfflinePermission(playerName, permission)) {
                        return true;
                    }
                } catch (Throwable t) {
                    // 单个提供者故障不影响整体
                }
            }
            // 离线降级策略（父类已做过 OP 检查，这里按配置再确认一次）
            // 注意：父类的离线 OP 检查已经在 super.hasPermission() 中执行过了，
            // 这里不需要重复，直接返回 false 即可
        }

        return false;
    }

    /**
     * 离线玩家权限判断（供外部直接调用，如操作队列、API 访问事件等）。
     * 不依赖凭证，直接按玩家名判断。
     *
     * @param playerName 玩家名
     * @param permission 权限节点
     * @return true=拥有该权限
     */
    public boolean hasOfflinePermission(String playerName, String permission) {
        if (playerName == null || playerName.isEmpty()) return false;

        // 1. 离线 OP 降级检查
        String fallback = plugin.getConfig().getString("permission.offline-fallback", "op-only");
        if ("op-only".equalsIgnoreCase(fallback)) {
            try {
                if (Bukkit.getOfflinePlayer(playerName).isOp()) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }

        // 2. 遍历所有活跃提供者的离线权限判断
        List<PermissionProvider> providers = providerRegistry.getActiveProviders();
        for (PermissionProvider provider : providers) {
            try {
                if (provider.hasOfflinePermission(playerName, permission)) {
                    return true;
                }
            } catch (Throwable t) {
                // 单个提供者故障不影响整体
            }
        }
        return false;
    }

    /**
     * 重新加载权限提供者组合（/soyshttp reload 时调用）。
     */
    public void reloadProviders() {
        providerRegistry.reload();
    }

    /**
     * 获取提供者注册表（供调试命令展示用）。
     */
    public ProviderRegistry getProviderRegistry() {
        return providerRegistry;
    }
}
