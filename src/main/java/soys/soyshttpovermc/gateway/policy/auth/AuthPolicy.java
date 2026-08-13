package soys.soyshttpovermc.gateway.policy.auth;

import org.bukkit.configuration.ConfigurationSection;
import soys.soyshttpovermc.gateway.*;
import soys.soyshttpovermc.gateway.policy.auth.issuer.CredentialIssuer;
import soys.soyshttpovermc.gateway.policy.auth.issuer.CredentialPresentation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 统一凭证鉴权策略（order 20）：支持三种凭证来源 + 可插拔颁发器，按路径保护。
 * <ul>
 *   <li><b>X-API-Key 头</b>：header 可配（默认 X-API-Key），值匹配静态 keys；</li>
 *   <li><b>Authorization</b>：Bearer &lt;key&gt;（匹配静态 keys）或 Basic（用户名=key）；</li>
 *   <li><b>Cookie</b>：请求携带的 cookie 交由 gateway/issuers/ 下启用的
 *       {@link CredentialIssuer} 校验（如 session-token 会话令牌）。</li>
 * </ul>
 * 静态 keys 与任一启用颁发器匹配即放行，全部不匹配 → 401。
 * 接入新登录插件 = 实现 CredentialIssuer + gateway/issuers/ 放 yml，无需改本策略。
 * 凭证解析/路径匹配/常量时间比较复用 {@link AuthUtils}。
 */
public class AuthPolicy extends SecurityPolicy {

    private final Set<String> keys = new HashSet<>();
    private final List<String> pathPatterns = new ArrayList<>();
    private final List<String> exemptPatterns = new ArrayList<>(); // 豁免路径（公开端点，跳过鉴权）
    private String header = "X-API-Key";
    private boolean acceptHeader = true;
    private boolean acceptBearer = true;
    private boolean acceptBasic = true;
    private boolean acceptCookie = true;
    private volatile List<CredentialIssuer> issuers = new ArrayList<>();

    @Override
    public String name() {
        return "auth";
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public void reload(ConfigurationSection cfg) {
        super.reload(cfg);
        if (cfg == null) return;
        header = cfg.getString("header", "X-API-Key");
        keys.clear();
        keys.addAll(cfg.getStringList("keys"));
        pathPatterns.clear();
        for (String p : cfg.getStringList("paths")) {
            if (p != null && !p.trim().isEmpty()) pathPatterns.add(p.trim());
        }
        exemptPatterns.clear();
        for (String p : cfg.getStringList("exempt")) {
            if (p != null && !p.trim().isEmpty()) exemptPatterns.add(p.trim());
        }
        ConfigurationSection acc = cfg.getConfigurationSection("accept");
        acceptHeader = acc == null || acc.getBoolean("header", true);
        acceptBearer = acc == null || acc.getBoolean("bearer", true);
        acceptBasic = acc == null || acc.getBoolean("basic", true);
        acceptCookie = acc == null || acc.getBoolean("cookie", true);
    }

    /** 由 GatewayFilter 注入启用的颁发器列表 */
    public void setIssuers(List<CredentialIssuer> issuers) {
        this.issuers = issuers == null ? new ArrayList<CredentialIssuer>() : issuers;
    }

    @Override
    public boolean appliesTo(GatewayContext ctx) {
        String path = ctx.getPath();
        // 豁免路径（公开端点，如 /api/ping）：命中则本策略不适用，直接放行
        for (String exempt : exemptPatterns) {
            if (AuthUtils.matchesPath(path, exempt)) return false;
        }
        if (pathPatterns.isEmpty()) return true; // 未配置路径 = 保护所有
        for (String pattern : pathPatterns) {
            if (AuthUtils.matchesPath(path, pattern)) return true;
        }
        return false;
    }

    @Override
    public PolicyResult check(GatewayContext ctx) {
        CredentialPresentation p = AuthUtils.extractPresentation(ctx.getHeaders(), header,
                acceptHeader, acceptBearer, acceptBasic, acceptCookie);
        if (isValid(p)) return PolicyResult.ALLOW;
        return PolicyResult.deny(401, "Unauthorized: missing or invalid credential");
    }

    private boolean isValid(CredentialPresentation p) {
        // 1) 静态 key：X-API-Key 头 或 Bearer（常量时间比较）
        if (acceptHeader && matchesAnyKey(p.getApiKey())) return true;
        if (acceptBearer && matchesAnyKey(p.getBearer())) return true;
        // 2) Basic：用户名=key（密码不校验，仅作浏览器弹窗/客户端友好形态）
        if (acceptBasic && matchesAnyKey(p.getBasicUser())) return true;
        // 3) 启用的颁发器校验（Bearer / X-API-Key / Cookie 均可被颁发器识别）
        for (CredentialIssuer issuer : issuers) {
            if (!issuer.isEnabled()) continue;
            try {
                if (issuer.validate(p)) return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private boolean matchesAnyKey(String presented) {
        if (presented == null) return false;
        for (String k : keys) {
            if (AuthUtils.constantTimeEquals(k, presented)) return true;
        }
        return false;
    }
}
