package soys.soyshttpovermc.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.plugin.Plugin;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * home-server=self 时，位于 {@code SslHandler} 之后的明文 HTTP 路由 handler。
 * <p>TLS 已由代理终止，这里拿到的是解密后的明文 HTTP 字节：
 * <ul>
 *   <li>{@code /server/<name>/...} → 经 {@link BackendPipe}（<b>TLS 客户端</b>模式）转发到该子服后端
 *       MC 端口，后端既有 HTTP 栈处理；响应明文回写，出站再由 SslHandler 加密给客户端；</li>
 *   <li>其余路径 → 由 {@link SelfServer} 用代理数据目录下的 web-root 托管。</li>
 * </ul>
 */
public class PlainHttpHandler extends ChannelInboundHandlerAdapter {

    public static final String NAME = "soys-plain-http";

    private final Plugin plugin;
    private final ProxyConfig config;
    private final ProxyServer proxy;
    private final String homeServer;
    // 串行间隔建连的每目标锁（消除并发建连突发 spike-drop）
    private final java.util.Map<String, Object> lockMap = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, Long> lastMap = new java.util.concurrent.ConcurrentHashMap<>();

    private final ByteArrayOutputStream buf = new ByteArrayOutputStream();
    private boolean routed = false;
    private BackendPipe pipe;
    private SelfServer selfServer;

    /** @param homeServer 配置的主页服务器：具体子服名 → "/" 路由到它；"self" → "/" 自托管静态页。 */
    public PlainHttpHandler(Plugin plugin, ProxyConfig config, String homeServer) {
        this.plugin = plugin;
        this.config = config;
        this.proxy = plugin.getProxy();
        this.homeServer = homeServer;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof ByteBuf)) {
            ctx.fireChannelRead(msg);
            return;
        }
        ByteBuf bb = (ByteBuf) msg;
        byte[] chunk = new byte[bb.readableBytes()];
        bb.getBytes(bb.readerIndex(), chunk);
        bb.release();
        try {
            if (!routed) {
                buf.write(chunk);
                byte[] data = buf.toByteArray();
                int nl = indexOf(data, (byte) '\n');
                if (nl < 0) {
                    if (data.length > 8192) ctx.close();      // 首行异常长 → 丢弃
                    return;                                    // 等首行到齐
                }
                String line = new String(data, 0, nl, StandardCharsets.US_ASCII).trim();
                int sp1 = line.indexOf(' ');
                int sp2 = line.indexOf(' ', sp1 + 1);
                String path = (sp1 > 0 && sp2 > sp1) ? line.substring(sp1 + 1, sp2) : "/";
                routed = true;
                route(ctx, path);
                byte[] all = buf.toByteArray();
                buf.reset();
                dispatch(all, ctx);
                return;
            }
            dispatch(chunk, ctx);
        } catch (Throwable t) {
            plugin.getLogger().warning("[SOYS-Proxy] self-TLS 明文路由异常: " + t);
            ctx.close();
        }
    }

    private void dispatch(byte[] data, ChannelHandlerContext ctx) {
        if (pipe != null) pipe.writeClient(data);
        else if (selfServer != null) selfServer.feed(data, ctx);
    }

    private void route(ChannelHandlerContext ctx, String path) {
        ServerInfo target = resolveServer(path);
        if (target != null) {
            try {
                pipe = openNew(target, ctx);
            } catch (Exception e) {
                plugin.getLogger().warning("[SOYS-Proxy] 后端 " + target.getName() + " 连接失败: " + e);
                writeError(ctx, 502, "Backend " + target.getName() + " unreachable");
                return;
            }
            plugin.getLogger().info("[SOYS-Proxy] HTTPS 路由 -> 后端 " + target.getName() + " path=" + path);
            return;
        }
        selfServer = new SelfServer(plugin, config);
    }

    /**
     * 建一条到后端的 TLS 客户端连接（secure=true，后端强制 HTTPS）。
     * <p><b>为何不复用池化连接</b>：后端 HTTPS 栈对每个 TLS 连接的服务端会话有空闲超时（实测 ~10-15s），
     * 池化的常驻连接被借出服务第二条客户端时，其 SSL 会话可能已失效 → 后端返回空响应(size=0)。
     * 故采用“每条客户端连接一条新后端 TLS 连接（一对一，TLS 会话始终新鲜）”+ 并发建连<b>串行+最小间隔</b>，
     * 既避开 TLS 会话复用冲突，又消除微秒级密集建连触发的 spike-drop(ERR_CONNECTION_ABORTED)。
     */
    private BackendPipe openNew(ServerInfo target, ChannelHandlerContext ctx) throws Exception {
        long spacing = config.getConnectionSpacingMs();
        String k = target.getName() + ":tls";
        Object lk = lockMap.computeIfAbsent(k, x -> new Object());
        synchronized (lk) {
            long now = System.currentTimeMillis();
            Long last = lastMap.get(k);
            if (last != null && spacing > 0 && now - last < spacing) {
                try {
                    Thread.sleep(spacing - (now - last));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
            lastMap.put(k, System.currentTimeMillis());
        }
        return new BackendPipe(plugin, target, (int) Math.min(config.getPoolAcquireMs(), 10000), ctx, true);
    }

    /**
     * 解析目标子服：
     * <ul>
     *   <li>{@code /server/<name>/...} 或 {@code /srv/<name>/...} → 该子服（跨服转发）；</li>
     *   <li>其余路径（含主页 "/"）：home-server 为具体子服名 → 路由到该子服；home-server=self → 返回 null（自托管）。</li>
     * </ul>
     */
    private ServerInfo resolveServer(String path) {
        String prefix = null;
        if (path.startsWith("/server/")) prefix = "/server/";
        else if (path.startsWith("/srv/")) prefix = "/srv/";
        if (prefix != null) {
            int base = prefix.length();
            int i = path.indexOf('/', base);
            String name = (i < 0) ? path.substring(base) : path.substring(base, i);
            if (!name.isEmpty()) return proxy.getServerInfo(name);
            return null;
        }
        if (homeServer != null && !"self".equalsIgnoreCase(homeServer) && !homeServer.isEmpty()) {
            return proxy.getServerInfo(homeServer);
        }
        return null;
    }

    private void writeError(ChannelHandlerContext ctx, int code, String msg) {
        byte[] body = (code + " " + msg).getBytes(StandardCharsets.UTF_8);
        byte[] head = ("HTTP/1.1 " + code + " " + msg + "\r\n"
                + "Content-Type: text/plain; charset=utf-8\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
        byte[] out = new byte[head.length + body.length];
        System.arraycopy(head, 0, out, 0, head.length);
        System.arraycopy(body, 0, out, head.length, body.length);
        ctx.writeAndFlush(Unpooled.copiedBuffer(out));
        ctx.close();
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

    private static int indexOf(byte[] a, byte v) {
        for (int i = 0; i < a.length; i++) if (a[i] == v) return i;
        return -1;
    }
}
