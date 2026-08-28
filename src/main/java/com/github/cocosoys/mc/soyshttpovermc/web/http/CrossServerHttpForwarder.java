package com.github.cocosoys.mc.soyshttpovermc.web.http;

import com.github.cocosoys.mc.soyshttpovermc.proxy.ServerRegistry;
import com.github.cocosoys.mc.soyshttpovermc.proxy.ServerTag;
import com.github.cocosoys.mc.soyshttpovermc.util.HttpFrames;
import com.github.cocosoys.mc.soyshttpovermc.web.proto.FrameProto;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 跨服 HTTP 转发器（非 bot-tunnel 模式使用）。
 *
 * <p>当请求路径以 {@code /server/<server-name>/} 开头时，识别为跨服请求，
 * 从 {@link ServerRegistry} 获取目标服务器的 host:port，通过 HTTP 客户端直接转发到
 * 目标服务器的同端口 HTTP 服务（目标服务器也运行 SOYSHTTPOverMC 并开启同端口嗅探）。
 *
 * <p>这是 bot-tunnel 模式下 BungeeCord Forward 跨服路由的替代方案，不依赖 Bot 和 PluginMessage 通道。
 *
 * <p>路径转换：{@code /server/lobby/api/status} → {@code http://<lobby-host>:<lobby-port>/api/status}
 */
public class CrossServerHttpForwarder {

    /** 跨服请求路径前缀 */
    public static final String CROSS_SERVER_PREFIX = "/server/";

    private final ServerRegistry registry;
    private final String localServerName;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public CrossServerHttpForwarder(ServerRegistry registry, String localServerName) {
        this(registry, localServerName, 5000, 30000);
    }

    public CrossServerHttpForwarder(ServerRegistry registry, String localServerName,
                                     int connectTimeoutMs, int readTimeoutMs) {
        this.registry = registry;
        this.localServerName = localServerName == null ? "" : localServerName;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    /**
     * 判断请求是否为跨服请求（路径以 /server/<name>/ 开头）。
     */
    public boolean isCrossServerRequest(String path) {
        if (path == null || !path.startsWith(CROSS_SERVER_PREFIX)) return false;
        String rest = path.substring(CROSS_SERVER_PREFIX.length());
        int slash = rest.indexOf('/');
        return slash > 0; // 必须有服务器名和后续路径
    }

    /**
     * 解析跨服请求的目标服务器名。
     * @return 服务器名，或 null（如果路径格式不正确）
     */
    public String parseTargetServer(String path) {
        if (!isCrossServerRequest(path)) return null;
        String rest = path.substring(CROSS_SERVER_PREFIX.length());
        int slash = rest.indexOf('/');
        return rest.substring(0, slash);
    }

    /**
     * 剥离跨服前缀，返回目标服务器上的实际路径。
     * 例如：/server/lobby/api/status?q=1 → /api/status?q=1
     */
    public String stripPrefix(String path) {
        if (!isCrossServerRequest(path)) return path;
        String rest = path.substring(CROSS_SERVER_PREFIX.length());
        int slash = rest.indexOf('/');
        return rest.substring(slash);
    }

    /**
     * 转发跨服请求到目标服务器。
     *
     * @param method  HTTP 方法
     * @param path    原始请求路径（含 /server/<name>/ 前缀）
     * @param headers 请求头
     * @param body    请求体
     * @return 目标服务器的响应帧；如果目标服务器未找到或转发失败，返回错误响应帧
     */
    public FrameProto.HttpResponseFrame forward(String method, String path,
                                                  Map<String, String> headers, byte[] body) {
        String targetName = parseTargetServer(path);
        if (targetName == null) {
            return HttpFrames.jsonError(400, "Invalid cross-server path");
        }

        // 目标是本服，直接剥离前缀后由本地处理（调用方应在调用前判断）
        if (targetName.equals(localServerName)) {
            return HttpFrames.jsonError(400, "Target is local server, use local path");
        }

        ServerTag target = registry.get(targetName);
        if (target == null) {
            return HttpFrames.jsonError(502, "Target server not found: " + targetName);
        }
        if (!target.isOnline()) {
            return HttpFrames.jsonError(502, "Target server offline: " + targetName);
        }

        String targetPath = stripPrefix(path);
        String targetUrl = "http://" + target.getHost() + ":" + target.getPort() + targetPath;

        HttpURLConnection conn = null;
        try {
            URL url = new URL(targetUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(connectTimeoutMs);
            conn.setReadTimeout(readTimeoutMs);
            conn.setInstanceFollowRedirects(false);

            // 复制请求头（跳过 hop-by-hop 头）
            if (headers != null) {
                for (Map.Entry<String, String> e : headers.entrySet()) {
                    String k = e.getKey();
                    if (isHopByHopHeader(k)) continue;
                    conn.setRequestProperty(k, e.getValue());
                }
            }

            // 写入请求体
            if (body != null && body.length > 0 && !"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body);
                }
            }

            int statusCode = conn.getResponseCode();

            // 读取响应体
            InputStream is = (statusCode >= 400) ? conn.getErrorStream() : conn.getInputStream();
            byte[] respBody = new byte[0];
            if (is != null) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) != -1) {
                    bos.write(buf, 0, n);
                }
                respBody = bos.toByteArray();
            }

            // 构建响应帧
            FrameProto.HttpResponseFrame.Builder builder = FrameProto.HttpResponseFrame.newBuilder()
                    .setStatusCode(statusCode)
                    .setBody(com.google.protobuf.ByteString.copyFrom(respBody));

            // 复制响应头
            Map<String, List<String>> respHeaders = conn.getHeaderFields();
            if (respHeaders != null) {
                for (Map.Entry<String, List<String>> e : respHeaders.entrySet()) {
                    String k = e.getKey();
                    if (k == null || isHopByHopHeader(k)) continue;
                    List<String> vals = e.getValue();
                    if (vals == null || vals.isEmpty()) continue;
                    String v = String.join(", ", vals);
                    builder.putHeaders(k, v);
                }
            }

            return builder.build();

        } catch (java.net.SocketTimeoutException e) {
            return HttpFrames.jsonError(504, "Cross-server timeout: " + targetName);
        } catch (java.net.ConnectException e) {
            return HttpFrames.jsonError(502, "Cross-server connection refused: " + targetName);
        } catch (Exception e) {
            return HttpFrames.jsonError(502, "Cross-server forward failed: " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** 判断是否为 hop-by-hop 头（不应被转发） */
    private static boolean isHopByHopHeader(String name) {
        if (name == null) return true;
        String lower = name.toLowerCase();
        return lower.equals("connection") || lower.equals("keep-alive")
                || lower.equals("proxy-authenticate") || lower.equals("proxy-authorization")
                || lower.equals("te") || lower.equals("trailers")
                || lower.equals("transfer-encoding") || lower.equals("upgrade")
                || lower.equals("host") || lower.equals("content-length");
    }
}
