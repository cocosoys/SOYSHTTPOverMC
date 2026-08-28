package com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy;

import org.bukkit.configuration.ConfigurationSection;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.GatewayContext;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.PolicyResult;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.SecurityPolicy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * API/网页访问限制器策略：按多条规则分别限流，每条规则以 {@code scope}（ip | key | path）
 * 在固定窗口内把访问次数限制在 {@code limit} 以内，超出则返回 429（附 Retry-After），等待窗口刷新后再开放。
 *
 * <ul>
 *   <li><b>规则列表</b>：{@code path-patterns} 下每条以 {name, description, scope, limit, window-seconds} 独立定义；
 *       一次请求会被<b>每条规则独立计数</b>，任一规则达到上限即被拒绝；</li>
 *   <li><b>固定窗口计数</b>：每 key 维护「窗口起点 + 已访问次数」；窗口到期后自动归零重计；</li>
 *   <li><b>被动刷新</b>：无后台定时任务，仅在本策略被访问时检测窗口是否已过、需要则刷新次数；</li>
 *   <li>规则列表为空时策略不生效（对所有请求放行）。</li>
 * </ul>
 *
 * <p>默认关闭（{@code gateway/policies/access-limiter.yml} 的 {@code enabled: false}）。</p>
 */
public class AccessLimiterPolicy extends SecurityPolicy {

    private final List<Rule> rules = new ArrayList<>();
    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();
    /** 访问计数：每 CLEANUP_INTERVAL 次访问触发一次过期清理，避免计数器无限增长。 */
    private static final long CLEANUP_INTERVAL = 1000L;
    private final AtomicLong accessCount = new AtomicLong(0);
    /** 过期阈值：超过 2 个最大窗口时间未访问的计数器将被清理（避免活跃计数器被误删）。 */
    private static final long EXPIRE_THRESHOLD_MILLIS = 2 * 3600 * 1000L;

    @Override
    public String name() {
        return "access-limiter";
    }

    @Override
    public int order() {
        return 31;
    }

    @Override
    public void reload(ConfigurationSection cfg) {
        super.reload(cfg);
        rules.clear();
        if (cfg != null) {
            List<?> list = cfg.getList("path-patterns");
            if (list != null) {
                for (Object o : list) {
                    if (!(o instanceof Map)) continue;
                    Map<?, ?> m = (Map<?, ?>) o;
                    String name = stringOf(m.get("name"));
                    String scope = stringOf(m.get("scope"));
                    if (scope == null || scope.isEmpty()) scope = "ip";
                    int limit = Math.max(1, intOf(m.get("limit"), 50));
                    long windowMillis = Math.max(1000L, longOf(m.get("window-seconds"), 3600) * 1000L);
                    rules.add(new Rule(name, scope, limit, windowMillis));
                }
            }
        }
        counters.clear();
    }

    @Override
    public boolean appliesTo(GatewayContext ctx) {
        return !rules.isEmpty();
    }

    @Override
    public PolicyResult check(GatewayContext ctx) {
        // 每 CLEANUP_INTERVAL 次访问触发一次过期清理，避免计数器内存泄漏
        if (accessCount.incrementAndGet() % CLEANUP_INTERVAL == 0) {
            cleanupExpiredCounters();
        }
        // 每条规则独立限流；任一规则达到上限即拒绝
        for (int i = 0; i < rules.size(); i++) {
            PolicyResult res = rules.get(i).check(i, ctx, counters);
            if (!res.isAllow()) return res;
        }
        return PolicyResult.ALLOW;
    }

    /** 清理超过 EXPIRE_THRESHOLD_MILLIS 未访问的过期计数器，防止长期运行后内存无限增长。 */
    private void cleanupExpiredCounters() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Counter>> it = counters.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Counter> entry = it.next();
            Counter c = entry.getValue();
            if (c != null && now - c.lastAccessTime > EXPIRE_THRESHOLD_MILLIS) {
                it.remove();
            }
        }
    }

    private static String stringOf(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static int intOf(Object v, int def) {
        if (v instanceof Number) return ((Number) v).intValue();
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return def;
        }
    }

    private static long longOf(Object v, long def) {
        if (v instanceof Number) return ((Number) v).longValue();
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Exception e) {
            return def;
        }
    }

    /** 单条限流规则：独立的 scope / limit / 窗口。 */
    private static final class Rule {
        private final String name;
        private final String scope;
        private final int limit;
        private final long windowMillis;

        Rule(String name, String scope, int limit, long windowMillis) {
            this.name = name;
            this.scope = scope;
            this.limit = limit;
            this.windowMillis = windowMillis;
        }

        PolicyResult check(int ruleIndex, GatewayContext ctx, ConcurrentHashMap<String, Counter> counters) {
            // 以「规则索引 + scope 键」命名空间区分不同规则，避免不同规则互相串计数
            String key = ruleIndex + ":" + scopeKey(ctx);
            Counter c = counters.computeIfAbsent(key, k -> new Counter());
            synchronized (c) {
                long now = System.currentTimeMillis();
                c.lastAccessTime = now;
                // 被动刷新：窗口已过则重置计数与窗口起点
                if (now - c.windowStart >= windowMillis) {
                    c.windowStart = now;
                    c.count = 0;
                }
                if (c.count >= limit) {
                    long retryAfter = Math.max(1, (c.windowStart + windowMillis - now + 999L) / 1000L);
                    Map<String, String> h = new HashMap<>();
                    h.put("Retry-After", String.valueOf(retryAfter));
                    String label = (name == null || name.isEmpty()) ? "rule#" + ruleIndex : name;
                    return PolicyResult.deny(429, "Too Many Requests: access limit reached (" + label + ")", h);
                }
                c.count++;
            }
            return PolicyResult.ALLOW;
        }

        /** 按 scope 计算限流键：quantity 从请求头 / socket IP 派生。 */
        private String scopeKey(GatewayContext ctx) {
            if ("path".equalsIgnoreCase(scope)) {
                return "path:" + ctx.getPath();
            }
            if ("key".equalsIgnoreCase(scope)) {
                String k = ctx.getHeader("X-API-Key");
                if (k != null && !k.isEmpty()) return "k:" + k.trim();
                String auth = ctx.getHeader("Authorization");
                if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
                    String t = auth.substring(7).trim();
                    if (!t.isEmpty()) return "k:" + t;
                }
            }
            return "ip:" + ctx.getSocketIp();
        }
    }

    /** 单一限流计数单元：固定窗口 + 命中次数 + 最后访问时间（用于过期清理）。 */
    private static final class Counter {
        long count = 0;
        long windowStart = System.currentTimeMillis();
        volatile long lastAccessTime = System.currentTimeMillis();
    }
}