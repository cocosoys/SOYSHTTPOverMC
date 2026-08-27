package com.github.cocosoys.mc.soyshttpovermc.web;

import com.github.cocosoys.mc.soyshttpovermc.web.proto.FrameProto;

import com.google.protobuf.ByteString;

import lombok.CustomLog;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * CORS 声明注册中心：为某路径前缀声明 {@code Access-Control-Allow-*}。
 * <ul>
 *   <li>命中配置且请求为 {@code OPTIONS} 预检 → 直接 204 + CORS 头（短路，不进入业务）；</li>
 *   <li>命中配置的普通请求 → 响应附加 CORS 头。</li>
 * </ul>
 * 经门面 {@code api.getWebPage().registerCors(...)} 注册；卸载随所属插件。
 *
 * <p><b>安全防护</b>：{@code origin=*} 与 {@code credentials=true} 同时配置会被浏览器拒绝
 * （W3C CORS 规范禁止通配源 + 凭据），属于典型配置陷阱。本注册中心在 {@link #register} 时
 * 检测该组合并<b>拒绝登记</b>，同时打印告警，避免运维误配后静默失效。</p>
 */
@CustomLog
public class CorsRegistry {

    /** CORS 声明条目。 */
    public static final class CorsEntry {
        public final String ownerPlugin;
        public final String pathPrefix;
        public final String origin;       // 如 * 或 https://example.com（支持逗号分隔多源）
        public final String methods;      // 如 GET,POST,PUT,DELETE,OPTIONS
        public final String headers;      // 如 Content-Type,Authorization（可为空=*）
        public final boolean credentials; // Access-Control-Allow-Credentials

        CorsEntry(String ownerPlugin, String pathPrefix, String origin, String methods,
                  String headers, boolean credentials) {
            this.ownerPlugin = ownerPlugin;
            this.pathPrefix = pathPrefix;
            this.origin = origin == null ? "*" : origin;
            this.methods = methods == null ? "GET,POST,PUT,DELETE,OPTIONS" : methods;
            this.headers = headers;
            this.credentials = credentials;
        }
    }

    private final java.util.List<CorsEntry> entries = new CopyOnWriteArrayList<>();

    /**
     * 注册 CORS 声明（pathPrefix 为空或 "/" 表示全局）。
     *
     * <p><b>拒绝危险组合</b>：当 origin 为 {@code *}（或为空回退到 {@code *}）且 credentials=true 时，
     * 浏览器会因 W3C CORS 规范禁止通配源 + 凭据而静默拒绝带 Cookie 的请求，导致凭据站点 CSRF
     * 防护形同虚设同时正常登录用户也无法访问。本方法检测该组合后<b>不登记</b>并打印告警。
     * 如确需凭据，请指定具体 origin（如 {@code https://example.com}）。</p>
     */
    public void register(String ownerPlugin, String pathPrefix, String origin, String methods,
                         String headers, boolean credentials) {
        String resolvedOrigin = (origin == null || origin.trim().isEmpty()) ? "*" : origin.trim();
        if (credentials && "*".equals(resolvedOrigin)) {
            log.warnT("log.cors.dangerous-combo-rejected",
                    "拒绝登记危险 CORS 组合: owner={0} prefix={1} —— origin=* 与 credentials=true 同时设置"
                            + " 会被浏览器拒绝（W3C CORS 禁止通配源 + 凭据）。如需凭据请指定具体 origin。"
                            + " 本次登记已忽略。",
                    ownerPlugin == null ? "?" : ownerPlugin,
                    pathPrefix == null ? "/" : pathPrefix);
            return;
        }
        String p = (pathPrefix == null || pathPrefix.isEmpty()) ? "/" : pathPrefix;
        entries.add(new CorsEntry(ownerPlugin, p, resolvedOrigin, methods, headers, credentials));
    }

    /** 卸载指定插件登记的全部 CORS 声明。 */
    public void unregisterPlugin(String pluginName) {
        if (pluginName == null) return;
        entries.removeIf(e -> pluginName.equals(e.ownerPlugin));
    }

    /** 按请求路径匹配 CORS 声明（最长前缀优先；无命中返回 null）。 */
    public CorsEntry match(String path) {
        if (path == null || entries.isEmpty()) return null;
        CorsEntry best = null;
        int bestLen = -1;
        for (CorsEntry e : entries) {
            String p = e.pathPrefix;
            if ("/".equals(p)) p = "";
            if (path.startsWith(p) && p.length() > bestLen) {
                best = e;
                bestLen = p.length();
            }
        }
        return best;
    }

    /** 构造 CORS 响应头（不带 Access-Control-Allow-Methods 用于普通请求）。 */
    private Map<String, String> headers(CorsEntry e, boolean preflight) {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("Access-Control-Allow-Origin", e.origin);
        if (e.credentials) h.put("Access-Control-Allow-Credentials", "true");
        if (preflight) {
            h.put("Access-Control-Allow-Methods", e.methods);
            String hdrs = e.headers == null || e.headers.trim().isEmpty() ? "*" : e.headers;
            h.put("Access-Control-Allow-Headers", hdrs);
            h.put("Access-Control-Max-Age", "600");
        }
        return h;
    }

    /** OPTIONS 预检短路响应（204 + CORS 头；204 无 body，Content-Type 声明统一为 JSON 无 text/plain 例外）。 */
    public FrameProto.HttpResponseFrame preflight(CorsEntry e) {
        return frame(204, MimeTypes.forExt("json"), new byte[0], headers(e, true));
    }

    /** 把 CORS 头附加到既有响应帧（不覆盖已有同名字段之外的逻辑，直接 put 进 headers map）。 */
    public FrameProto.HttpResponseFrame attach(CorsEntry e, FrameProto.HttpResponseFrame resp) {
        Map<String, String> cors = headers(e, false);
        FrameProto.HttpResponseFrame.Builder b = resp.toBuilder();
        for (Map.Entry<String, String> x : cors.entrySet()) {
            b.putHeaders(x.getKey(), x.getValue());
        }
        return b.build();
    }

    private static FrameProto.HttpResponseFrame frame(int status, String ct, byte[] body, Map<String, String> headers) {
        FrameProto.HttpResponseFrame.Builder b = FrameProto.HttpResponseFrame.newBuilder()
                .setStatusCode(status)
                .putHeaders("Content-Type", ct)
                .setBody(ByteString.copyFrom(body))
                .setFragmentIndex(0)
                .setTotalFragments(1);
        for (Map.Entry<String, String> h : headers.entrySet()) {
            b.putHeaders(h.getKey(), h.getValue());
        }
        return b.build();
    }
}
