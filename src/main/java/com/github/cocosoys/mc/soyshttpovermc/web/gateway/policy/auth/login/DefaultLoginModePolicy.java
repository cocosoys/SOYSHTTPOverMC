package com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.login;
import com.github.cocosoys.mc.soyshttpovermc.enums.LoginMode;

/**
 * 默认登录模式策略：
 * <ul>
 *   <li>玩家游戏内在线 → {@link LoginMode#ONLINE}（令牌完整镜像玩家权限）；</li>
 *   <li>玩家不在线 → 允许以 {@link LoginMode#OFFLINE} 离线模式登录网页（离线专属 cookie，
 *       打"离线模式登录"标签；玩家进游戏登录后由 LoginEvent 自动升级为 ONLINE）。</li>
 * </ul>
 */
public class DefaultLoginModePolicy extends LoginModePolicy {

    @Override
    public String name() {
        return "default";
    }

    @Override
    public LoginMode decideLogin(String player) {
        return isOnline(player) ? LoginMode.ONLINE : LoginMode.OFFLINE;
    }

    @Override
    public boolean allowOfflineLogin(String player) {
        return true; // 允许离线登录（离线 cookie 权限按离线处理）
    }
}
