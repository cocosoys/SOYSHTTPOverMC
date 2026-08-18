package soys.soyshttpovermc.gateway.policy.auth.login;

/**
 * 网页登录模式策略（抽象基类，仿 {@code SecurityPolicy} 的可插拔模式）：
 * 控制"玩家是否必须在线才能登录网页 / 离线登录的许可与模式判定"。
 *
 * <p>接入点：{@code AuthLoginBridge} 在登录时调用 {@link #decideLogin} 决定签发的令牌是
 * 在线（ONLINE）还是离线（OFFLINE）模式；玩家不在线时是否允许离线登录由
 * {@link #allowOfflineLogin} 决定。默认实现 {@code DefaultLoginModePolicy} 允许离线登录
 * （在线→ONLINE、离线→OFFLINE）；服务端可通过替换该策略定制（如禁止离线登录、离线只读等）。
 */
public abstract class LoginModePolicy {

    /** 策略唯一标识（日志/事件用）。 */
    public abstract String name();

    /**
     * 决定某玩家登录网页时应使用的模式：
     * 在线（{@code Bukkit.getPlayerExact(player) != null}）→ ONLINE；不在线 → OFFLINE。
     */
    public abstract LoginMode decideLogin(String player);

    /** 玩家不在线时是否允许以离线模式登录网页（默认允许；false=必须游戏内在线才能登录）。 */
    public boolean allowOfflineLogin(String player) {
        return true;
    }

    /** 便捷判定：玩家是否在线（默认按 Bukkit 在线判定，子类可覆盖）。 */
    protected boolean isOnline(String player) {
        return org.bukkit.Bukkit.getPlayerExact(player) != null;
    }
}
