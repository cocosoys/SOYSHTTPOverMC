package soys.soyshttpovermc.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.plugin.Plugin;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 首包分类器（挂在每个被接受子连接的 pipeline 最前）。
 * <p>首包判定：
 * <ul>
 *   <li>{@code 0x16 0x03} → TLS(HTTPS)：代理<b>自身终止客户端 TLS</b>（SslContext 来自 TlsContextProvider），
 *       解密后的明文 HTTP 交给 {@link PlainHttpHandler} 路由；PlainHttpHandler 为每条客户端连接新建一条
 *       独立后端 TLS 客户端连接(secure=true)，并以串行最小间隔建连，避免“透明透传复用后端连接导致的第二条
 *       客户端 TLS 会话冲突”（后端在新 ClientHello 上 RST）及后端 spike-drop。</li>
 *   <li>首字节 A-Z 且首行形如 {@code METHOD /path HTTP/x.y} → 明文 HTTP：代理直接返回 426 要求 TLS 升级。</li>
 *   <li>其余 → 视为 MC，原样 fireChannelRead 交还 BungeeCord 正常代理。</li>
 * </ul>
 */
public class HttpClassifier extends ChannelInboundHandlerAdapter {

    /** 本 handler 在子连接 pipeline 中的名字（注入与自摘除都用它）。 */
    public static final String NAME = "soys-http-classifier";

    private final Plugin plugin;
    private final ProxyConfig config;
    private final ProxyServer proxy;

    private final ByteArrayOutputStream buf = new ByteArrayOutputStream();
    private boolean decided = false;
    private boolean passthrough = false;   // MC：透传
    private boolean isTls = false;
    private BackendPipe pipe;               // 接管后的后端透传（旧透传路径保留兼容）
    private SelfServer selfServer;         // 接管后的自托管

    public HttpClassifier(Plugin plugin, ProxyConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.proxy = plugin.getProxy();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof ByteBuf)) {
            ctx.fireChannelRead(msg);
            return;
        }
        if (decided && passthrough) {
            ctx.fireChannelRead(msg);
            return;
        }
        ByteBuf bb = (ByteBuf) msg;
        byte[] chunk = new byte[bb.readableBytes()];
        bb.getBytes(bb.readerIndex(), chunk);
        bb.release();
        try {
            if (!decided) {
                buf.write(chunk);
                decide(ctx);
                if (!decided) return;
                byte[] all = buf.toByteArray();
                buf.reset();
                if (passthrough) {
                    ctx.pipeline().remove(this);
                    ctx.fireChannelRead(Unpooled.wrappedBuffer(all));
                    return;
                }
                if (pipe != null || selfServer != null) {
                    detachDownstream(ctx);
                }
                if (pipe != null) pipe.writeClient(all);
                else if (selfServer != null) selfServer.feed(all, ctx);
                return;
            }
            if (pipe != null) pipe.writeClient(chunk);
            else if (selfServer != null) selfServer.feed(chunk, ctx);
        } catch (Throwable t) {
            plugin.getLogger().warning("[SOYS-Proxy] 分类/转发异常: " + t);
            ctx.close();
        }
    }

    private void detachDownstream(ChannelHandlerContext ctx) {
        int removed = 0;
        for (String name : new java.util.ArrayList<String>(ctx.pipeline().names())) {
            if (NAME.equals(name)) continue;
            try {
                if (ctx.pipeline().get(name) == null) continue;
                ctx.pipeline().remove(name);
                removed++;
            } catch (Throwable ignore) {
            }
        }
        if (removed > 0) {
            plugin.getLogger().fine("[SOYS-Proxy] 已摘除下游 handler " + removed + " 个（接管为纯管道）。");
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (pipe != null) pipe.close();
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (pipe != null) pipe.close();
        ctx.close();
    }

    private void decide(ChannelHandlerContext ctx) throws Exception {
        byte[] data = buf.toByteArray();
        if (data.length < 2) return;
        int b0 = data[0] & 0xFF;
        int b1 = data[1] & 0xFF;

        if (b0 == 0x16 && b1 == 0x03) {
            isTls = true;
            decided = true;
            routeTls(ctx);
            return;
        }
        if (b0 >= 0x41 && b0 <= 0x5A) {
            int nl = indexOf(data, (byte) '\n', 0);
            if (nl < 0) {
                if (data.length > 8192) {
                    decided = true; passthrough = true;
                }
                return;
            }
            String line = new String(data, 0, nl, StandardCharsets.US_ASCII).trim();
            int sp1 = line.indexOf(' ');
            int sp2 = line.indexOf(' ', sp1 + 1);
            if (sp1 > 0 && sp2 > sp1 && line.substring(sp2 + 1).startsWith("HTTP/")) {
                decided = true;
                String path = line.substring(sp1 + 1, sp2);
                routeHttp(ctx, path);
                return;
            }
            decided = true; passthrough = true;
            return;
        }
        decided = true; passthrough = true;
    }

    /**
     * 处理明文 HTTP：代理直接返回 426 要求 TLS 升级（不为此建后端连接）。
     * 浏览器收到 426 后会改用 HTTPS 重连，进入 routeTls 反向代理路径。
     */
    private void routeHttp(ChannelHandlerContext ctx, String path) throws Exception {
        writeError(ctx, 426, "TLS Required");
    }

    /**
     * 处理 TLS(HTTPS)：代理自身终止客户端 TLS，解密后的明文交给 PlainHttpHandler 路由。
     * 不论 home-server 是 self 还是具体子服名，统一走此反向代理路径：
     * <ul>
     *   <li>home-server=self → PlainHttpHandler 的 "/" 等自托管静态页；</li>
     *   <li>home-server=具体子服 → PlainHttpHandler 的 "/" 路由到该子服后端；</li>
     *   <li>/server/&lt;name&gt;/... 与 /srv/&lt;name&gt;/... 始终路由到对应子服后端。</li>
     * </ul>
     * 后端连接由 PlainHttpHandler 为<b>每条客户端连接新建一条独立 TLS 客户端连接</b>(secure=true)，
     * 并以 {@link ProxyConfig#getConnectionSpacingMs()} 串行最小间隔建连，消除微秒级密集建连触发的
     * 后端 MC 端口 spike-drop（静默 RST 第 4+ 连接 → 客户端 ERR_CONNECTION_ABORTED）。
     * 注意：BungeeCord 自身的 connection_throttle 亦会按 IP 丢弃并行连接，代理端口须在 config.yml
     * 设 connection_throttle: -1 才能放行浏览器单页的并行 TLS 连接。
     */
    private void routeTls(ChannelHandlerContext ctx) throws Exception {
        try {
            io.netty.handler.ssl.SslContext sslCtx = TlsContextProvider.get(plugin, config);
            detachDownstream(ctx);
            ctx.pipeline().addAfter(NAME, "soys-ssl", sslCtx.newHandler(ctx.alloc()));
            ctx.pipeline().addAfter("soys-ssl", PlainHttpHandler.NAME,
                    new PlainHttpHandler(plugin, config, config.getHomeServer()));
            passthrough = true;
            plugin.getLogger().info("[SOYS-Proxy] TLS 由代理自身终止（反向代理，home-server=" + config.getHomeServer() + "）");
        } catch (Throwable t) {
            plugin.getLogger().warning("[SOYS-Proxy] TLS 终止/反向代理初始化失败: " + t);
            ctx.close();
        }
    }

    private void writeError(ChannelHandlerContext ctx, int code, String msg) {
        String body = code + " " + msg;
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        String head = "HTTP/1.1 " + code + " " + msg + "\r\n"
                + "Content-Type: text/plain; charset=utf-8\r\n"
                + "Content-Length: " + bodyBytes.length + "\r\n"
                + "Connection: close\r\n\r\n";
        byte[] headBytes = head.getBytes(StandardCharsets.US_ASCII);
        byte[] out = new byte[headBytes.length + bodyBytes.length];
        System.arraycopy(headBytes, 0, out, 0, headBytes.length);
        System.arraycopy(bodyBytes, 0, out, headBytes.length, bodyBytes.length);
        ctx.writeAndFlush(Unpooled.copiedBuffer(out));
        ctx.close();
    }

    private static int indexOf(byte[] a, byte v, int from) {
        for (int i = from; i < a.length; i++) if (a[i] == v) return i;
        return -1;
    }
}
