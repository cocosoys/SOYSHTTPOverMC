package soys.soyshttpovermc.auth;

import soys.soyshttpovermc.annotations.PermissionService;
import soys.soyshttpovermc.gateway.GatewayFilter;
import soys.soyshttpovermc.gateway.policy.auth.issuer.CredentialIssuer;
import soys.soyshttpovermc.gateway.policy.auth.issuer.CredentialPresentation;
import soys.soyshttpovermc.gateway.policy.auth.issuer.SessionTokenIssuer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * 玩家权限映射服务（{@link PermissionService} 实现）：把会话令牌（session-token 颁发器签发）
 * 映射为玩家，再判定该玩家在游戏内是否拥有 {@code @ApiPermission} 声明的 Bukkit 权限。
 *
 * <p>设计要点：
 * <ul>
 *   <li>令牌与玩家绑定（AuthMe 登录成功时为玩家名签发），玩家拥有的权限令牌也拥有
 *       （如玩家拥有 {@code soyshttp:api:status}，则其令牌拥有）；</li>
 *   <li>非会话令牌（如 /soyshttp key 下发的静态 X-API-Key）视为完全信任（返回 true）；</li>
 *   <li>无启用的会话颁发器时回退开放（兼容未启用 session-token 的旧部署）；</li>
 *   <li>玩家在线查实时权限；离线时仅 op 经离线权限放行（Spigot 1.12.2 的 OfflinePlayer 无 hasPermission API）。</li>
 * </ul>
 */
public class PlayerPermissionService implements PermissionService {

    private final GatewayFilter gateway;

    public PlayerPermissionService(GatewayFilter gateway) {
        this.gateway = gateway;
    }

    @Override
    public boolean hasPermission(CredentialPresentation credential, String permission) {
        if (credential == null) return false;
        SessionTokenIssuer issuer = findSessionIssuer();
        if (issuer == null) return true; // 无启用的会话颁发器 → 回退开放
        String player = issuer.subjectOf(credential);
        if (player == null) return true; // 非玩家令牌（如静态 key）→ 完全信任
        Player online = Bukkit.getPlayerExact(player);
        if (online != null) return online.hasPermission(permission);
        // 玩家离线：Spigot 1.12.2 的 OfflinePlayer 无 hasPermission API，仅 op 可经离线权限放行（保守默认）
        return Bukkit.getOfflinePlayer(player).isOp();
    }

    /** 从网关已启用的颁发器中找到会话令牌颁发器（用于解析令牌→玩家）。 */
    private SessionTokenIssuer findSessionIssuer() {
        if (gateway == null) return null;
        for (CredentialIssuer i : gateway.getIssuers()) {
            if (i instanceof SessionTokenIssuer) return (SessionTokenIssuer) i;
        }
        return null;
    }
}
