package com.github.cocosoys.mc.soyshttpovermc.web;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

/**
 * 对外真实 HTTP 请求的响应载体（门面 sendHttp/sendGet/sendPost 的返回值）。
 * 仅承载状态码 / 响应头 / 原始响应体，便于插件自行解析（JSON / 二进制等）。
 */
public class HttpResponse {

    private final int status;
    private final Map<String, String> headers;
    private final byte[] body;

    public HttpResponse(int status, Map<String, String> headers, byte[] body) {
        this.status = status;
        this.headers = headers == null ? Collections.<String, String>emptyMap() : headers;
        this.body = body == null ? new byte[0] : body;
    }

    /**
     * HTTP 状态码（如 200 / 404 / 500）
     */
    public int getStatus() {
        return status;
    }

    /**
     * 响应头（小写 key 归一化后的快照）
     */
    public Map<String, String> getHeaders() {
        return headers;
    }

    /**
     * 原始响应体字节
     */
    public byte[] getBody() {
        return body;
    }

    /**
     * 响应体按 UTF-8 解码为字符串（便于 JSON / 文本场景）
     */
    public String bodyAsString() {
        return new String(body, StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return "HttpResponse{status=" + status + ", bodyLen=" + body.length + "}";
    }
}
