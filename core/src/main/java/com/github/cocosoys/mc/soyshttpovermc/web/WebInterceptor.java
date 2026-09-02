package com.github.cocosoys.mc.soyshttpovermc.web;

import com.github.cocosoys.mc.soyshttpovermc.util.AjaxResult;

import java.util.HashMap;
import java.util.Map;

/**
 * 请求级拦截 SPI（GatewayFilter 之后、业务路由之前）：
 * 允许第三方插件在请求进入注解式 API / 静态资源前，<b>改写 path、注入/改写请求头、
 * 或按自定义规则短路返回</b>（SSO 校验、维护页、灰度分流、自定义鉴权等）。
 *
 * <p>用法：实现本接口，经 {@code api.getExtension().registerWebInterceptor(interceptor)} 注册。
 * 多个拦截器按注册顺序执行；任一拦截器返回 {@link #stop} 即短路（后续拦截器与业务路由不再执行）。
 *
 * <p>注意：本拦截器位于网关策略链（TLS/IP 白名单/Auth/限流）<b>之后</b>，
 * 无法绕过网关级安全策略；改写的 path 会继续参与 WebFrontendHandler 的后续路由。
 */
public interface WebInterceptor {

    /**
     * 拦截器唯一名称（日志/调试用）。
     */
    String name();

    /**
     * 拦截一次请求。
     *
     * @param ctx 可变上下文：{@code path}/{@code headers} 可直接改写（后续路由使用改写后的值）
     * @return {@link Outcome#pass()} 放行继续；{@link Outcome#stop(status, contentType, body, headers)} 短路返回
     */
    Outcome intercept(WebInterceptContext ctx) throws Exception;

    /**
     * 拦截结果。
     */
    final class Outcome {
        private final int status;
        private final String contentType;
        private final byte[] body;
        private final Map<String, String> headers;

        private Outcome(int status, String contentType, byte[] body, Map<String, String> headers) {
            this.status = status;
            this.contentType = contentType;
            this.body = body;
            this.headers = headers;
        }

        /**
         * 放行（继续后续处理）。
         */
        public static Outcome pass() {
            return new Outcome(0, null, null, null);
        }

        /**
         * 短路返回：status=0 表示放行；非 0 为直接返回的 HTTP 状态码。
         */
        public static Outcome stop(int status, String contentType, byte[] body) {
            return new Outcome(status, contentType, body, null);
        }

        /**
         * 短路返回并附加响应头。
         */
        public static Outcome stop(int status, String contentType, byte[] body, Map<String, String> headers) {
            return new Outcome(status, contentType, body, headers);
        }

        /**
         * 便捷：短路返回纯文本（兼容旧用法；新代码建议用 {@link #stopJson} 保持统一 JSON 信封）。
         */
        public static Outcome stopText(int status, String text) {
            return new Outcome(status, MimeTypes.forExt("txt"),
                    text == null ? new byte[0] : text.getBytes(java.nio.charset.StandardCharsets.UTF_8), null);
        }

        /**
         * 便捷：短路返回 JSON 信封（{@code {code,msg,data}}，与全局业务响应格式一致）。
         */
        public static Outcome stopJson(int status, AjaxResult body) {
            return new Outcome(status, MimeTypes.forExt("json"),
                    (body == null ? AjaxResult.error(status, "") : body).toJson()
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8), null);
        }

        /**
         * 便捷：短路返回 JSON 错误信封（真实状态码 + 文案）。
         */
        public static Outcome stopJson(int status, String msg) {
            return stopJson(status, AjaxResult.error(status, msg));
        }

        public boolean isStop() {
            return status > 0;
        }

        public int status() {
            return status;
        }

        public String contentType() {
            return contentType;
        }

        public byte[] body() {
            return body;
        }

        public Map<String, String> headers() {
            return headers == null ? new HashMap<>() : headers;
        }
    }

    /**
     * 可变拦截上下文：path 与 headers 可直接改写（改写后继续后续路由）。
     */
    final class WebInterceptContext {
        private String method;
        private String path;
        private final Map<String, String> headers;
        private final byte[] body;
        private final String ip;
        private final boolean tls;

        public WebInterceptContext(String method, String path, Map<String, String> headers,
                                   byte[] body, String ip, boolean tls) {
            this.method = method;
            this.path = path;
            this.headers = headers;
            this.body = body;
            this.ip = ip;
            this.tls = tls;
        }

        public String method() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }

        /**
         * 当前路径（可改写；后续路由使用改写后的值）。
         */
        public String path() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        /**
         * 请求头（可变：可 put 注入/改写，业务层透传可见）。
         */
        public Map<String, String> headers() {
            return headers;
        }

        public byte[] body() {
            return body;
        }

        public String ip() {
            return ip;
        }

        public boolean tls() {
            return tls;
        }
    }
}
