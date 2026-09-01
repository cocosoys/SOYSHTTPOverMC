package com.github.cocosoys.mc.soyshttpovermc.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 注解式 API 响应控制（仿 Spring MVC 的 {@code ResponseEntity}）：
 * 允许端点声明<b>自定义 HTTP 状态码</b>与<b>附加响应头</b>（302 跳转、Set-Cookie、
 * 401/400/503 等真实错误状态码），而默认的 {@link AjaxResult} 返回由网关统一组装为
 * HTTP 200 + JSON 信封（RuoYi 风格）。
 *
 * <p>由 {@code ApiRegistry.dispatch} 原样透传，{@code WebFrontendHandler} 组装帧时
 * 使用 {@code statusCode} 与 {@code headers} 构造真实 HTTP 响应（body 恒为 JSON 信封，
 * 无 text/plain 例外）。</p>
 */
public class ApiResponse {

    private final int statusCode;
    private final AjaxResult body;
    private final Map<String, String> headers;

    private ApiResponse(int statusCode, AjaxResult body, Map<String, String> headers) {
        this.statusCode = statusCode;
        this.body = body == null ? new AjaxResult(statusCode, "") : body;
        this.headers = headers == null ? Collections.<String, String>emptyMap()
                : Collections.unmodifiableMap(new HashMap<>(headers));
    }

    /**
     * 成功（HTTP 200 + JSON 信封）。
     */
    public static ApiResponse ok(AjaxResult body) {
        return new ApiResponse(200, body, null);
    }

    /**
     * 自定义状态码 + JSON 信封。
     */
    public static ApiResponse status(int statusCode, AjaxResult body) {
        return new ApiResponse(statusCode, body, null);
    }

    /**
     * 自定义状态码 + JSON 信封 + 附加响应头（如 Set-Cookie）。
     */
    public static ApiResponse status(int statusCode, AjaxResult body, Map<String, String> headers) {
        return new ApiResponse(statusCode, body, headers);
    }

    /**
     * 302 跳转（Location 头 + JSON 提示体；浏览器原生跳转）。
     */
    public static ApiResponse redirect(String location) {
        Map<String, String> h = new HashMap<>();
        h.put("Location", location == null ? "/" : location);
        return new ApiResponse(302, new AjaxResult(302, "Redirecting to " + (location == null ? "/" : location)), h);
    }

    /**
     * 错误响应：真实 HTTP 状态码 + {@code {code,msg,data:null}} 信封。
     */
    public static ApiResponse jsonError(int code, String msg) {
        return new ApiResponse(code, AjaxResult.error(code, msg), null);
    }

    /**
     * 错误响应（i18n 版，key 首参，命中语言表翻译，未命中回退 fallback）：{@code jsonErrorT(400, "ajax.auth.key", "兜底 {0}", v)}。
     */
    public static ApiResponse jsonErrorT(int code, String i18nKey, String fallback, Object... args) {
        return new ApiResponse(code, AjaxResult.errorT(code, i18nKey, fallback, args), null);
    }

    public int statusCode() {
        return statusCode;
    }

    public AjaxResult body() {
        return body;
    }

    /**
     * 附加响应头（不可变；空 map 表示无）。
     */
    public Map<String, String> headers() {
        return headers;
    }
}
