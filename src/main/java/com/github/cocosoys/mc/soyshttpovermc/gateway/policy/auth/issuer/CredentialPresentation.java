package com.github.cocosoys.mc.soyshttpovermc.gateway.policy.auth.issuer;

import com.github.cocosoys.mc.soyshttpovermc.api.event.GatewayAccessDeniedEvent;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;

/**
 * 客户端在请求中提交的凭证表示（从 X-API-Key / Authorization / Cookie 头解析而来）。
 * 供安全策略与凭证颁发器（{@link CredentialIssuer}）校验使用。
 * 实现 Serializable，便于事件（如 {@link GatewayAccessDeniedEvent}）
 * 携带/记录凭证快照。
 */
public class CredentialPresentation implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String apiKey;      // X-API-Key 头值（可能为 null）
    private final String bearer;      // Authorization: Bearer <token>
    private final String basicUser;   // Authorization: Basic 用户名
    private final String basicPass;   // Authorization: Basic 密码
    private final Map<String, String> cookies; // Cookie 头解析结果

    public CredentialPresentation(String apiKey, String bearer,
                                  String basicUser, String basicPass,
                                  Map<String, String> cookies) {
        this.apiKey = apiKey;
        this.bearer = bearer;
        this.basicUser = basicUser;
        this.basicPass = basicPass;
        this.cookies = cookies == null ? Collections.<String, String>emptyMap() : cookies;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getBearer() {
        return bearer;
    }

    public String getBasicUser() {
        return basicUser;
    }

    public String getBasicPass() {
        return basicPass;
    }

    public Map<String, String> getCookies() {
        return cookies;
    }

    /** 大小写不敏感读取 cookie 值 */
    public String getCookie(String name) {
        if (name == null) return null;
        for (Map.Entry<String, String> e : cookies.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) return e.getValue();
        }
        return null;
    }

    /** 是否携带了任一凭证形态（X-API-Key / Bearer / Basic / Cookie）；无头解析出的空对象返回 false。 */
    public boolean hasAnyCredential() {
        return (apiKey != null && !apiKey.isEmpty())
                || (bearer != null && !bearer.isEmpty())
                || (basicUser != null && !basicUser.isEmpty())
                || !cookies.isEmpty();
    }
}
