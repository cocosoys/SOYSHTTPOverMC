package soys.soyshttpovermc.api.impl;

import soys.soyshttpovermc.ApiRegistry;
import soys.soyshttpovermc.HttpResponse;
import soys.soyshttpovermc.api.HttpClientApi;
import soys.soyshttpovermc.exception.ExceptionBus;
import soys.soyshttpovermc.exception.HttpClientException;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 能力组 7：HTTP 请求（对外真实请求 + 对内回环调本插件 API）。
 * 由 {@link SoysHttpOverMcApiImpl} 组合并对外暴露；实现 {@link HttpClientApi}。
 */
public class HttpClientImpl implements HttpClientApi {

    private final ApiRegistry apiRegistry;

    public HttpClientImpl(ApiRegistry apiRegistry) {
        this.apiRegistry = apiRegistry;
    }

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
            throw ExceptionBus.fire(new HttpClientException("E_SEND", method + " " + url + " 失败: " + ex.getMessage(), ex));
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

    @Override
    public Object callLocalApi(String method, String path, Map<String, String> headers, byte[] body) {
        try {
            return apiRegistry.dispatch(method, path,
                    headers == null ? Collections.<String, String>emptyMap() : headers, body);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new HttpClientException("E_LOCAL", "本地回环调用失败(" + method + " " + path + "): " + ex.getMessage(), ex));
        }
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
