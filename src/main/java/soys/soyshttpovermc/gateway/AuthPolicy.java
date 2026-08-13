package soys.soyshttpovermc.gateway;

import org.bukkit.configuration.ConfigurationSection;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
 */
public class AuthPolicy extends SecurityPolicy {

    private final Set<String> keys = new HashSet<>();
    private final List<PathPattern> paths = new ArrayList<>();
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
        paths.clear();
        for (String p : cfg.getStringList("paths")) {
            PathPattern pp = PathPattern.parse(p);
            if (pp != null) paths.add(pp);
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
        if (paths.isEmpty()) return true; // 未配置路径 = 保护所有
        String path = ctx.getPath();
        for (PathPattern pp : paths) {
            if (pp.matches(path)) return true;
        }
        return false;
    }

    @Override
    public PolicyResult check(GatewayContext ctx) {
        CredentialPresentation p = extract(ctx);
        if (isValid(p)) return PolicyResult.ALLOW;
        return PolicyResult.deny(401, "Unauthorized: missing or invalid credential");
    }

    private boolean isValid(CredentialPresentation p) {
        // 1) 静态 key：X-API-Key 头 或 Bearer
        if (acceptHeader && p.getApiKey() != null && keys.contains(p.getApiKey())) return true;
        if (acceptBearer && p.getBearer() != null && keys.contains(p.getBearer())) return true;
        // 2) Basic：用户名=key（密码不校验，仅作浏览器弹窗/客户端友好形态）
        if (acceptBasic && p.getBasicUser() != null && keys.contains(p.getBasicUser())) return true;
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

    /** 从请求头中提取凭证表示 */
    private CredentialPresentation extract(GatewayContext ctx) {
        String apiKey = acceptHeader ? ctx.getHeader(header) : null;
        if (apiKey != null && apiKey.isEmpty()) apiKey = null;

        String bearer = null;
        String basicUser = null;
        String basicPass = null;
        String auth = ctx.getHeader("Authorization");
        if (auth != null && !auth.isEmpty()) {
            String t = auth.trim();
            if (t.regionMatches(true, 0, "Bearer ", 0, 7)) {
                bearer = t.substring(7).trim();
                if (bearer.isEmpty()) bearer = null;
            } else if (t.regionMatches(true, 0, "Basic ", 0, 6)) {
                try {
                    String decoded = new String(Base64.getDecoder().decode(t.substring(6).trim()),
                            StandardCharsets.UTF_8);
                    int colon = decoded.indexOf(':');
                    if (colon >= 0) {
                        basicUser = decoded.substring(0, colon);
                        basicPass = decoded.substring(colon + 1);
                    } else {
                        basicUser = decoded;
                        basicPass = "";
                    }
                } catch (Exception ignored) {
                }
            }
        }

        Map<String, String> cookies = new HashMap<>();
        if (acceptCookie) {
            String cookieHeader = ctx.getHeader("Cookie");
            if (cookieHeader != null) {
                for (String part : cookieHeader.split(";")) {
                    int eq = part.indexOf('=');
                    if (eq > 0) {
                        String k = part.substring(0, eq).trim();
                        String v = part.substring(eq + 1).trim();
                        if (!k.isEmpty()) cookies.put(k, v);
                    }
                }
            }
        }
        return new CredentialPresentation(apiKey, bearer, basicUser, basicPass, cookies);
    }

    /**
     * 路径匹配：* 全匹配；/api/* 前缀匹配；/api 或 /api/ 视为目录前缀（匹配 /api/...）。
     */
    private static final class PathPattern {
        final String prefix; // null 表示 * 全匹配

        PathPattern(String prefix) {
            this.prefix = prefix;
        }

        static PathPattern parse(String p) {
            if (p == null) return null;
            p = p.trim();
            if (p.isEmpty()) return null;
            if (p.equals("*")) return new PathPattern(null);
            if (p.endsWith("/*")) return new PathPattern(p.substring(0, p.length() - 1));
            return new PathPattern(p.endsWith("/") ? p : p + "/");
        }

        boolean matches(String path) {
            if (prefix == null) return true;
            return path != null && path.startsWith(prefix);
        }
    }
}
