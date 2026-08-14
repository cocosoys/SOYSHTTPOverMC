package soys.soyshttpovermc.gateway;

import soys.soyshttpovermc.log.LogKit;

import org.bukkit.configuration.ConfigurationSection;
import soys.soyshttpovermc.gateway.policy.*;
import soys.soyshttpovermc.gateway.policy.auth.AuthPolicy;
import soys.soyshttpovermc.gateway.policy.auth.issuer.CredentialIssuer;
import soys.soyshttpovermc.gateway.policy.auth.issuer.SessionTokenIssuer;
import soys.soyshttpovermc.gateway.policy.tls.TlsPolicy;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * 安全策略链执行器（nginx 式网关的核心）：
 * 按 order 升序遍历已启用的策略，任一 DENY 立即短路返回。
 *
 * <p>配置采用独立文件布局（便于未来接入更多策略/颁发器）：
 * <pre>
 *   gateway/config.yml               总开关 enabled
 *   gateway/https.yml                HTTPS 设置（host 会覆盖 tls 策略的 host）
 *   gateway/policies/&lt;name&gt;.yml     每个策略一个文件，文件名即策略名
 *   gateway/issuers/&lt;name&gt;.yml      每个凭证颁发器一个文件（注入给 auth 策略）
 * </pre>
 * 新增策略 = 1) 继承 {@link SecurityPolicy}；2) {@link #REGISTRY} 注册一行；3) policies/ 放 yml。
 * 新增凭证颁发器（登录插件接入）= 1) 继承 {@link CredentialIssuer}；2) {@link #ISSUER_REGISTRY}
 * 注册一行；3) issuers/ 放 yml。均无需改动链执行逻辑，/soyshttp reload 热重载。
 */
public class GatewayFilter {

    /** 策略注册表：策略名（= policies 目录下的文件名）→ 工厂。新增策略在此注册。 */
    private static final Map<String, Supplier<SecurityPolicy>> REGISTRY = new LinkedHashMap<>();

    static {
        REGISTRY.put("tls", TlsPolicy::new);
        REGISTRY.put("auth", AuthPolicy::new);
//        REGISTRY.put("api-key", AuthPolicy::new); // 兼容旧名（旧 api-key.yml 仍可加载）
        REGISTRY.put("ip-allowlist", IpAllowlistPolicy::new);
        REGISTRY.put("rate-limit", RateLimitPolicy::new);
    }

    /** 凭证颁发器注册表：颁发器名（= issuers 目录下的文件名）→ 工厂。登录插件在此注册。 */
    private static final Map<String, Supplier<CredentialIssuer>> ISSUER_REGISTRY = new LinkedHashMap<>();

    static {
        ISSUER_REGISTRY.put("session-token", SessionTokenIssuer::new);
    }

    /**
     * 注册一个凭证颁发器工厂（登录插件接入点，供 {@code SoysHttpOverMcApi.registerCredentialIssuer} 委托）。
     * 注册名 = gateway/issuers/&lt;name&gt;.yml 的文件名；之后在对应 yml 写 {@code enabled: true} 即启用。
     * 注意：仅注册工厂不自动启用，需配合 issuers 目录下的 yml。
     */
    public static void registerIssuer(String name, Supplier<CredentialIssuer> factory) {
        if (name == null || name.isEmpty() || factory == null) return;
        ISSUER_REGISTRY.put(name, factory);
    }

    private final Logger log;
    private volatile List<SecurityPolicy> policies = new ArrayList<>();
    private volatile List<CredentialIssuer> issuers = new ArrayList<>();
    /** 网关统一的 API 前缀（gateway/config.yml api-prefix，默认 /api；始终生效） */
    private volatile String apiPrefix = "/api";

    public GatewayFilter(Logger log) {
        this.log = log;
    }

    /** 网关统一的 API 前缀（auth 策略匹配 exempt/paths 时自动兼容逻辑路径）。 */
    public String getApiPrefix() {
        return apiPrefix;
    }

    /** 从 gateway/ 目录重建策略链与颁发器（启动与 /soyshttp reload 均走这里）。 */
    public synchronized void reload(File gatewayDir) {
        List<CredentialIssuer> issuerList = loadIssuers(gatewayDir);
        this.issuers = issuerList;

        // 读取网关全局 API 前缀（config.yml api-prefix，默认 /api，始终生效）
        ConfigurationSection cfg = GatewayConfig.loadYml(new File(gatewayDir, "config.yml"));
        this.apiPrefix = cfg == null ? "/api" : cfg.getString("api-prefix", "/api");

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
                        LogKit.warn("[HTTP-Over-MC] 忽略未注册的策略文件: " + f.getName()
                                + "（如需启用请在 GatewayFilter.REGISTRY 注册对应实现）");
                        continue;
                    }
                    SecurityPolicy p = factory.get();
                    p.reload(GatewayConfig.loadYml(f));
                    if (p instanceof TlsPolicy && httpsHost != null && !httpsHost.isEmpty()) {
                        ((TlsPolicy) p).setHost(httpsHost);
                    }
                    if (p instanceof AuthPolicy) {
                        ((AuthPolicy) p).setIssuers(issuerList);
                        ((AuthPolicy) p).setApiPrefix(apiPrefix);
                    }
                    if (p.isEnabled()) list.add(p);
                }
            }
        }
        list.sort(Comparator.comparingInt(SecurityPolicy::order));
        policies = list;
        LogKit.info("[HTTP-Over-MC] 网关策略链已加载：" + (list.isEmpty() ? "无启用策略" : describe(list))
                + (issuerList.isEmpty() ? "" : " | 颁发器: " + describeIssuers(issuerList))
                + " | api-prefix=" + apiPrefix);
        if (LogKit.isDebugEnabled()) {
            StringBuilder sb = new StringBuilder("[HTTP-Over-MC] 策略明细: ");
            for (SecurityPolicy p : list) {
                sb.append(p.name()).append('(').append(p.order()).append(p.isEnabled() ? ",enabled" : ",disabled").append(") ");
            }
            LogKit.debug(sb.toString().trim());
        }
    }

    /** 扫描 gateway/issuers/*.yml，实例化并启用注册过的颁发器。 */
    private List<CredentialIssuer> loadIssuers(File gatewayDir) {
        List<CredentialIssuer> list = new ArrayList<>();
        if (gatewayDir == null || !gatewayDir.isDirectory()) return list;
        File dir = new File(gatewayDir, "issuers");
        File[] files = dir.isDirectory() ? dir.listFiles((d, n) -> n.endsWith(".yml")) : null;
        if (files == null) return list;
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File f : files) {
            String name = f.getName().substring(0, f.getName().length() - 4);
            Supplier<CredentialIssuer> factory = ISSUER_REGISTRY.get(name);
            if (factory == null) {
                LogKit.warn("[HTTP-Over-MC] 忽略未注册的颁发器文件: " + f.getName()
                        + "（如需启用请在 GatewayFilter.ISSUER_REGISTRY 注册对应实现）");
                continue;
            }
            CredentialIssuer issuer = factory.get();
            issuer.reload(GatewayConfig.loadYml(f));
            if (issuer.isEnabled()) list.add(issuer);
        }
        return list;
    }

    private static String describe(List<SecurityPolicy> list) {
        StringBuilder sb = new StringBuilder();
        for (SecurityPolicy p : list) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(p.name()).append("(order=").append(p.order()).append(')');
        }
        return sb.toString();
    }

    private static String describeIssuers(List<CredentialIssuer> list) {
        StringBuilder sb = new StringBuilder();
        for (CredentialIssuer i : list) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(i.name());
        }
        return sb.toString();
    }

    /** 遍历策略链；返回第一个 DENY 或 ALLOW。策略执行异常按拒绝处理（fail-closed）。 */
    public PolicyResult filter(GatewayContext ctx) {
        return filterDetailed(ctx).result;
    }

    /** 与 {@link #filter} 相同，但额外返回拒绝该请求的策略（供事件/日志使用）。 */
    public Outcome filterDetailed(GatewayContext ctx) {
        for (SecurityPolicy p : policies) {
            if (!p.isEnabled() || !p.appliesTo(ctx)) continue;
            try {
                PolicyResult r = p.check(ctx);
                if (r != null && !r.isAllow()) return new Outcome(p, r);
            } catch (Exception e) {
                LogKit.warn("[HTTP-Over-MC] 策略 " + p.name() + " 执行异常，按拒绝处理: " + e, e);
                return new Outcome(p, PolicyResult.deny(500, "Internal Server Error: policy " + p.name()));
            }
        }
        return new Outcome(null, PolicyResult.ALLOW);
    }

    /** 判定结果 + 拒绝该请求的策略（null=放行） */
    public static final class Outcome {
        public final SecurityPolicy policy;
        public final PolicyResult result;

        Outcome(SecurityPolicy policy, PolicyResult result) {
            this.policy = policy;
            this.result = result;
        }
    }

    public List<SecurityPolicy> getPolicies() {
        return policies;
    }

    /**
     * 解析请求头中的凭证为 {@link Credential}（权限控制抽象载体）。
     * 供 TLS 策略在链路最前判断"是否携带有效 X-API-Key 可旁路 HTTPS 强制升级"复用，
     * 与 AuthPolicy 共用同一校验逻辑（静态 keys + 启用颁发器）。
     * 未启用 auth 策略时返回 null（无有效凭证）。
     */
    public Credential resolveCredential(java.util.Map<String, String> headers) {
        for (SecurityPolicy p : policies) {
            if (p instanceof AuthPolicy) {
                return ((AuthPolicy) p).resolveFromHeaders(headers);
            }
        }
        return null;
    }

    /** auth 鉴权策略是否启用（决定注解式 API 是否自动加 /api 全局前缀） */
    public boolean isAuthEnabled() {
        for (SecurityPolicy p : policies) {
            if (p instanceof AuthPolicy && p.isEnabled()) return true;
        }
        return false;
    }

    /** 已启用的凭证颁发器（供 /soyshttp key 下发命令使用） */
    public List<CredentialIssuer> getIssuers() {
        return issuers;
    }
}
