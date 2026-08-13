package soys.soyshttpovermc.web;

import soys.soyshttpovermc.api.util.AjaxResult;
import soys.soyshttpovermc.api.ApiRegistry;
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
 * 服务端 HTTP 处理器：把一次经 Bot 隧道送达的 HTTP 请求，路由为注解式 API 或静态资源。
 *
 * 路由优先级：
 *  1) 注解式 API（@GetMapping 注册，如 /api/status、/api/ping）→ dispatch；
 *  2) /favicon.ico → 204；
 *  3) 静态资源：web.root 磁盘目录（含 .. 穿越防护）→ jar 内置 /web/ → 404。
 */
public class WebFrontendHandler {

    private final File webRoot;     // null 表示未配置磁盘 webroot
    private final String webRootCanonical;
    private final ApiRegistry apiRegistry; // 注解式 API 注册表（可为 null）

    public WebFrontendHandler(String webRootPath, ApiRegistry apiRegistry) {
        this.apiRegistry = apiRegistry;
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
        String rawPath = decode(path);

        // 0) 注解式 API 优先（@GetMapping 等注册的路由）
        if (apiRegistry != null) {
            Object apiResult = apiRegistry.dispatch(m, rawPath, headers, body);
            if (apiResult != null) {
                AjaxResult ar = apiResult instanceof AjaxResult
                        ? (AjaxResult) apiResult : AjaxResult.success(apiResult);
                return jsonResponse(200, ar.toJson());
            }
        }

        String cleanPath = stripQuery(rawPath);
        if (cleanPath.isEmpty() || cleanPath.equals("/")) cleanPath = "/";

        // 动态接口
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
        // 目录/根路径实际服务 index.html（/ 或 /status 或 /xxx/ 均回退 <dir>/index.html），
        // Content-Type 必须按实际文件名判定，否则浏览器会下载而非渲染
        String servedName = cleanPath;
        if (cleanPath.equals("/") || cleanPath.endsWith("/") || !hasExtension(cleanPath)) {
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
        // 无扩展名的路径视为目录：/status、/status/ → status/index.html
        if (!relative.equals("index.html") && !hasExtension(relative)) {
            relative = relative.endsWith("/") ? relative + "index.html" : relative + "/index.html";
        }

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
        return readResource("/web/" + relative);
    }

    /** 路径最后一段是否含扩展名（无扩展名视为目录 → 找 index.html） */
    private static boolean hasExtension(String p) {
        String last = p;
        int slash = p.lastIndexOf('/');
        if (slash >= 0) last = p.substring(slash + 1);
        return last.indexOf('.') >= 0;
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

    // ===== JSON 响应（注解式 API 序列化） =====
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

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
