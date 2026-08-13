package soys.soyshttpovermc.gateway;

import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;

/**
 * TLS 强制策略：明文 HTTP 请求 → 426 Upgrade Required，Location 指向同端口 https
 * （25564 三协议端口上，TLS 在嗅探器内就地升级，无需独立端口）。
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

    /** 由 GatewayFilter 用 https.host 覆盖（与证书主机名保持一致） */
    public void setHost(String h) {
        if (h != null && !h.isEmpty()) host = h;
    }

    @Override
    public PolicyResult check(GatewayContext ctx) {
        if (ctx.isTls()) return PolicyResult.ALLOW;
        String loc = "https://" + host + ctx.getPath();
        Map<String, String> h = new HashMap<>();
        h.put("Location", loc);
        return PolicyResult.deny(426, "Upgrade Required: please use HTTPS (" + loc + ")", h);
    }
}
