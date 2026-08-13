package soys.soyshttpovermc.gateway;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * API Key 鉴权策略：请求头携带密钥（默认 X-API-Key，同时兼容 Authorization: Bearer &lt;key&gt;），
 * 按路径粒度保护（paths 空 = 保护所有路径）。命中返回 401。
 */
public class ApiKeyPolicy extends SecurityPolicy {

    private final Set<String> keys = new HashSet<>();
    private final List<PathPattern> paths = new ArrayList<>();
    private String header = "X-API-Key";

    @Override
    public String name() {
        return "api-key";
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
    }

    @Override
    public boolean appliesTo(GatewayContext ctx) {
        if (paths.isEmpty()) return true; // 未配置路径 = 保护所有路径
        String path = ctx.getPath();
        for (PathPattern pp : paths) {
            if (pp.matches(path)) return true;
        }
        return false;
    }

    @Override
    public PolicyResult check(GatewayContext ctx) {
        String key = extractKey(ctx);
        if (key != null && keys.contains(key)) return PolicyResult.ALLOW;
        return PolicyResult.deny(401, "Unauthorized: missing or invalid API key");
    }

    private String extractKey(GatewayContext ctx) {
        String h = ctx.getHeader(header);
        if (h != null && !h.isEmpty()) return h.trim();
        String auth = ctx.getHeader("Authorization");
        if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String t = auth.substring(7).trim();
            if (!t.isEmpty()) return t;
        }
        return null;
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
