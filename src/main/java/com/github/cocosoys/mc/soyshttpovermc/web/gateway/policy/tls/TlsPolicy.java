package com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.tls;

import com.github.cocosoys.mc.soyshttpovermc.web.gateway.GatewayContext;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.PolicyResult;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.SecurityPolicy;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;

/**
 * TLS 强制策略：明文 HTTP 请求 → 426 Upgrade Required，Location 指向同端口 https
 * （在 MC 端口上，TLS 在嗅探器内就地升级，无需独立端口）。
 */
public class TlsPolicy extends SecurityPolicy {

    private String host = "127.0.0.1";

    @Override
    public String name() {
        return "tls";
    }

    @Override
    public int order() {
        return 5;
    }

    @Override
    public void reload(ConfigurationSection cfg) {
        super.reload(cfg);
        if (cfg != null) {
            String h = cfg.getString("host", "");
            if (!h.isEmpty()) host = h;
        }
    }

    /**
     * 由 GatewayFilter 用 https.host 覆盖（与证书主机名保持一致）
     */
    public void setHost(String h) {
        if (h != null && !h.isEmpty()) host = h;
    }

    @Override
    public PolicyResult check(GatewayContext ctx) {
        if (ctx.isTls()) return PolicyResult.ALLOW;
        // 携带有效 X-API-Key（或任一有效凭证）的请求：允许明文 HTTP 直接访问，旁路 HTTPS 强制升级。
        // 权限控制抽象：当前有效凭证 = 拥有全部权限；未来可在此加 hasPermission 细粒度条件。
        if (ctx.isAuthenticated()) return PolicyResult.ALLOW;
        // 优先用请求头 Host（含端口，如 127.0.0.1:25564），回退到配置的 host；
        // 否则非标准端口会丢失端口号，浏览器跳转到 https://127.0.0.1/（默认443）导致连接失败。
        String hostHeader = ctx.getHeader("Host");
        String target = (hostHeader != null && !hostHeader.isEmpty()) ? hostHeader : host;
        // Location 用原始路径（含 /server/<name>/ 跨服前缀），否则升级到 HTTPS 后会丢失目标子服
        String loc = "https://" + target + ctx.getRawPath();
        Map<String, String> h = new HashMap<>();
        h.put("Location", loc);
        return PolicyResult.deny(426, "Upgrade Required: please use HTTPS (" + loc + ")", h);
    }
}
