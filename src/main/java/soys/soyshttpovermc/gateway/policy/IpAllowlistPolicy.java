package soys.soyshttpovermc.gateway.policy;

import org.bukkit.configuration.ConfigurationSection;
import soys.soyshttpovermc.gateway.GatewayContext;
import soys.soyshttpovermc.gateway.PolicyResult;
import soys.soyshttpovermc.gateway.SecurityPolicy;

import java.util.ArrayList;
import java.util.List;

/**
 * IP 白/黑名单策略：支持精确 IP 与 CIDR 段（IPv4）。
 * <ul>
 *   <li>default: allow → 列表即黑名单（命中拒绝），其余放行；</li>
 *   <li>default: deny  → 列表即白名单（仅命中放行），其余拒绝。</li>
 * </ul>
 * trust-proxy=true 时优先取 X-Forwarded-For 首个 IP（用于前置可信代理场景）。
 */
public class IpAllowlistPolicy extends SecurityPolicy {

    private boolean defaultAllow = true;
    private boolean trustProxy = false;
    private final List<Cidr> rules = new ArrayList<>();

    @Override
    public String name() {
        return "ip-allowlist";
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public void reload(ConfigurationSection cfg) {
        super.reload(cfg);
        if (cfg == null) return;
        defaultAllow = cfg.getString("default", "allow").equalsIgnoreCase("allow");
        trustProxy = cfg.getBoolean("trust-proxy", false);
        rules.clear();
        for (String s : cfg.getStringList("list")) {
            if (s == null) continue;
            Cidr c = Cidr.parse(s.trim());
            if (c != null) rules.add(c);
        }
    }

    @Override
    public PolicyResult check(GatewayContext ctx) {
        String ip = effectiveIp(ctx);
        boolean inList = false;
        for (Cidr c : rules) {
            if (c.matches(ip)) {
                inList = true;
                break;
            }
        }
        boolean allowed = defaultAllow ? !inList : inList;
        if (allowed) return PolicyResult.ALLOW;
        return PolicyResult.deny(403, "Forbidden: ip " + ip + " is not allowed");
    }

    private String effectiveIp(GatewayContext ctx) {
        if (trustProxy) {
            String xff = ctx.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isEmpty()) {
                String first = xff.split(",")[0].trim();
                if (!first.isEmpty()) return first;
            }
        }
        return ctx.getSocketIp();
    }

    /** IPv4 精确地址或 CIDR 段（a.b.c.d/n，n=0..32） */
    private static final class Cidr {
        final int base;
        final int mask;

        Cidr(int base, int mask) {
            this.base = base;
            this.mask = mask;
        }

        static Cidr parse(String s) {
            try {
                String ipPart;
                int bits = 32;
                int slash = s.indexOf('/');
                if (slash >= 0) {
                    ipPart = s.substring(0, slash);
                    bits = Integer.parseInt(s.substring(slash + 1));
                    if (bits < 0 || bits > 32) return null;
                } else {
                    ipPart = s;
                }
                int ip = ipv4(ipPart);
                if (ip < 0) return null;
                return new Cidr(ip, bits);
            } catch (Exception e) {
                return null;
            }
        }

        boolean matches(String ipStr) {
            int ip = ipv4(ipStr);
            if (ip < 0) return false;
            if (mask == 32) return base == ip;
            int m = mask == 0 ? 0 : (0xFFFFFFFF << (32 - mask));
            return (base & m) == (ip & m);
        }

        private static int ipv4(String s) {
            try {
                String[] p = s.split("\\.");
                if (p.length != 4) return -1;
                int v = 0;
                for (String part : p) {
                    int x = Integer.parseInt(part);
                    if (x < 0 || x > 255) return -1;
                    v = (v << 8) | x;
                }
                return v;
            } catch (Exception e) {
                return -1;
            }
        }
    }
}
