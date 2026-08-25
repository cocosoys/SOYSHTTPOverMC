package com.github.cocosoys.mc.soyshttpovermc.gateway.policy;

import org.bukkit.configuration.ConfigurationSection;
import com.github.cocosoys.mc.soyshttpovermc.gateway.GatewayContext;
import com.github.cocosoys.mc.soyshttpovermc.gateway.PolicyResult;
import com.github.cocosoys.mc.soyshttpovermc.gateway.SecurityPolicy;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 令牌桶限流策略：按 IP（默认）或 API Key 维度，rpm 每分钟补充、burst 突发上限；
 * 超限返回 429 + Retry-After。内存桶惰性清理，防止长期膨胀。
 */
public class RateLimitPolicy extends SecurityPolicy {

    private String scope = "ip";
    private double ratePerSec = 1.0;
    private double burst = 10;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private long checks = 0;

    @Override
    public String name() {
        return "rate-limit";
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public void reload(ConfigurationSection cfg) {
        super.reload(cfg);
        if (cfg == null) return;
        scope = cfg.getString("scope", "ip");
        double rpm = Math.max(1, cfg.getDouble("rpm", 60));
        ratePerSec = rpm / 60.0;
        burst = Math.max(1, cfg.getInt("burst", 10));
        buckets.clear();
    }

    @Override
    public PolicyResult check(GatewayContext ctx) {
        String key = scopeKey(ctx);
        Bucket b = buckets.computeIfAbsent(key, k -> new Bucket(ratePerSec, burst));
        synchronized (b) {
            if (b.tryAcquire()) {
                maybeSweep();
                return PolicyResult.ALLOW;
            }
        }
        long retryAfter = Math.max(1, (long) Math.ceil(1.0 / ratePerSec));
        Map<String, String> h = new HashMap<>();
        h.put("Retry-After", String.valueOf(retryAfter));
        return PolicyResult.deny(429, "Too Many Requests: rate limit exceeded", h);
    }

    private void maybeSweep() {
        if ((++checks & 0x3FF) == 0) {
            buckets.entrySet().removeIf(e -> e.getValue().stale());
        }
    }

    private String scopeKey(GatewayContext ctx) {
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

    /** 令牌桶：按 rate 连续补充，容量 capacity；超过 5 分钟未访问视为过期 */
    private static final class Bucket {
        final double rate;
        final double capacity;
        double tokens;
        long lastRefillNanos;
        long lastAccessNanos;

        Bucket(double rate, double capacity) {
            this.rate = rate;
            this.capacity = capacity;
            this.tokens = capacity;
            long now = System.nanoTime();
            this.lastRefillNanos = now;
            this.lastAccessNanos = now;
        }

        synchronized boolean tryAcquire() {
            long now = System.nanoTime();
            lastAccessNanos = now;
            long dt = now - lastRefillNanos;
            if (dt > 0) {
                tokens = Math.min(capacity, tokens + rate * dt / 1_000_000_000.0);
                lastRefillNanos = now;
            }
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }

        boolean stale() {
            return System.nanoTime() - lastAccessNanos > TimeUnit.MINUTES.toNanos(5);
        }
    }
}
