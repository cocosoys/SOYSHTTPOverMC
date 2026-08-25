package com.github.cocosoys.mc.soyshttpovermc.gateway;

import org.bukkit.configuration.ConfigurationSection;

/**
 * 安全策略抽象基类（需求：安全能力制作为抽象类，便于未来扩展新策略、调控策略开关）。
 *
 * <p>实现一个策略 = 继承本类 + 实现 {@link #check}，再在 {@link GatewayFilter#reload} 里注册即可，
 * 无需改动链执行逻辑。每个策略独立可开关（config 的 gateway.policies.&lt;name&gt;.enabled），
 * 通过 /soyshttp reload 热重载，无需重启服务器。
 */
public abstract class SecurityPolicy {

    private volatile boolean enabled = false;

    /** 策略唯一标识（与 config 中 policies.&lt;name&gt; 对应，仅用于日志/调试） */
    public abstract String name();

    /** 链中的执行顺序（越小越靠前；IP 白名单 10、API Key 20、限流 30、TLS 5） */
    public int order() {
        return 50;
    }

    public final boolean isEnabled() {
        return enabled;
    }

    public final void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** 是否对该请求生效（默认全部生效；如 API Key 策略可按路径过滤） */
    public boolean appliesTo(GatewayContext ctx) {
        return true;
    }

    /** 执行策略判定：返回 ALLOW 或 DENY（DENY 将短路整条链）。 */
    public abstract PolicyResult check(GatewayContext ctx);

    /** 从 config 重载参数与开关（热重载时调用）。 */
    public void reload(ConfigurationSection cfg) {
        setEnabled(cfg != null && cfg.getBoolean("enabled", false));
    }
}
