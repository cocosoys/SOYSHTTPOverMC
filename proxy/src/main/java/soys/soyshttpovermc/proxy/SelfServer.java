package soys.soyshttpovermc.proxy;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import net.md_5.bungee.api.plugin.Plugin;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.zip.GZIPOutputStream;

/**
 * home-server=self 时由代理模块自身托管的极简静态页面服务（HTTP）。
 * 从插件数据目录下的 web-root 读取文件；"/" 解析为 index.html；无文件时返回内置落地页。
 * <p>支持 gzip 压缩、ETag + 304、Cache-Control（省流量 + 快速重复加载）。每条请求独立响应后关闭
 * （self 模式多为单次落地页访问，保持 Connection: close）。</p>
 */
public class SelfServer {

    private final Plugin plugin;
    private final ProxyConfig config;
    private boolean handled = false;

    public SelfServer(Plugin plugin, ProxyConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void feed(byte[] data, ChannelHandlerContext ctx) {
        if (handled) return;
        handled = true;
        String path = "/";
        String acceptEncoding = "";
        String ifNoneMatch = "";
        int nl = indexOf(data, (byte) '\n', 0);
        if (nl > 0) {
            String line = new String(data, 0, nl, StandardCharsets.US_ASCII).trim();
            int sp1 = line.indexOf(' ');
            int sp2 = line.indexOf(' ', sp1 + 1);
            if (sp1 > 0 && sp2 > sp1) path = line.substring(sp1 + 1, sp2);
        }
        // 解析请求头（Accept-Encoding / If-None-Match）用于压缩与 304
        String headerText = extractHeaderText(data);
        if (headerText != null) {
            for (String h : headerText.split("\r\n")) {
                int c = h.indexOf(':');
                if (c > 0) {
                    String k = h.substring(0, c).trim();
                    String v = h.substring(c + 1).trim();
                    if (k.equalsIgnoreCase("Accept-Encoding")) acceptEncoding = v;
                    else if (k.equalsIgnoreCase("If-None-Match")) ifNoneMatch = v;
                }
            }
        }
        serve(path, acceptEncoding, ifNoneMatch, ctx);
    }

    private void serve(String path, String acceptEncoding, String ifNoneMatch, ChannelHandlerContext ctx) {
        File root = new File(plugin.getDataFolder(), config.getWebRoot());
        File f = resolve(root, path);
        byte[] body;
        String ctype;
        int code = 200;
        if (f == null || !f.isFile()) {
            if ("/".equals(path)) {
                body = defaultPage().getBytes(StandardCharsets.UTF_8);
                ctype = "application/json; charset=utf-8"; // 无 index.html 时返回 JSON 服务信息（非 HTML）
            } else {
                body = ("404 Not Found: " + path).getBytes(StandardCharsets.UTF_8);
                ctype = "text/plain; charset=utf-8";
                code = 404;
            }
        } else {
            try (FileInputStream fis = new FileInputStream(f)) {
                body = new byte[(int) f.length()];
                int off = 0, n;
                while ((n = fis.read(body, off, body.length - off)) > 0) off += n;
                ctype = guessType(f.getName());
            } catch (Exception e) {
                body = ("500 " + e).getBytes(StandardCharsets.UTF_8);
                ctype = "text/plain; charset=utf-8";
                code = 500;
            }
        }

        // ETag + 304
        String etag = '"' + sha256hex(body) + '"';
        if (!ifNoneMatch.isEmpty() && ifNoneMatch.trim().equals(etag)) {
            String head304 = "HTTP/1.1 304 Not Modified\r\n"
                    + "ETag: " + etag + "\r\n"
                    + "Cache-Control: public, max-age=300\r\n"
                    + "Content-Length: 0\r\n"
                    + "Connection: close\r\n\r\n";
            byte[] out = head304.getBytes(StandardCharsets.US_ASCII);
            ctx.writeAndFlush(Unpooled.copiedBuffer(out));
            ctx.close();
            return;
        }

        // gzip 压缩
        boolean compressed = false;
        if (isCompressible(ctype) && body.length >= 512 && acceptEncoding.contains("gzip")) {
            byte[] gz = gzip(body);
            if (gz.length < body.length) { body = gz; compressed = true; }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 ").append(code).append(" OK\r\n");
        sb.append("Content-Type: ").append(ctype).append("\r\n");
        if (compressed) sb.append("Content-Encoding: gzip\r\n");
        sb.append("ETag: ").append(etag).append("\r\n");
        sb.append("Cache-Control: public, max-age=300\r\n");
        sb.append("Content-Length: ").append(body.length).append("\r\n");
        sb.append("Connection: close\r\n\r\n");
        byte[] head = sb.toString().getBytes(StandardCharsets.US_ASCII);
        byte[] out = new byte[head.length + body.length];
        System.arraycopy(head, 0, out, 0, head.length);
        System.arraycopy(body, 0, out, head.length, body.length);
        ctx.writeAndFlush(Unpooled.copiedBuffer(out));
        ctx.close();
    }

    private File resolve(File root, String path) {
        try {
            if (path == null || path.isEmpty()) path = "/";
            String rel = path.startsWith("/") ? path.substring(1) : path;
            int q = rel.indexOf('?');
            if (q >= 0) rel = rel.substring(0, q);
            if (rel.isEmpty()) rel = "index.html";
            else if (rel.endsWith("/")) rel += "index.html";
            File f = new File(root, rel).getCanonicalFile();
            File r = root.getCanonicalFile();
            if (!f.getPath().startsWith(r.getPath() + File.separator) && !f.getPath().equals(r.getPath())) {
                return null; // 防目录穿越
            }
            return f;
        } catch (Exception e) {
            return null;
        }
    }

    private String guessType(String name) {
        name = name.toLowerCase();
        if (name.endsWith(".html") || name.endsWith(".htm")) return "text/html; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (name.endsWith(".json")) return "application/json; charset=utf-8";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".txt")) return "text/plain; charset=utf-8";
        return "application/octet-stream";
    }

    /** 无 index.html 时的兜底主页：返回 JSON 服务信息（非 HTML，符合「后端零 HTML」整改）。 */
    private String defaultPage() {
        return "{\"service\":\"soys-http-proxy\",\"mode\":\"self\","
                + "\"tip\":\"该页面由 BungeeCord 端代理模块托管（home-server=self），请在 web-root 放置 index.html 以定制主页。\","
                + "\"home\":\"/server/<子服名>/\"}";
    }

    /** 从请求字节中提取头块文本（到 \r\n\r\n 之前）。 */
    private static String extractHeaderText(byte[] data) {
        int end = indexOfSeq(data, 0, data.length, new byte[]{'\r', '\n', '\r', '\n'});
        if (end < 0) end = indexOfSeq(data, 0, data.length, new byte[]{'\n', '\n'});
        if (end < 0) return null;
        return new String(data, 0, end, StandardCharsets.US_ASCII);
    }

    private static boolean isCompressible(String ctype) {
        if (ctype == null) return false;
        String c = ctype.toLowerCase();
        if (c.startsWith("text/")) return true;
        if (c.startsWith("application/javascript") || c.startsWith("application/json")
                || c.startsWith("application/xml") || c.startsWith("application/atom+xml")
                || c.startsWith("application/ld+json") || c.startsWith("application/x-javascript")
                || c.startsWith("image/svg+xml")) return true;
        return false;
    }

    private static byte[] gzip(byte[] data) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length);
            try (GZIPOutputStream gz = new GZIPOutputStream(bos)) { gz.write(data); }
            return bos.toByteArray();
        } catch (Exception e) {
            return data;
        }
    }

    private static String sha256hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(data);
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte x : d) sb.append(String.format("%02x", x & 0xFF));
            return sb.toString();
        } catch (Exception e) {
            int h = java.util.Arrays.hashCode(data);
            return String.format("%08x", h);
        }
    }

    private static int indexOf(byte[] a, byte v, int from) {
        for (int i = from; i < a.length; i++) if (a[i] == v) return i;
        return -1;
    }

    private static int indexOfSeq(byte[] a, int from, int to, byte[] seq) {
        int sl = seq.length;
        if (sl == 0) return from;
        for (int i = from; i + sl <= to; i++) {
            boolean ok = true;
            for (int j = 0; j < sl; j++) if (a[i + j] != seq[j]) { ok = false; break; }
            if (ok) return i;
        }
        return -1;
    }
}
