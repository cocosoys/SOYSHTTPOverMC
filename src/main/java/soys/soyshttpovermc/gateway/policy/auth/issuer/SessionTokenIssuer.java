package soys.soyshttpovermc.gateway.policy.auth.issuer;

import org.bukkit.configuration.ConfigurationSection;
import soys.soyshttpovermc.gateway.policy.auth.AuthUtils;

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
        String token = AuthUtils.generateToken("st_", 24);
        sessions.put(token, new Session(subject, System.currentTimeMillis() + ttlMillis));
        return IssuedCredential.ofToken(token, cookieName);
    }

    @Override
    public boolean validate(CredentialPresentation p) {
        if (p == null) return false;
        String token = p.getBearer();
        if (token == null || token.isEmpty()) token = p.getApiKey();
        if (token == null || token.isEmpty()) token = p.getCookie(cookieName);
        if (token == null || token.isEmpty()) return false;
        Session s = sessions.get(token);
        if (s == null) return false;
        if (s.expiresAt < System.currentTimeMillis()) {
            sessions.remove(token);
            return false;
        }
        return true;
    }

    private static final class Session {
        final String subject;
        final long expiresAt;

        Session(String subject, long expiresAt) {
            this.subject = subject;
            this.expiresAt = expiresAt;
        }
    }
}
