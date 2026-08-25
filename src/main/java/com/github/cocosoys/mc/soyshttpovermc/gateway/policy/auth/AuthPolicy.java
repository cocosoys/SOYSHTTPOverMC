package com.github.cocosoys.mc.soyshttpovermc.gateway.policy.auth;

import com.github.cocosoys.mc.soyshttpovermc.gateway.Credential;
import org.bukkit.configuration.ConfigurationSection;
import com.github.cocosoys.mc.soyshttpovermc.gateway.policy.auth.issuer.CredentialIssuer;
import com.github.cocosoys.mc.soyshttpovermc.gateway.policy.auth.util.AuthUtils;

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
public class AuthPolicy extends com.github.cocosoys.mc.soyshttpovermc.gateway.SecurityPolicy {

    private final Set<String> keys = new HashSet<>();
    private final List<String> pathPatterns = new ArrayList<>();
    private final List<String> exemptPatterns = new ArrayList<>(); // 豁免路径（公开端点，跳过鉴权）
    private String header = "X-API-Key";
    /** 网页登录使用的登录插件提供者名（gateway/policies/auth.yml login-provider；空=自动选第一个可用） */
    private String loginProviderName = "";
    private boolean acceptHeader = true;
    private boolean acceptBearer = true;
    private boolean acceptBasic = true;
    private boolean acceptCookie = true;
    private volatile List<CredentialIssuer> issuers = new ArrayList<>();
    /** 网关统一的 API 前缀（config.yml api-prefix，默认 /api）：匹配 exempt/paths 时自动兼容逻辑路径 */
    private volatile String apiPrefix = "/api";

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
        // 网页登录使用的登录插件提供者名（LoginProvider 的 name，如 authme；留空=自动取第一个可用）
        loginProviderName = cfg.getString("login-provider", "");
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

    /** 网页登录使用的登录插件提供者名（gateway/policies/auth.yml login-provider；空=自动）。 */
    public String getLoginProviderName() {
        return loginProviderName == null ? "" : loginProviderName;
    }

    /** 由 GatewayFilter 注入启用的颁发器列表 */
    public void setIssuers(List<CredentialIssuer> issuers) {
        this.issuers = issuers == null ? new ArrayList<CredentialIssuer>() : issuers;
    }

    /** 由 GatewayFilter 注入网关统一的 API 前缀（config.yml api-prefix）；匹配 exempt/paths 时自动兼容逻辑路径 */
    public void setApiPrefix(String prefix) {
        this.apiPrefix = prefix == null ? "" : prefix.trim();
    }

    @Override
    public boolean appliesTo(com.github.cocosoys.mc.soyshttpovermc.gateway.GatewayContext ctx) {
        String path = ctx.getPath();
        // 豁免路径（公开端点，如 /api/ping）：命中则本策略不适用，直接放行。
        // 同时兼容逻辑路径（/ping）与显式路径（/api/ping）——网关会自动给逻辑路径补上前缀后再匹配，
        // 因此用户在 auth.yml 中写 /ping 即可，无需手动写 /api 前缀（避免未开 auth 时地址不一致问题）。
        for (String exempt : exemptPatterns) {
            if (matchesPattern(path, exempt)) return false;
        }
        if (pathPatterns.isEmpty()) return true; // 未配置路径 = 保护所有
        for (String pattern : pathPatterns) {
            if (matchesPattern(path, pattern)) return true;
        }
        return false;
    }

    /**
     * 路径匹配：支持两种写法——用户直接写显式路径（/api/ping），或写逻辑路径（/ping）。
     * 对逻辑路径自动补 api-prefix 后再匹配（已带前缀则不重复补）。
     * 这样 exempt/paths 的写法与「auth 是否启用」「API 前缀是否生效」完全解耦。
     */
    private boolean matchesPattern(String path, String pattern) {
        if (pattern == null || pattern.isEmpty()) return true;
        if ("*".equals(pattern)) return true;
        if (AuthUtils.matchesPath(path, pattern)) return true;
        String prefixed = applyApiPrefix(pattern);
        return prefixed != null && !prefixed.equals(pattern) && AuthUtils.matchesPath(path, prefixed);
    }

    /** 给逻辑路径补 api-prefix（已带前缀 / 空前缀 / 通配前缀则不处理） */
    private String applyApiPrefix(String pattern) {
        if (apiPrefix == null || apiPrefix.isEmpty() || apiPrefix.equals("/")) return null;
        if (pattern.startsWith(apiPrefix)) return null; // 已显式带前缀
        if (pattern.equals("*")) return null;
        return apiPrefix + pattern;
    }

    @Override
    public com.github.cocosoys.mc.soyshttpovermc.gateway.PolicyResult check(com.github.cocosoys.mc.soyshttpovermc.gateway.GatewayContext ctx) {
        if (resolve(ctx) != null) return com.github.cocosoys.mc.soyshttpovermc.gateway.PolicyResult.ALLOW;
        return com.github.cocosoys.mc.soyshttpovermc.gateway.PolicyResult.deny(401, "Unauthorized: missing or invalid credential");
    }

    /**
     * 解析请求携带的凭证为 {@link com.github.cocosoys.mc.soyshttpovermc.gateway.Credential}（权限控制抽象载体）。
     * 与 {@link #check} 共用同一校验逻辑，供 TLS 策略判断"是否携带有效 X-API-Key 可旁路 HTTPS"。
     */
    public com.github.cocosoys.mc.soyshttpovermc.gateway.Credential resolve(com.github.cocosoys.mc.soyshttpovermc.gateway.GatewayContext ctx) {
        return resolveFromHeaders(ctx.getHeaders());
    }

    /** 从原始请求头解析凭证（无需构建 GatewayContext，便于 GatewayFilter 在链路最前复用）。 */
    public Credential resolveFromHeaders(java.util.Map<String, String> headers) {
        return AuthUtils.resolveCredential(headers, header,
                acceptHeader, acceptBearer, acceptBasic, acceptCookie, issuers, keys);
    }
}
