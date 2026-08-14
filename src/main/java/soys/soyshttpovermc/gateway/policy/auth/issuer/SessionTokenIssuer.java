package soys.soyshttpovermc.gateway.policy.auth.issuer;

import org.bukkit.configuration.ConfigurationSection;
import soys.soyshttpovermc.gateway.policy.auth.util.AuthUtils;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话令牌颁发器（{@link CredentialIssuer} 参考实现，演示接入模式）。
 * <ul>
 *   <li>{@link #issue}：为指定主体生成随机会话令牌并内存登记（绑定主体 + 过期时间）；
 *       同一 token 可作 X-API-Key / Authorization Bearer / Cookie 三种形态下发；</li>
 *   <li>{@link #validate}：校验 Bearer / X-API-Key / Cookie(soys_session) 携带的令牌是否有效未过期。</li>
 * </ul>
 * 服务重启后内存令牌全部失效（内存态实现）；未来登录插件可按同样的模式实现自己的持久化颁发器。
 */
public class SessionTokenIssuer extends CredentialIssuer {

    private String cookieName = "soys_session";
    private long ttlMillis = 24L * 3600 * 1000;
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();

    @Override
    public String name() {
        return "session-token";
    }

    @Override
    public void reload(ConfigurationSection cfg) {
        super.reload(cfg);
        if (cfg == null) return;
        cookieName = cfg.getString("cookie-name", "soys_session");
        ttlMillis = Math.max(1000L, cfg.getLong("ttl-seconds", 86400) * 1000L);
        sessions.clear();
    }

    @Override
    public IssuedCredential issue(String subject) {
        return issue(subject, soys.soyshttpovermc.gateway.policy.login.LoginMode.ONLINE);
    }

    /** 按指定登录模式签发会话令牌（offline=离线模式专属 cookie，online=正常在线令牌）。 */
    public IssuedCredential issue(String subject, soys.soyshttpovermc.gateway.policy.login.LoginMode mode) {
        String token = AuthUtils.generateToken("st_", 24);
        sessions.put(token, new Session(subject, System.currentTimeMillis() + ttlMillis,
                mode == null ? soys.soyshttpovermc.gateway.policy.login.LoginMode.ONLINE : mode));
        return IssuedCredential.ofToken(token, cookieName);
    }

    @Override
    public boolean validate(CredentialPresentation p) {
        if (p == null) return false;
        String token = tokenOf(p);
        if (token == null) return false;
        Session s = sessions.get(token);
        if (s == null) return false;
        if (s.expiresAt < System.currentTimeMillis()) {
            sessions.remove(token);
            return false;
        }
        return true;
    }

    /** 从凭证表示提取本颁发器可识别的令牌（Bearer / X-API-Key / Cookie(soys_session) 任一），供校验与权限映射复用。 */
    protected String tokenOf(CredentialPresentation p) {
        if (p == null) return null;
        String token = p.getBearer();
        if (token == null || token.isEmpty()) token = p.getApiKey();
        if (token == null || token.isEmpty()) token = p.getCookie(cookieName);
        return (token == null || token.isEmpty()) ? null : token;
    }

    @Override
    public String subjectOf(CredentialPresentation p) {
        String token = tokenOf(p);
        if (token == null) return null;
        Session s = sessions.get(token);
        if (s == null) return null;
        if (s.expiresAt < System.currentTimeMillis()) {
            sessions.remove(token);
            return null;
        }
        return s.subject;
    }

    /** 查询请求凭证对应会话的登录模式（无效/过期返回 null）。 */
    public soys.soyshttpovermc.gateway.policy.login.LoginMode modeOf(CredentialPresentation p) {
        String token = tokenOf(p);
        if (token == null) return null;
        Session s = sessions.get(token);
        if (s == null || s.expiresAt < System.currentTimeMillis()) return null;
        return s.mode;
    }

    /**
     * 玩家进游戏登录成功后，把其名下全部现存会话令牌升级为在线模式（"补全/替换为在线登录的正常 cookie"）：
     * 离线模式登录的浏览器 cookie 值不变、语义自动升级为 ONLINE（权限完整、标签变在线）。
     * 返回被升级的令牌数（0=该玩家尚无令牌，此时由调用方正常签发新令牌）。
     */
    public int upgradePlayerToOnline(String player) {
        if (player == null) return 0;
        int n = 0;
        for (Session s : sessions.values()) {
            if (player.equalsIgnoreCase(s.subject) && s.mode != soys.soyshttpovermc.gateway.policy.login.LoginMode.ONLINE) {
                s.mode = soys.soyshttpovermc.gateway.policy.login.LoginMode.ONLINE;
                n++;
            }
        }
        return n;
    }

    /** 会话令牌 TTL（秒），供 Set-Cookie 的 Max-Age 使用。 */
    public long getTtlSeconds() {
        return ttlMillis / 1000L;
    }

    /** Cookie 名称（默认 soys_session），供 Set-Cookie 头构造使用。 */
    public String getCookieName() {
        return cookieName;
    }

    /** 按令牌字符串撤销会话（退出登录用）；撤销成功返回 true。 */
    public boolean revoke(String token) {
        return token != null && sessions.remove(token) != null;
    }

    /** 按请求携带的凭证撤销会话（退出登录用）；提取不到令牌或不存在返回 false。 */
    public boolean revoke(CredentialPresentation p) {
        String token = tokenOf(p);
        return token != null && sessions.remove(token) != null;
    }

    private static final class Session {
        final String subject;
        final long expiresAt;
        /** 登录模式：OFFLINE=离线模式登录（待升级），ONLINE=在线正常登录（玩家进游戏后自动升级）。 */
        volatile soys.soyshttpovermc.gateway.policy.login.LoginMode mode;

        Session(String subject, long expiresAt, soys.soyshttpovermc.gateway.policy.login.LoginMode mode) {
            this.subject = subject;
            this.expiresAt = expiresAt;
            this.mode = mode;
        }
    }
}
