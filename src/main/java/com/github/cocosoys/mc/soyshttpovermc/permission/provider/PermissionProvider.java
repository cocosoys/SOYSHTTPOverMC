package com.github.cocosoys.mc.soyshttpovermc.permission.provider;

import org.bukkit.entity.Player;

/**
 * 权限提供者接口：抽象不同权限插件（LuckPerms / Essentials / EssentialsX / PermissionsEx）
 * 的权限判断能力，支持在线玩家和离线玩家的权限查询。
 *
 * <p>每个提供者对应一个权限插件，{@link #isAvailable()} 检测插件是否存在且启用。
 * 组合权限服务 {@code CombinedPermissionService} 会遍历所有可用提供者，
 * 任一返回 true 则权限认证通过（"或"逻辑）。
 *
 * <p>实现要点：
 * <ul>
 *   <li>在线玩家：优先使用插件自身 API，无 API 时回退 {@code player.hasPermission()}；</li>
 *   <li>离线玩家：仅 LuckPerms / PermissionsEx 等支持离线数据查询的插件返回有效结果，
 *       不支持的插件返回 false（由组合服务的降级策略兜底）；</li>
 *   <li>所有方法不得抛出异常，内部异常应捕获并返回 false（避免单个提供者故障影响整体）。</li>
 * </ul>
 */
public interface PermissionProvider {

    /**
     * 提供者唯一名称（对应 config.yml 中的 providers 配置值）。
     */
    String name();

    /**
     * 对应权限插件是否存在且已启用。
     */
    boolean isAvailable();

    /**
     * 在线玩家权限判断。
     *
     * @param player     在线玩家（非空）
     * @param permission 权限节点
     * @return true=拥有该权限；false=无权限或判断失败
     */
    boolean hasPermission(Player player, String permission);

    /**
     * 离线玩家权限判断（核心新增能力）。
     *
     * <p>仅支持离线数据查询的权限插件（如 LuckPerms / PermissionsEx）返回有效结果；
     * 不支持的插件应直接返回 false，由组合服务的降级策略（如 OP 检查）兜底。
     *
     * @param playerName 玩家名（区分大小写，与登录名一致）
     * @param permission 权限节点
     * @return true=拥有该权限；false=无权限/不支持离线/判断失败
     */
    boolean hasOfflinePermission(String playerName, String permission);
}
