package soys.soyshttpovermc.gateway.policy.login;

/**
 * 网页登录模式：决定会话令牌的身份语义。
 * <ul>
 *   <li>{@link #ONLINE}：玩家在游戏内在线（或曾在线且令牌已升级），令牌完整镜像玩家游戏内 Bukkit 权限；</li>
 *   <li>{@link #OFFLINE}：玩家不在线，仅以离线模式登录网页，令牌打上"离线模式登录"标签，
 *       权限按离线处理（op / 静态权限），玩家进游戏登录后自动升级为 ONLINE。</li>
 * </ul>
 */
public enum LoginMode {
    ONLINE,
    OFFLINE
}
