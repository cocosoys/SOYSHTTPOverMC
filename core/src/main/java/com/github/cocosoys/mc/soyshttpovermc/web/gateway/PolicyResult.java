package com.github.cocosoys.mc.soyshttpovermc.web.gateway;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

/**
 * 策略判定结果：ALLOW 放行，或 DENY(状态码 + 响应体 + 附加响应头)。
 */
public final class PolicyResult {

    public static final PolicyResult ALLOW = new PolicyResult(true, 0, "", null);

    private final boolean allow;
    private final int statusCode;
    private final String body;
    private final Map<String, String> headers;

    private PolicyResult(boolean allow, int statusCode, String body, Map<String, String> headers) {
        this.allow = allow;
        this.statusCode = statusCode;
        this.body = body == null ? "" : body;
        this.headers = headers;
    }

    public static PolicyResult deny(int statusCode, String body) {
        return new PolicyResult(false, statusCode, body, null);
    }

    /**
     * 带附加响应头（如 429 的 Retry-After、426 的 Location）
     */
    public static PolicyResult deny(int statusCode, String body, Map<String, String> headers) {
        return new PolicyResult(false, statusCode, body, headers);
    }

    public boolean isAllow() {
        return allow;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getBody() {
        return body;
    }

    public byte[] getBodyBytes() {
        return body.getBytes(StandardCharsets.UTF_8);
    }

    public Map<String, String> getHeaders() {
        return headers == null ? Collections.<String, String>emptyMap() : headers;
    }
}
