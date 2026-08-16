package soys.soyshttpovermc.proxy;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import net.md_5.bungee.api.plugin.Plugin;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * home-server=self 时由代理模块自身托管的极简静态页面服务（HTTP）。
 * 从插件数据目录下的 web-root 读取文件；"/" 解析为 index.html；无文件时返回内置落地页。
 * 仅处理首段请求（一次性响应后关闭），满足“bungee 自身 web 配置作为主体”的基本诉求。
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
        int nl = indexOf(data, (byte) '\n', 0);
        if (nl > 0) {
            String line = new String(data, 0, nl, StandardCharsets.US_ASCII).trim();
            int sp1 = line.indexOf(' ');
            int sp2 = line.indexOf(' ', sp1 + 1);
            if (sp1 > 0 && sp2 > sp1) path = line.substring(sp1 + 1, sp2);
        }
        serve(path, ctx);
    }

    private void serve(String path, ChannelHandlerContext ctx) {
        File root = new File(plugin.getDataFolder(), config.getWebRoot());
        File f = resolve(root, path);
        byte[] body;
        String ctype;
        int code = 200;
        if (f == null || !f.isFile()) {
            if ("/".equals(path)) {
                body = defaultPage().getBytes(StandardCharsets.UTF_8);
                ctype = "text/html; charset=utf-8";
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
        byte[] head = ("HTTP/1.1 " + code + " OK\r\n"
                + "Content-Type: " + ctype + "\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
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

    private String defaultPage() {
        return "<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">"
                + "<title>SOYS · HTTP-Over-MC (BungeeCord 代理主页)</title></head><body>"
                + "<h1>SOYS HTTP-Over-MC · BungeeCord 代理主页</h1>"
                + "<p>该页面由 BungeeCord 端代理模块自身托管（home-server=self）。</p>"
                + "<ul>"
                + "<li>访问某子服主页：<code>/server/&lt;子服名&gt;/</code></li>"
                + "<li>访问某子服 API：<code>/server/&lt;子服名&gt;/api/...</code></li>"
                + "</ul></body></html>";
    }

    private static int indexOf(byte[] a, byte v, int from) {
        for (int i = from; i < a.length; i++) if (a[i] == v) return i;
        return -1;
    }
}
