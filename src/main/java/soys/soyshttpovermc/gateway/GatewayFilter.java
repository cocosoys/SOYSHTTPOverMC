package soys.soyshttpovermc.gateway;

import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 安全策略链执行器（nginx 式网关的核心）：
 * 按 order 升序遍历已启用的策略，任一 DENY 立即短路返回。
 *
 * <p>配置采用独立文件布局（便于未来接入更多策略）：
 * <pre>
 *   gateway/config.yml             总开关 enabled
 *   gateway/https.yml              HTTPS 设置（host 会覆盖 tls 策略的 host）
 *   gateway/policies/&lt;name&gt;.yml   每个策略一个文件，文件名即策略名
 * </pre>
 * 新增策略 = 1) 继承 {@link SecurityPolicy} 实现 check；2) 在 {@link #REGISTRY} 注册一行；
 * 3) 在 gateway/policies/ 放一个 &lt;name&gt;.yml。无需改动链执行逻辑。
 * 修改配置后 /soyshttp reload 热重载，无需重启。
 */
public class GatewayFilter {

    /** 策略注册表：策略名（= policies 目录下的文件名）→ 工厂。新增策略在此注册。 */
    private static final Map<String, Supplier<SecurityPolicy>> REGISTRY = new LinkedHashMap<>();

    static {
        REGISTRY.put("tls", TlsPolicy::new);
        REGISTRY.put("ip-allowlist", IpAllowlistPolicy::new);
        REGISTRY.put("api-key", ApiKeyPolicy::new);
        REGISTRY.put("rate-limit", RateLimitPolicy::new);
    }

    private final Logger log;
    private volatile List<SecurityPolicy> policies = new ArrayList<>();

    public GatewayFilter(Logger log) {
        this.log = log;
    }

    /** 从 gateway/ 目录重建策略链（启动与 /soyshttp reload 均走这里）。 */
    public synchronized void reload(File gatewayDir) {
        List<SecurityPolicy> list = new ArrayList<>();
        if (gatewayDir != null && gatewayDir.isDirectory()) {
            ConfigurationSection https = GatewayConfig.loadYml(new File(gatewayDir, "https.yml"));
            String httpsHost = https == null ? null : https.getString("host", "");

            File policiesDir = new File(gatewayDir, "policies");
            File[] files = policiesDir.isDirectory() ? policiesDir.listFiles((d, n) -> n.endsWith(".yml")) : null;
            if (files != null) {
                Arrays.sort(files, Comparator.comparing(File::getName));
                for (File f : files) {
                    String name = f.getName().substring(0, f.getName().length() - 4);
                    Supplier<SecurityPolicy> factory = REGISTRY.get(name);
                    if (factory == null) {
                        log.warning("[HTTP-Over-MC] 忽略未注册的策略文件: " + f.getName()
                                + "（如需启用请在 GatewayFilter.REGISTRY 注册对应实现）");
                        continue;
                    }
                    SecurityPolicy p = factory.get();
                    p.reload(GatewayConfig.loadYml(f));
                    if (p instanceof TlsPolicy && httpsHost != null && !httpsHost.isEmpty()) {
                        ((TlsPolicy) p).setHost(httpsHost);
                    }
                    if (p.isEnabled()) list.add(p);
                }
            }
        }
        list.sort(Comparator.comparingInt(SecurityPolicy::order));
        policies = list;
        log.info("[HTTP-Over-MC] 网关策略链已加载：" + (list.isEmpty() ? "无启用策略" : describe(list)));
    }

    private static String describe(List<SecurityPolicy> list) {
        StringBuilder sb = new StringBuilder();
        for (SecurityPolicy p : list) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(p.name()).append("(order=").append(p.order()).append(')');
        }
        return sb.toString();
    }

    /** 遍历策略链；返回第一个 DENY 或 ALLOW。策略执行异常按拒绝处理（fail-closed）。 */
    public PolicyResult filter(GatewayContext ctx) {
        for (SecurityPolicy p : policies) {
            if (!p.isEnabled() || !p.appliesTo(ctx)) continue;
            try {
                PolicyResult r = p.check(ctx);
                if (r != null && !r.isAllow()) return r;
            } catch (Exception e) {
                log.log(Level.WARNING, "[HTTP-Over-MC] 策略 " + p.name() + " 执行异常，按拒绝处理: " + e, e);
                return PolicyResult.deny(500, "Internal Server Error: policy " + p.name());
            }
        }
        return PolicyResult.ALLOW;
    }

    public List<SecurityPolicy> getPolicies() {
        return policies;
    }
}
