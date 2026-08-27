package com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.issuer;

import java.io.Serializable;

/**
 * 下发（签发）的凭证：一次签发可同时承载 X-API-Key / Authorization Bearer / Cookie 三种形态。
 * 具体填充哪些字段由颁发器决定（如会话令牌三种形态都是同一个 token）。
 * 实现 Serializable，便于事件/日志/跨进程传递时对凭证实体进行序列化控制。
 */
public class IssuedCredential implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String apiKey;      // 可作为 X-API-Key 头
    private final String bearer;      // 可作为 Authorization: Bearer
    private final String cookieName;  // cookie 名（null 表示不签发 cookie）
    private final String cookieValue; // cookie 值

    public IssuedCredential(String apiKey, String bearer, String cookieName, String cookieValue) {
        this.apiKey = apiKey;
        this.bearer = bearer;
        this.cookieName = cookieName;
        this.cookieValue = cookieValue;
    }

    /** 便捷工厂：同一个 token 同时作为 X-API-Key / Bearer / Cookie 值下发。 */
    public static IssuedCredential ofToken(String token, String cookieName) {
        return new IssuedCredential(token, token, cookieName, token);
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getBearer() {
        return bearer;
    }

    public String getCookieName() {
        return cookieName;
    }

    public String getCookieValue() {
        return cookieValue;
    }
}
