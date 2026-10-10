package com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth;

import com.github.cocosoys.mc.soyshttpovermc.web.gateway.AnonymousProbe;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.Credential;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.GatewayContext;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.PolicyResult;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.SecurityPolicy;
import com.github.cocosoys.mc.soyshttpovermc.permission.local.ApiKeyStore;
import com.github.cocosoys.mc.soyshttpovermc.spring.impl.LocalPermStorageImpl;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.issuer.CredentialIssuer;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.util.AuthUtils;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/**
 * 统一凭证鉴权策略（order 20）：支持三种凭证来源 + 可插拔颁发器，按路径保护。
 * <ul>
 *   <li><b>X-API-Key 头</b>：header 可配（默认 X-API-Key），值经 SHA-256 哈希匹配本地表
 *       （soys_api_key 表，由 /soyshttp apikey 管理；auth.yml keys 已迁移至该表）；</li>
 *   <li><b>Authorization</b>：Bearer &lt;key&gt;（哈希匹配本地表）或 Basic（用户名=key）；</li>
 *   <li><b>Cookie</b>：请求携带的 cookie 交由 gateway/issuers/ 下启用的
 *       {@link CredentialIssuer} 校验（如 session-token 会话令牌）。</li>
 * </ul>
 * 本地表 key 与任一启用颁发器匹配即放行，全部不匹配 → 401。
 * 接入新登录插件 = 实现 CredentialIssuer + gateway/issuers/ 放 yml，无需改本策略。
 * 凭证解析/路径匹配/常量时间比较复用 {@link AuthUtils}。
 */
public class AuthPolicy extends SecurityPolicy {

    private final List<String> pathPatterns = new ArrayList<>();
    private final List<String> exemptPatterns = new ArrayList<>(); // 豁免路径（公开端点，跳过鉴权）
    private String header = "X-API-Key";
    /**
     * 网页登录使用的登录插件提供者名（gateway/policies/auth.yml login-provider；空=自动选第一个可用）
     */
    private String loginProviderName = "";
    private boolean acceptHeader = true;
    private boolean acceptBearer = true;
    private boolean acceptBasic = true;
    private boolean acceptCookie = true;
    /**
     * 自动登录（记住我 / IP 匹配）配置（gateway/policies/auth.yml auto.login.*）。
     */
    private boolean rememberEnabled = true;   // auto.login.ttl.enable（默认 true）
    private int rememberTtlDays = 7;          // auto.login.ttl.activetime（默认 7 天）
    private boolean ipEnabled = false;        // auto.login.ip.enabled（默认 false）
    /**
     * 设备指纹双因子开关（auth.yml auto.login.fp.enable，默认 false）：
     * true=开启：自动登录（记住我）时校验请求 X-Device-Fingerprint 与绑定表一致；
     * false=仅凭 Cookie 凭证（现状，行为不变）。
     */
    private boolean fpEnabled = false;
    /**
     * 指纹严格模式（auth.yml auto.login.fp.strict，默认 true）：
     * true=指纹不一致/未携带 → 拒绝自动登录；false=不一致仅告警放行（宽松）。
     */
    private boolean fpStrict = true;
    /**
     * 游戏端→网页端绑定票据有效期（秒，auth.yml auto.login.ticket.ttl，默认 60）。
     */
    private int ticketTtlSeconds = 60;
    /**
     * 游戏内登录后是否发送可点击票据链接（auth.yml auto.login.ticket.in-game-link，默认 true）。
     */
    private boolean ticketLinkEnabled = true;
    /**
     * X-API-Key 本地权限降级开关（auth.yml api-key.local-fallback-all，默认 false）：
     * true=本地权限表（local）不可用时 X-API-Key 退化为拥有所有权限（fail-open）；
     * false=不可用时按无权限拒绝（安全优先，默认）。
     */
    private boolean apiKeyLocalFallbackAll = false;
    /**
     * Cookie Domain（auth.yml cookie.domain，默认空=精确 host）：
     * 配 {@code .example.com} 后所有子域共享 cookie（多子域名 SSO）；
     * 同域名多端口部署浏览器按 host 共享，无需配置。
     */
    private String cookieDomain = "";
    /**
     * Cookie Secure 标记（auth.yml cookie.secure，默认 false）：HTTPS 部署开启；跨域名回跳必须 HTTPS。
     */
    private boolean cookieSecure = false;
    /**
     * Cookie SameSite（auth.yml cookie.same-site，默认 Lax）：跨域顶层 GET 导航/302 回跳 Lax 即足够。
     */
    private String cookieSameSite = "Lax";
    /**
     * SSO 统一登录页地址（auth.yml sso.login-url，默认空=不启用回跳链）：
     * 浏览器 HTML 请求未登录时不返 401 JSON，而是 302 跳到此地址并附带 ?redirect=<原URL>。
     * 留空=行为不变（API 与 HTML 一律 401）。
     */
    private String ssoLoginUrl = "";
    /**
     * SSO 允许回跳的来源白名单（auth.yml sso.allowed-origins，origin = scheme://host[:port]）：
     * 仅当当前请求 origin 命中本列表时才发 302（否则回退 401），防开放跳转 / 被当跳板。
     */
    private List<String> ssoAllowedOrigins = new ArrayList<>();
    private volatile List<CredentialIssuer> issuers = new ArrayList<>();
    /**
     * 本地 API Key 表（soys_api_key 实体；懒创建）。
     */
    private volatile ApiKeyStore apiKeyStore;
    /**
     * 网关统一的 API 前缀（config.yml api-prefix，默认 /api）：匹配 exempt/paths 时自动兼容逻辑路径
     */
    private volatile String apiPrefix = "/api";
    /**
     * 匿名端点探测器（由 GatewayFilter 注入，指向 ApiRegistry）：命中 @Anonymous 注解端点时本策略放行（免凭证）。
     */
    private volatile AnonymousProbe anonymousProbe;

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
        // 自动登录（记住我 / IP 匹配 / 设备指纹双因子）：auto.login.*（缺省保持默认）
        ConfigurationSection autoLogin = cfg.getConfigurationSection("auto.login");
        if (autoLogin != null) {
            ConfigurationSection ttl = autoLogin.getConfigurationSection("ttl");
            rememberEnabled = ttl == null || ttl.getBoolean("enable", true);
            rememberTtlDays = ttl == null ? 7 : Math.max(1, ttl.getInt("activetime", 7));
            ConfigurationSection ip = autoLogin.getConfigurationSection("ip");
            ipEnabled = ip != null && ip.getBoolean("enabled", false);
            ConfigurationSection fp = autoLogin.getConfigurationSection("fp");
            if (fp != null) {
                fpEnabled = fp.getBoolean("enable", false);
                fpStrict = fp.getBoolean("strict", true);
            }
            ConfigurationSection ticket = autoLogin.getConfigurationSection("ticket");
            if (ticket != null) {
                ticketTtlSeconds = Math.max(10, ticket.getInt("ttl", 60));
                ticketLinkEnabled = ticket.getBoolean("in-game-link", true);
            }
        }
        // X-API-Key 本地权限降级开关（api-key.local-fallback-all，默认 false）
        ConfigurationSection apiKeyCfg = cfg.getConfigurationSection("api-key");
        apiKeyLocalFallbackAll = apiKeyCfg != null && apiKeyCfg.getBoolean("local-fallback-all", false);
        // Cookie 属性（cookie.*；多子域名共享 / HTTPS 加固）
        ConfigurationSection cookieCfg = cfg.getConfigurationSection("cookie");
        cookieDomain = cookieCfg == null ? "" : cookieCfg.getString("domain", "").trim();
        cookieSecure = cookieCfg != null && cookieCfg.getBoolean("secure", false);
        String ss = cookieCfg == null ? "Lax" : cookieCfg.getString("same-site", "Lax");
        cookieSameSite = (ss == null || ss.trim().isEmpty()) ? "Lax" : ss.trim();
        // SSO 跨域回跳链（sso.*；login-url 空=不启用）
        ConfigurationSection ssoCfg = cfg.getConfigurationSection("sso");
        ssoLoginUrl = ssoCfg == null ? "" : ssoCfg.getString("login-url", "").trim();
        ssoAllowedOrigins.clear();
        if (ssoCfg != null) {
            for (String o : ssoCfg.getStringList("allowed-origins")) {
                if (o != null && !o.trim().isEmpty()) ssoAllowedOrigins.add(o.trim());
            }
        }
    }

    /**
     * 网页登录使用的登录插件提供者名（gateway/policies/auth.yml login-provider；空=自动）。
     */
    public String getLoginProviderName() {
        return loginProviderName == null ? "" : loginProviderName;
    }

    /**
     * X-API-Key 请求头名（auth.yml header，默认 X-API-Key）。
     */
    public String getHeader() {
        return header == null || header.isEmpty() ? "X-API-Key" : header;
    }

    /**
     * 自动登录配置：记住我（设备免登录）总开关（auto.login.ttl.enable，默认 true）。
     */
    public boolean isRememberEnabled() {
        return rememberEnabled;
    }

    /**
     * 自动登录配置：记住我凭证有效期（天，auto.login.ttl.activetime，默认 7）。
     */
    public int getRememberTtlDays() {
        return rememberTtlDays;
    }

    /**
     * 自动登录配置：旧“IP 匹配免登录”开关（auto.login.ip.enabled，默认 false）。
     */
    public boolean isIpEnabled() {
        return ipEnabled;
    }

    /**
     * 自动登录配置：设备指纹双因子开关（auto.login.fp.enable，默认 false）。
     */
    public boolean isFpEnabled() {
        return fpEnabled;
    }

    /**
     * 自动登录配置：指纹严格模式（auto.login.fp.strict，默认 true）。
     */
    public boolean isFpStrict() {
        return fpStrict;
    }

    /**
     * 自动登录配置：游戏端→网页端绑定票据有效期（秒，auto.login.ticket.ttl，默认 60）。
     */
    public int getTicketTtlSeconds() {
        return ticketTtlSeconds;
    }

    /**
     * 自动登录配置：游戏内登录后是否发送可点击票据链接（auto.login.ticket.in-game-link，默认 true）。
     */
    public boolean isTicketLinkEnabled() {
        return ticketLinkEnabled;
    }

    /**
     * X-API-Key 本地权限降级开关（auth.yml api-key.local-fallback-all，默认 false）。
     * true=local 不可用时 X-API-Key 全权限放行；false=按无权限拒绝。供权限判定层（CombinedPermissionService）使用。
     */
    public boolean isApiKeyLocalFallbackAll() {
        return apiKeyLocalFallbackAll;
    }

    /**
     * Cookie Domain（auth.yml cookie.domain，空=精确 host；配 .example.com = 多子域共享）。
     */
    public String getCookieDomain() {
        return cookieDomain == null ? "" : cookieDomain;
    }

    /**
     * Cookie Secure 标记（auth.yml cookie.secure，默认 false；HTTPS 部署开启）。
     */
    public boolean isCookieSecure() {
        return cookieSecure;
    }

    /**
     * Cookie SameSite（auth.yml cookie.same-site，默认 Lax）。
     */
    public String getCookieSameSite() {
        return cookieSameSite;
    }

    /**
     * SSO 统一登录页地址（auth.yml sso.login-url，空=未启用回跳链）。
     */
    public String getSsoLoginUrl() {
        return ssoLoginUrl == null ? "" : ssoLoginUrl;
    }

    /**
     * SSO 允许回跳的来源白名单（origin 列表）。
     */
    public List<String> getSsoAllowedOrigins() {
        return ssoAllowedOrigins;
    }

    /**
     * 本地 API Key 表门面（懒创建；与权限层各自持实例，共享统一 ORM 存储）。
     */
    public ApiKeyStore getApiKeyStore() {
        ApiKeyStore s = apiKeyStore;
        if (s == null) {
            synchronized (this) {
                s = apiKeyStore;
                if (s == null) {
                    s = new ApiKeyStore(new LocalPermStorageImpl());
                    apiKeyStore = s;
                }
            }
        }
        return s;
    }

    /**
     * 请求值是否为有效的本地表 key（存在 && 启用 && 未过期）。
     * 供权限判定层识别「已通过认证门」的 X-API-Key，避免把未认证的请求头误当 key。
     */
    public boolean isValidKey(String key) {
        return getApiKeyStore().isValid(key);
    }

    /**
     * 由 GatewayFilter 注入启用的颁发器列表
     */
    public void setIssuers(List<CredentialIssuer> issuers) {
        this.issuers = issuers == null ? new ArrayList<CredentialIssuer>() : issuers;
    }

    /**
     * 由 GatewayFilter 注入网关统一的 API 前缀（config.yml api-prefix）；匹配 exempt/paths 时自动兼容逻辑路径
     */
    public void setApiPrefix(String prefix) {
        this.apiPrefix = prefix == null ? "" : prefix.trim();
    }

    /**
     * 由 GatewayFilter 注入匿名端点探测器（ApiRegistry 实现）；命中 @Anonymous 的端点认证门放行。
     */
    public void setAnonymousProbe(AnonymousProbe probe) {
        this.anonymousProbe = probe;
    }

    @Override
    public boolean appliesTo(GatewayContext ctx) {
        String path = ctx.getPath();
        // 豁免路径（公开端点，如 /api/ping）：命中则本策略不适用，直接放行。
        // 同时兼容逻辑路径（/ping）与显式路径（/api/ping）——网关会自动给逻辑路径补上前缀后再匹配，
        // 因此用户在 auth.yml 中写 /ping 即可，无需手动写 /api 前缀（避免未开 auth 时地址不一致问题）。
        for (String exempt : exemptPatterns) {
            if (matchesPattern(path, exempt)) return false;
        }
        // @Anonymous 注解端点（如验证码/登录/探活）：认证门放行（免凭证）。
        // 与 exempt 配置互为双保险——exempt 是运维手动豁免，@Anonymous 是代码层注解声明。
        AnonymousProbe probe = anonymousProbe;
        if (probe != null && probe.isAnonymous(ctx.getMethod(), path)) return false;
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
     * 注意：path 可能包含 query 字符串（如 /api/auth/status?player=test），匹配前先剥离。
     */
    private boolean matchesPattern(String path, String pattern) {
        if (pattern == null || pattern.isEmpty()) return true;
        if ("*".equals(pattern)) return true;
        // 剥离 query 字符串（/api/auth/status?player=test → /api/auth/status）
        String cleanPath = path;
        int q = path.indexOf('?');
        if (q >= 0) cleanPath = path.substring(0, q);
        if (AuthUtils.matchesPath(cleanPath, pattern)) return true;
        String prefixed = applyApiPrefix(pattern);
        return prefixed != null && !prefixed.equals(pattern) && AuthUtils.matchesPath(cleanPath, prefixed);
    }

    /**
     * 给逻辑路径补 api-prefix（已带前缀 / 空前缀 / 通配前缀则不处理）
     */
    private String applyApiPrefix(String pattern) {
        if (apiPrefix == null || apiPrefix.isEmpty() || apiPrefix.equals("/")) return null;
        if (pattern.startsWith(apiPrefix)) return null; // 已显式带前缀
        if (pattern.equals("*")) return null;
        return apiPrefix + pattern;
    }

    @Override
    public PolicyResult check(GatewayContext ctx) {
        if (resolve(ctx) != null) return PolicyResult.ALLOW;
        // 浏览器 HTML 导航请求未登录 → 302 SSO 回跳统一登录页（而非 401 JSON）
        PolicyResult sso = ssoRedirectIfHtml(ctx);
        if (sso != null) return sso;
        return PolicyResult.deny(401, "Unauthorized: missing or invalid credential");
    }

    /**
     * 浏览器 HTML 导航请求未登录时，302 跳统一登录页并附带 redirect=原URL。
     * 仅当全部条件满足时返回 302，否则返回 null（回退 401）：
     * sso.login-url 已配置；GET + Accept 含 text/html；当前 origin 在 allowed-origins 白名单；
     * 不是对登录页 / SSO callback 自身的请求（防 302 循环）。
     */
    private PolicyResult ssoRedirectIfHtml(GatewayContext ctx) {
        if (ssoLoginUrl.isEmpty()) return null;
        if (!"GET".equalsIgnoreCase(ctx.getMethod())) return null;
        String accept = ctx.getHeader("Accept");
        if (accept == null || !accept.toLowerCase().contains("text/html")) return null;
        String path = ctx.getPath();
        // 防循环：登录页 / SSO callback 自身不跳
        if (path.contains("/auth/login") || path.contains("/auth/sso/") || path.endsWith("login.html")) return null;
        // 当前请求 origin（scheme://host[:port]）必须在白名单
        String host = ctx.getHeader("Host");
        if (host == null || host.isEmpty()) return null;
        String origin = (ctx.isTls() ? "https://" : "http://") + host;
        if (!originAllowed(origin)) return null;
        // redirect = 当前完整 URL（scheme://host + 原始路径含 query）
        String redirect = origin + ctx.getRawPath();
        String sep = ssoLoginUrl.contains("?") ? "&" : "?";
        String location = ssoLoginUrl + sep + "redirect=" + urlEncode(redirect);
        java.util.Map<String, String> headers = new java.util.HashMap<>();
        headers.put("Location", location);
        return PolicyResult.deny(302, "", headers);
    }

    /** origin 白名单匹配（尾部去斜杠归一，大小写不敏感）。 */
    private boolean originAllowed(String origin) {
        if (ssoAllowedOrigins.isEmpty()) return false;
        String o = origin.endsWith("/") ? origin.substring(0, origin.length() - 1) : origin;
        for (String allow : ssoAllowedOrigins) {
            String a = allow.endsWith("/") ? allow.substring(0, allow.length() - 1) : allow;
            if (a.equalsIgnoreCase(o)) return true;
        }
        return false;
    }

    private static String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    /**
     * 解析请求携带的凭证为 {@link Credential}（权限控制抽象载体）。
     * 与 {@link #check} 共用同一校验逻辑，供 TLS 策略判断"是否携带有效 X-API-Key 可旁路 HTTPS"。
     */
    public Credential resolve(GatewayContext ctx) {
        return resolveFromHeaders(ctx.getHeaders());
    }

    /**
     * 从原始请求头解析凭证（无需构建 GatewayContext，便于 GatewayFilter 在链路最前复用）。
     */
    public Credential resolveFromHeaders(java.util.Map<String, String> headers) {
        return AuthUtils.resolveCredential(headers, header,
                acceptHeader, acceptBearer, acceptBasic, acceptCookie, issuers, null, getApiKeyStore());
    }
}
