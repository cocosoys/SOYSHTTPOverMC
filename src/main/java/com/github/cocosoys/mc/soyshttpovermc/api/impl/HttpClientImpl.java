package com.github.cocosoys.mc.soyshttpovermc.api.impl;
import com.github.cocosoys.mc.soyshttpovermc.enums.ProxyPlatform;

import com.github.cocosoys.mc.soyshttpovermc.ApiRegistry;
import com.github.cocosoys.mc.soyshttpovermc.HttpResponse;
import com.github.cocosoys.mc.soyshttpovermc.HttpOverMcPlugin;
import com.github.cocosoys.mc.soyshttpovermc.api.HttpClientApi;
import com.github.cocosoys.mc.soyshttpovermc.exception.ExceptionBus;
import com.github.cocosoys.mc.soyshttpovermc.exception.HttpClientException;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 能力组 7：HTTP 请求（对外真实请求 + 对内回环调本插件 API + 环境自适配通用发送）。
 * 由 {@link SoysHttpOverMcApiImpl} 组合并对外暴露；实现 {@link HttpClientApi}。
 *
 * <p>环境自适配（{@link #resolveUrl}）所需的拓扑信息在构造时从 {@link HttpOverMcPlugin}
 * 注入：api 前缀、群组服名（独立服为空）、auth 开关，开发者无需关心部署形态。</p>
 */
public class HttpClientImpl implements HttpClientApi {

    private final ApiRegistry apiRegistry;
    private final Plugin hostPlugin;
    private final String apiPrefix;
    private final String serverPrefix;
    private final boolean authEnabled;

    public HttpClientImpl(HttpOverMcPlugin host, ApiRegistry apiRegistry) {
        this.hostPlugin = host;
        this.apiRegistry = apiRegistry;
        String p = apiRegistry == null ? "/api" : apiRegistry.getPathPrefix();
        this.apiPrefix = (p == null || p.trim().isEmpty()) ? "/api" : p.trim();
        boolean grouped = host.getProxyPlatform() != ProxyPlatform.STANDALONE;
        String sn = host.getServerName();
        this.serverPrefix = (grouped && sn != null && !sn.isEmpty()) ? "/server/" + sn : "";
        this.authEnabled = host.getGateway() != null && host.getGateway().isAuthEnabled();
    }

    // ===== 对外真实 HTTP =====

    @Override
    public HttpResponse sendHttp(String method, String url, Map<String, String> headers, byte[] body) {
        try {
            URL u = new URL(url);
            HttpURLConnection conn = (HttpURLConnection) u.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);
            conn.setInstanceFollowRedirects(true);
            if (headers != null) {
                for (Map.Entry<String, String> e : headers.entrySet()) {
                    if (e.getKey() != null && e.getValue() != null) conn.setRequestProperty(e.getKey(), e.getValue());
                }
            }
            boolean hasBody = body != null && body.length > 0;
            boolean writeBody = hasBody && !"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method);
            if (writeBody) {
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body);
                }
            }
            int status = conn.getResponseCode();
            Map<String, String> respHeaders = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> e : conn.getHeaderFields().entrySet()) {
                if (e.getKey() == null) continue;
                respHeaders.put(e.getKey().toLowerCase(), String.join(",", e.getValue()));
            }
            InputStream in = (status >= 400 && conn.getErrorStream() != null) ? conn.getErrorStream() : conn.getInputStream();
            byte[] respBody = readAll(in);
            conn.disconnect();
            return new HttpResponse(status, respHeaders, respBody);
        } catch (Exception ex) {
            // 对外请求失败属于明确异常：经总线结构化报错后抛出，便于调用方精准处理
            throw ExceptionBus.fire(new HttpClientException("E_SEND", "exception.httpclient.send-fail", "{0} {1} 失败: {2}", ex, method, url, ex.getMessage()));
        }
    }

    @Override
    public HttpResponse sendGet(String url) { return sendHttp("GET", url, null, null); }

    @Override
    public HttpResponse sendGet(String url, Map<String, String> headers) { return sendHttp("GET", url, headers, null); }

    @Override
    public HttpResponse sendPost(String url, byte[] body) { return sendHttp("POST", url, null, body); }

    @Override
    public HttpResponse sendPost(String url, Map<String, String> headers, byte[] body) { return sendHttp("POST", url, headers, body); }

    // ===== 本地回环 =====

    @Override
    public Object callLocalApi(String method, String path, Map<String, String> headers, byte[] body) {
        try {
            return apiRegistry.dispatch(method, path,
                    headers == null ? Collections.<String, String>emptyMap() : headers, body);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new HttpClientException("E_LOCAL", "exception.httpclient.local-call-fail", "本地回环调用失败({0} {1}): {2}", ex, method, path, ex.getMessage()));
        }
    }

    // ===== 环境自适配（通用发送） =====

    @Override
    public String resolveUrl(String logicalPath) {
        if (logicalPath == null || logicalPath.isEmpty()) return "/";
        String p = logicalPath.startsWith("/") ? logicalPath : "/" + logicalPath;
        StringBuilder sb = new StringBuilder();
        if (serverPrefix != null && !serverPrefix.isEmpty()) sb.append(serverPrefix);
        if (!p.startsWith(apiPrefix)) sb.append(apiPrefix);
        // 插件名前缀：调用方为第三方插件时补 /plugins/<插件名>（主插件自身调用不补；已带则不重复）
        String caller = detectCallerPlugin();
        if (caller != null && !caller.equals(hostPlugin.getName()) && !p.startsWith("/plugins/")) {
            sb.append("/plugins/").append(caller);
        }
        sb.append(p);
        return sb.toString();
    }

    @Override
    public String getServerPrefix() {
        return serverPrefix;
    }

    @Override
    public String getApiPrefix() {
        return apiPrefix;
    }

    @Override
    public boolean isAuthEnabled() {
        return authEnabled;
    }

    @Override
    public Object sendApi(String method, String logicalPath, Map<String, String> headers, byte[] body) {
        return callLocalApi(method, resolveUrl(logicalPath), headers, body);
    }

    /**
     * 沿调用栈找到首个属于某个插件的类，返回其插件名（跳过本插件内部类）；
     * 找不到则回落为主插件名（主插件自身调用不补 /plugins 前缀）。
     */
    private String detectCallerPlugin() {
        StackTraceElement[] stack = new Throwable().getStackTrace();
        for (int i = 1; i < stack.length; i++) {
            String cn = stack[i].getClassName();
            if (cn.startsWith("soys.soyshttpovermc")) continue; // 跳过本插件内部类
            try {
                Class<?> c = Class.forName(cn, false, getClass().getClassLoader());
                ClassLoader cl = c.getClassLoader();
                for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
                    if (p.getClass().getClassLoader() == cl) return p.getName();
                }
            } catch (Throwable ignored) {
            }
        }
        return hostPlugin.getName();
    }

    private static byte[] readAll(InputStream in) throws Exception {
        if (in == null) return new byte[0];
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) > 0) out.write(b, 0, n);
        in.close();
        return out.toByteArray();
    }
}
