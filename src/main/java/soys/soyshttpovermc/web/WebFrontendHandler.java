package soys.soyshttpovermc.web;

import soys.soyshttpovermc.proto.FrameProto;

import com.google.protobuf.ByteString;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 服务端 HTTP 处理器：把一次经 Bot 隧道送达的 HTTP 请求，路由为静态资源或动态 JSON。
 *
 * 资源查找优先级：
 *  1) 若 config 指定 web.root 且磁盘文件存在（含 .. 穿越防护）→ 磁盘文件；
 *  2) 否则回退到插件 jar 内置 /web/ 资源（默认赛博朋克状态面板）；
 *  3) 均不存在 → 404。
 *
 * 特殊路由：
 *  - GET /api/status → 实时统计 JSON（证明静态页 + 动态接口都通）；
 *  - GET /favicon.ico → 204 空响应。
 */
public class WebFrontendHandler {

    private final RequestStats stats;
    private final File webRoot;     // null 表示未配置磁盘 webroot
    private final String webRootCanonical;
    private final int port;
    private final String botName;

    public WebFrontendHandler(RequestStats stats, String webRootPath, int port, String botName) {
        this.stats = stats;
        this.port = port;
        this.botName = botName == null ? "" : botName;
        File root = null;
        String canonical = null;
        if (webRootPath != null && !webRootPath.trim().isEmpty()) {
            try {
                root = new File(webRootPath).getAbsoluteFile();
                canonical = root.getCanonicalPath();
                if (!root.isDirectory()) {
                    root.mkdirs();
                }
            } catch (Exception e) {
                root = null;
                canonical = null;
            }
        }
        this.webRoot = root;
        this.webRootCanonical = canonical;
    }

    /**
     * 处理一次请求，返回完整（单分片）HttpResponseFrame。
     * 调用方负责按 32000 字节上限分片发回客户端。
     */
    public FrameProto.HttpResponseFrame handle(String method, String path, Map<String, String> headers, byte[] body) {
        String m = method == null ? "" : method.toUpperCase();
        String cleanPath = stripQuery(decode(path));
        if (cleanPath.isEmpty() || cleanPath.equals("/")) cleanPath = "/";

        // 动态接口
        if (cleanPath.equals("/api/status") && (m.equals("GET") || m.equals("HEAD"))) {
            return jsonResponse(200, buildStatusJson());
        }
        if (cleanPath.equals("/favicon.ico")) {
            return FrameProto.HttpResponseFrame.newBuilder()
                    .setStatusCode(204)
                    .putHeaders("Content-Type", "image/x-icon")
                    .setBody(ByteString.EMPTY)
                    .setFragmentIndex(0)
                    .setTotalFragments(1)
                    .build();
        }

        // 静态资源
        // 目录/根路径实际服务 index.html，Content-Type 必须按实际文件名判定，否则浏览器会下载而非渲染
        String servedName = cleanPath;
        if (cleanPath.equals("/") || cleanPath.endsWith("/")) {
            servedName = "index.html";
        }
        byte[] content = resolveResource(cleanPath);
        if (content == null) {
            String notFound = "<!doctype html><html><head><meta charset=utf-8>"
                    + "<title>404</title></head><body style='font-family:monospace;background:#0a0a12;color:#0ff'>"
                    + "<h1>404 Not Found</h1><p>HTTP-Over-MC: " + escape(cleanPath) + " 不存在</p></body></html>";
            return FrameProto.HttpResponseFrame.newBuilder()
                    .setStatusCode(404)
                    .putHeaders("Content-Type", "text/html; charset=utf-8")
                    .setBody(ByteString.copyFrom(notFound.getBytes(StandardCharsets.UTF_8)))
                    .setFragmentIndex(0)
                    .setTotalFragments(1)
                    .build();
        }
        return FrameProto.HttpResponseFrame.newBuilder()
                .setStatusCode(200)
                .putHeaders("Content-Type", MimeTypes.forPath(servedName))
                .setBody(ByteString.copyFrom(content))
                .setFragmentIndex(0)
                .setTotalFragments(1)
                .build();
    }

    // ===== 资源解析 =====
    private byte[] resolveResource(String cleanPath) {
        String relative = cleanPath.startsWith("/") ? cleanPath.substring(1) : cleanPath;
        if (relative.isEmpty()) relative = "index.html";

        // 1) 磁盘 webroot
        if (webRoot != null) {
            File f = new File(webRoot, relative);
            try {
                if (f.getCanonicalPath().startsWith(webRootCanonical)
                        && f.isFile() && f.length() <= 16L * 1024 * 1024) {
                    return readFile(f);
                }
            } catch (Exception ignored) {
            }
        }

        // 2) jar 内置 /web/
        String jarPath = "/web/" + relative;
        byte[] fromJar = readResource(jarPath);
        if (fromJar != null) return fromJar;

        // 3) 目录默认首页
        if (cleanPath.equals("/")) {
            return readResource("/web/index.html");
        }
        return null;
    }

    private byte[] readResource(String resource) {
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            if (in == null) return null;
            return toBytes(in);
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] readFile(File f) throws IOException {
        try (FileInputStream in = new FileInputStream(f)) {
            return toBytes(in);
        }
    }

    private static byte[] toBytes(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }

    // ===== 动态 JSON =====
    private String buildStatusJson() {
        long up = System.currentTimeMillis() - stats.getStartTime();
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"online\":true,");
        sb.append("\"port\":").append(port).append(',');
        sb.append("\"bot\":\"").append(escapeJson(botName)).append("\",");
        sb.append("\"uptimeMillis\":").append(up).append(',');
        sb.append("\"uptime\":\"").append(formatUptime(up)).append("\",");
        sb.append("\"requests\":{");
        sb.append("\"total\":").append(stats.getTotal()).append(',');
        sb.append("\"get\":").append(stats.getGetCount()).append(',');
        sb.append("\"post\":").append(stats.getPostCount()).append(',');
        sb.append("\"other\":").append(stats.getOtherCount());
        sb.append("},");
        sb.append("\"latency\":{");
        sb.append("\"avgMs\":").append(fmt(stats.getAvgLatencyMs())).append(',');
        sb.append("\"maxMs\":").append(fmt(stats.getMaxLatencyMs()));
        sb.append("},");
        sb.append("\"recent\":[");
        boolean first = true;
        for (RequestStats.RecentReq r : stats.getRecent()) {
            if (!first) sb.append(',');
            first = false;
            sb.append("{\"method\":\"").append(escapeJson(r.method)).append('"');
            sb.append(",\"path\":\"").append(escapeJson(r.path)).append('"');
            sb.append(",\"code\":").append(r.code);
            sb.append(",\"ms\":").append(fmt(r.latencyMs()));
            sb.append('}');
        }
        sb.append("]");
        sb.append("}");
        return sb.toString();
    }

    private static FrameProto.HttpResponseFrame jsonResponse(int code, String json) {
        return FrameProto.HttpResponseFrame.newBuilder()
                .setStatusCode(code)
                .putHeaders("Content-Type", "application/json; charset=utf-8")
                .setBody(ByteString.copyFrom(json.getBytes(StandardCharsets.UTF_8)))
                .setFragmentIndex(0)
                .setTotalFragments(1)
                .build();
    }

    // ===== 路径/字符串工具 =====
    private static String stripQuery(String p) {
        int q = p.indexOf('?');
        return q >= 0 ? p.substring(0, q) : p;
    }

    private static String decode(String p) {
        if (p == null) return "/";
        try {
            return java.net.URLDecoder.decode(p, "UTF-8");
        } catch (Exception e) {
            return p;
        }
    }

    private static String formatUptime(long ms) {
        long s = ms / 1000;
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        if (h > 0) return h + "h" + m + "m";
        if (m > 0) return m + "m" + sec + "s";
        return sec + "s";
    }

    private static String fmt(double v) {
        if (v < 0) return "null";
        return String.format("%.2f", v);
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
