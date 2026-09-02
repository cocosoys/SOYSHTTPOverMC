package com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.issuer;

import com.github.cocosoys.mc.soyshttpovermc.web.gateway.GatewayFilter;
import org.bukkit.configuration.ConfigurationSection;

/**
 * 凭证颁发器抽象基类：用于"下发" X-API-KEY / Authorization / Cookie 三类凭证，并校验客户端回传的凭证。
 *
 * <p>设计目的：未来接入各类服务器登录插件时，登录插件在玩家认证成功后调用 {@link #issue}
 * 签发会话凭证交给客户端；网关侧 {@link #validate} 校验请求携带的凭证。接入步骤：
 * <ol>
 *   <li>继承本类，实现 {@link #issue} 与 {@link #validate}；</li>
 *   <li>在 {@link GatewayFilter#ISSUER_REGISTRY} 注册一行（策略名=文件名）；</li>
 *   <li>在 gateway/issuers/ 放一个 &lt;name&gt;.yml（enabled: true 启用）。</li>
 * </ol>
 * 之后 auth 策略会自动用启用的颁发器校验 X-API-Key / Bearer / Cookie 三种来源的凭证。
 */
public abstract class CredentialIssuer {

    private volatile boolean enabled = false;

    /**
     * 颁发器唯一标识（= gateway/issuers/&lt;name&gt;.yml 的文件名）
     */
    public abstract String name();

    public final boolean isEnabled() {
        return enabled;
    }

    public final void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 从配置重载参数与开关（热重载时调用）。
     */
    public void reload(ConfigurationSection cfg) {
        setEnabled(cfg != null && cfg.getBoolean("enabled", false));
    }

    /**
     * 下发凭证：为已认证主体（如玩家 UUID/用户名）签发一套凭证。
     * 登录插件在玩家登录成功后调用，把返回的 {@link IssuedCredential} 交给客户端。
     */
    public abstract IssuedCredential issue(String subject);

    /**
     * 校验请求中携带的凭证（X-API-Key / Bearer / Basic / Cookie）是否有效。
     */
    public abstract boolean validate(CredentialPresentation presented);

    /**
     * 由凭证表示解析绑定的主体（如玩家名）；默认返回 null（不绑定主体）。
     * 供 {@link PermissionService} 把请求凭证映射为"主体"以判定权限
     * （如会话令牌绑定玩家，则令牌拥有该玩家在游戏内的 Bukkit 权限）。
     */
    public String subjectOf(CredentialPresentation presented) {
        return null;
    }
}
