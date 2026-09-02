package com.github.cocosoys.mc.soyshttpovermc.web;

import lombok.CustomLog;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 首页（{@code web.home}）解析器：支持三种来源，返回首页内容字节与 Content-Type；
 * <b>未配置或解析失败返回 null</b>（调用方回退默认 web.root/index.html → jar /dist/index.html）。
 *
 * <ul>
 *   <li><b>相对/逻辑路径</b>：如 {@code dist/index.html}（剥 {@code dist/} 前缀 → jar 内置 /dist/index.html）、
 *       {@code status/index.html}（按 web.root → jar /dist/ 顺序解析，等价普通静态资源）；正常情况下直接填写相对路径。
 *       例如 {@code /status/index.html}、{@code /index.html}。</li>
 *   <li><b>绝对路径</b>：如 {@code C:/sites/home.html}（本地磁盘文件，直接伺服）；</li>
 *   <li><b>网络 URL</b>：如 {@code https://example.com/home.html}（按需拉取并内存缓存约 {@link #REMOTE_TTL_MS}，
 *       失败回退默认首页，不缓存失败结果）。</li>
 * </ul>
 *
 * <p>大文件防护：任何来源超过 {@code maxBytes} 上限视为失败（回退默认），防单文件把内存打爆。</p>
 */
@CustomLog
public class HomePageResolver {

    /**
     * 网络首页缓存存活时间（毫秒）。
     */
    public static final long REMOTE_TTL_MS = 5 * 60 * 1000L;

    /**
     * 解析结果（首页字节 + 生效 Content-Type；contentType 可为 null=按扩展名推断）。
     */
    public static final class Result {
        public final String name;
        public final byte[] bytes;
        public final String contentType;

        Result(String name, byte[] bytes, String contentType) {
            this.name = name;
            this.bytes = bytes;
            this.contentType = contentType;
        }
    }

    private final String spec;
    private final File webRoot;
    private final String webRootCanonical;
    private final long maxBytes;
    private final String jarPrefix; // 相对路径的 jar 兜底前缀（/dist），与静态资源一致

    // 网络首页缓存（单 URL，互斥读写）
    private volatile byte[] remoteBytes;
    private volatile String remoteContentType;
    private volatile long remoteCachedAt;

    public HomePageResolver(String homeSpec, File webRoot, String webRootCanonical, long maxBytes) {
        this.spec = homeSpec == null ? "" : homeSpec.trim();
        this.webRoot = webRoot;
        this.webRootCanonical = webRootCanonical;
        this.maxBytes = Math.max(0, maxBytes);
        this.jarPrefix = "/dist";
    }

    /**
     * 首页是否已配置（非空）。
     */
    public boolean isConfigured() {
        return !spec.isEmpty();
    }

    /**
     * 解析首页；未配置/失败返回 null。
     */
    public Result resolve() {
        if (!isConfigured()) return null;
        if (spec.startsWith("http://") || spec.startsWith("https://")) {
            return resolveRemote(spec);
        }
        File f = new File(spec);
        if (f.isAbsolute()) {
            return resolveFile(f);
        }
        return resolveLogical(spec);
    }

    /**
     * 网络 URL：拉取 + 缓存（TTL）；失败返回 null（回退默认）。
     */
    private Result resolveRemote(String url) {
        long now = System.currentTimeMillis();
        byte[] cached = remoteBytes;
        if (cached != null && now - remoteCachedAt < REMOTE_TTL_MS) {
            return new Result("home.html", cached, remoteContentType);
        }
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "SOYSHTTPOverMC/1.0");
            int status = conn.getResponseCode();
            if (status < 200 || status >= 300) {
                log.warnT("log.web.home-fetch-fail", "首页网络拉取失败: {0} status={1}", url, status);
                return null;
            }
            long len = conn.getContentLengthLong();
            if (len > maxBytes) {
                log.warnT("log.web.home-oversize", "首页网络内容超过大文件上限，回退默认: {0} size={1}", url, len);
                return null;
            }
            byte[] body = toBytes(conn.getInputStream());
            if (body == null || body.length == 0 || (maxBytes > 0 && body.length > maxBytes)) return null;
            String ct = conn.getContentType();
            if (ct == null || ct.trim().isEmpty()) ct = MimeTypes.forPath(url);
            remoteBytes = body;
            remoteContentType = ct;
            remoteCachedAt = System.currentTimeMillis();
            log.infoT("log.web.home-fetch-ok", "首页网络拉取成功: {0} ({1} B, ct={2})", url, body.length, ct);
            return new Result("home.html", body, ct);
        } catch (Exception e) {
            log.warnT("log.web.home-fetch-exception", "首页网络拉取异常，回退默认首页: {0} -> {1}", url, e.getMessage());
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * 绝对路径：本地磁盘文件（含大文件防护）。
     */
    private Result resolveFile(File f) {
        try {
            if (!f.isFile()) {
                log.warnT("log.web.home-file-missing", "首页绝对路径不存在，回退默认: {0}", f.getAbsolutePath());
                return null;
            }
            if (maxBytes > 0 && f.length() > maxBytes) {
                log.warnT("log.web.home-file-oversize", "首页文件超过大文件上限，回退默认: {0} size={1}", f.getAbsolutePath(), f.length());
                return null;
            }
            byte[] body = readFile(f);
            if (body == null || body.length == 0) return null;
            return new Result(f.getName(), body, null);
        } catch (Exception e) {
            log.warnT("log.web.home-file-read-fail", "首页绝对路径读取失败，回退默认: {0} -> {1}", f.getAbsolutePath(), e.getMessage());
            return null;
        }
    }

    /**
     * 相对/逻辑路径：剥 dist/ 前缀后按 web.root → jar /dist/ 顺序解析。
     */
    private Result resolveLogical(String path) {
        String rel = path.startsWith("/") ? path.substring(1) : path;
        if (rel.isEmpty()) return null;
        // "dist/index.html" → jar /dist/index.html（dist/ 前缀显式表达"jar 内置 dist"）
        if (rel.startsWith("dist/")) {
            rel = rel.substring("dist/".length());
            return jarResource(rel);
        }
        // 其余：web.root 优先 → jar /dist/ 兜底（与普通静态资源一致）
        if (webRoot != null) {
            File f = new File(webRoot, rel);
            try {
                if (webRootCanonical != null && f.getCanonicalPath().startsWith(webRootCanonical) && f.isFile()) {
                    if (maxBytes <= 0 || f.length() <= maxBytes) {
                        byte[] body = readFile(f);
                        if (body != null && body.length > 0) return new Result(f.getName(), body, null);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return jarResource(rel);
    }

    private Result jarResource(String rel) {
        byte[] body = readJarResource(jarPrefix + "/" + rel);
        if (body == null || body.length == 0) {
            log.warnT("log.web.home-logical-miss", "首页逻辑路径未命中，回退默认: {0} (rel={1})", spec, rel);
            return null;
        }
        return new Result(rel.substring(rel.lastIndexOf('/') + 1), body, null);
    }

    private byte[] readJarResource(String resource) {
        try (InputStream in = HomePageResolver.class.getResourceAsStream(resource)) {
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
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) > 0) out.write(b, 0, n);
        return out.toByteArray();
    }
}
