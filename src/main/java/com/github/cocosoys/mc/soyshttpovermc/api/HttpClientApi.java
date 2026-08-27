package com.github.cocosoys.mc.soyshttpovermc.api;

import com.github.cocosoys.mc.soyshttpovermc.web.HttpResponse;

import java.util.Map;

/**
 * 能力组 7：HTTP 请求（对外真实请求 + 对内回环调本插件 API + 环境自适配通用发送）。
 * 由 {@link SoysHttpOverMcApi#getHttpClient()} 跳转获取。
 */
public interface HttpClientApi {

    /** 对外发起真实 HTTP 请求（任意方法/URL/头/体），返回状态码+响应头+响应体；连接失败抛 {@code HttpClientException} */
    HttpResponse sendHttp(String method, String url, Map<String, String> headers, byte[] body);

    HttpResponse sendGet(String url);

    HttpResponse sendGet(String url, Map<String, String> headers);

    HttpResponse sendPost(String url, byte[] body);

    HttpResponse sendPost(String url, Map<String, String> headers, byte[] body);

    /** 对内回环：直接调用本插件自身已注册的 API（绕过网络，等价于本地分发）；分发异常抛 {@code HttpClientException} */
    Object callLocalApi(String method, String path, Map<String, String> headers, byte[] body);

    // ===== 环境自适配（通用发送，单服/群组服同一套代码） =====

    /**
     * 通用发送：自动补全前缀（{@link #resolveUrl}）后<b>本地分发</b>到本插件已注册的 API
     * （等价 {@code callLocalApi(method, resolveUrl(path), ...)}，不经网络/网关 TLS，
     * 注解层 {@code @ApiPublic/@ApiPermission} 判定照常生效）。
     * 返回处理器返回值（通常为 {@code AjaxResult}）；未命中路由返回 {@code null}。
     */
    Object sendApi(String method, String logicalPath, Map<String, String> headers, byte[] body);

    /**
     * 解析本服 HTTP 可访问的<b>完整路径</b>（环境自适配，自动补全三种前缀）：
     * <ul>
     *   <li><b>/api 前缀</b>：注解式 API 全局前缀（恒生效，与 auth 是否开启无关）；</li>
     *   <li><b>服务器前缀</b>：群组服自动补 {@code /server/&lt;本服名&gt;}（跨服路由）；独立服无此概念 → 留空，
     *       因此同一段代码可直接通用于单服务器与群组服；</li>
     *   <li><b>插件名前缀</b>：调用方为第三方插件时自动补 {@code /plugins/&lt;插件名&gt;}
     *       （沿调用栈识别；主插件自身调用不补）。</li>
     * </ul>
     * 例：第三方插件 Foo 调 {@code resolveUrl("/status")} →
     * 独立服 {@code /api/plugins/Foo/status}；群组服 {@code /server/lobby/api/plugins/Foo/status}。
     *
     * @param logicalPath 逻辑路径（如 {@code /status}、{@code /auth/login}，可带 query）；已带 /api 或 /plugins 前缀不重复补
     */
    String resolveUrl(String logicalPath);

    /** 群组服跨服前缀（如 {@code /server/lobby}）；独立服返回空串。 */
    String getServerPrefix();

    /** 注解式 API 全局前缀（如 {@code /api}）。 */
    String getApiPrefix();

    /** 网关 auth 是否开启（开启则调用受保护 API 需携带凭证；{@link #sendApi} 本地分发时由注解层 {@code @ApiPublic/@ApiPermission} 判定）。 */
    boolean isAuthEnabled();
}
