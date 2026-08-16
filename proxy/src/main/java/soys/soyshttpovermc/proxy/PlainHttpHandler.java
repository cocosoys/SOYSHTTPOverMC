package soys.soyshttpovermc.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.plugin.Plugin;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * home-server=self 时，位于 {@code SslHandler} 之后的明文 HTTP 路由 handler。
 * <p>TLS 已由代理终止，这里拿到的是解密后的明文 HTTP 字节：
 * <ul>
 *   <li>{@code /server/<name>/...} → 经 {@link BackendPipe}（<b>TLS 客户端</b>模式）转发到该子服后端
 *       MC 端口，后端既有 HTTP 栈处理；响应明文回写，出站再由 SslHandler 加密给客户端；</li>
 *   <li>其余路径 → 由 {@link SelfServer} 用代理数据目录下的 web-root 托管。</li>
 * </ul>
 *
 * <p><b>keep-alive</b>：逐请求解析请求头边界（{@code \r\n\r\n}），对同一条客户端连接复用同一条后端
 * 管道（HTTP/1.1 持久连接），仅在目标子服变化时才关闭旧后端连接并新建；后端响应含
 * {@code Connection: keep-alive} 时浏览器无需为每资源重跑 TLS 握手。</p>
 *
 * <p><b>真实客户端 IP</b>：代理在转发每个请求前注入 {@code X-Forwarded-For}/{@code X-Real-IP}/
 * {@code X-Forwarded-Proto: https}，后端据此恢复访客真实 IP（限流/白名单/审计）。</p>
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

    /** 当前请求头块累积（含已到齐的请求行+头+分隔符，可能带部分 body）。 */
    private final ByteArrayOutputStream headerBuf = new ByteArrayOutputStream();
    /** 当前请求 body 剩余待转发字节数（>0 时收到的字节直接透传后端，不再解析）。 */
    private int bodyRemaining = 0;
    private ServerInfo currentTarget;
    private BackendPipe pipe;
    private SelfServer selfServer;
    private boolean selfMode = false;

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
            if (selfServer != null) {
                selfServer.feed(chunk, ctx);
                return;
            }
            if (bodyRemaining > 0) {
                // 当前请求 body 透传阶段
                int n = Math.min(chunk.length, bodyRemaining);
                if (pipe != null) pipe.writeClient(copyOf(chunk, 0, n));
                bodyRemaining -= n;
                if (n < chunk.length) {
                    // 余下字节是下一个请求的头块
                    headerBuf.reset();
                    headerBuf.write(chunk, n, chunk.length - n);
                    tryRoute(ctx);
                }
                return;
            }
            headerBuf.write(chunk);
            tryRoute(ctx);
        } catch (Throwable t) {
            plugin.getLogger().warning("[SOYS-Proxy] self-TLS 明文路由异常: " + t);
            ctx.close();
        }
    }

    /** 头块已收齐（含 \r\n\r\n）时解析目标并路由/转发；否则继续累积。 */
    private void tryRoute(ChannelHandlerContext ctx) {
        byte[] raw = headerBuf.toByteArray();
        int sep = headerEnd(raw);
        if (sep < 0) {
            if (raw.length > 8192) {
                // 异常超长头块 → 丢弃
                headerBuf.reset();
                ctx.close();
            }
            return; // 等待头块收齐
        }
        int headerLen = sep;            // 头块长度（到分隔符前）
        int sepLen = (sep > 0 && raw[sep - 1] == '\r') ? 4 : 2;
        String firstLine;
        String path;
        int contentLength = 0;
        try {
            String headerText = new String(raw, 0, headerLen, StandardCharsets.US_ASCII);
            String[] lines = headerText.split("\r\n");
            if (lines.length == 0) return;
            String[] rl = lines[0].split(" ");
            if (rl.length < 2) return;
            firstLine = lines[0];
            path = rl[1];
            for (int l = 1; l < lines.length; l++) {
                int c = lines[l].indexOf(':');
                if (c > 0 && lines[l].substring(0, c).trim().equalsIgnoreCase("Content-Length")) {
                    try { contentLength = Integer.parseInt(lines[l].substring(c + 1).trim()); } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            headerBuf.reset();
            ctx.close();
            return;
        }

        ServerInfo target = resolveServer(path);
        if (target == null) {
            if (!selfMode) {
                selfMode = true;
                selfServer = new SelfServer(plugin, config);
            }
            // self 模式直接喂入（含头块，SelfServer 自行解析首行）
            selfServer.feed(headerBuf.toByteArray(), ctx);
            resetRequest();
            return;
        }

        // 目标变化（或尚未建连）→ 关闭旧后端连接（仅后端，保留客户端）并新建
        if (pipe != null && (currentTarget == null || !currentTarget.equals(target))) {
            pipe.closeBackendOnly();
            pipe = null;
        }
        if (pipe == null) {
            try {
                pipe = openNew(target, ctx);
                currentTarget = target;
            } catch (Exception e) {
                plugin.getLogger().warning("[SOYS-Proxy] 后端 " + target.getName() + " 连接失败: " + e);
                writeError(ctx, 502, "Backend " + target.getName() + " unreachable");
                resetRequest();
                return;
            }
        }

        // 注入 X-Forwarded-For / X-Real-IP / X-Forwarded-Proto，再转发（头块 + 已到 body 部分）
        byte[] outBytes = buildForwardBytes(raw, headerLen, sepLen, clientIp(ctx));
        pipe.writeClient(outBytes);
        int bodyInBuf = raw.length - (headerLen + sepLen);
        bodyRemaining = contentLength - bodyInBuf;
        if (bodyRemaining < 0) bodyRemaining = 0;
        resetRequest();
    }

    /** 清空当前请求累积缓冲（保留已建连的 pipe / selfServer）。 */
    private void resetRequest() {
        headerBuf.reset();
    }

    /** 构造转发字节：在请求行后插入 XFF 头，原样保留其余头与已到 body。 */
    private static byte[] buildForwardBytes(byte[] raw, int headerLen, int sepLen, String ip) {
        // 在请求行（第一个 \r\n）之后插入
        int fl = indexOf(raw, 0, headerLen, (byte) '\n');
        if (fl < 0) fl = headerLen;
        ByteArrayOutputStream out = new ByteArrayOutputStream(raw.length + 80);
        out.write(raw, 0, fl);                       // 请求行（含末尾 \r\n 的一部分）
        // 确保请求行以 \r\n 结束
        if (fl < raw.length && raw[fl] == '\n') {
            if (fl == 0 || raw[fl - 1] != '\r') out.write('\r');
            out.write('\n');
        }
        String xff = "X-Forwarded-For: " + ip + "\r\n"
                + "X-Real-IP: " + ip + "\r\n"
                + "X-Forwarded-Proto: https\r\n";
        byte[] xffBytes = xff.getBytes(StandardCharsets.US_ASCII);
        out.write(xffBytes, 0, xffBytes.length);
        out.write(raw, fl + (raw[fl] == '\n' ? 1 : 0), raw.length - (fl + (raw[fl] == '\n' ? 1 : 0)));
        return out.toByteArray();
    }

    private static String clientIp(ChannelHandlerContext ctx) {
        try {
            java.net.SocketAddress sa = ctx.channel().remoteAddress();
            if (sa instanceof InetSocketAddress) {
                return ((InetSocketAddress) sa).getAddress().getHostAddress();
            }
        } catch (Exception ignored) {
        }
        return "127.0.0.1";
    }

    /**
     * 建一条到后端的 TLS 客户端连接（secure=true，后端强制 HTTPS）。
     * 每条客户端连接对应一条后端 TLS 连接，并以串行最小间隔建连消除 spike-drop。
     * keep-alive 下该后端连接被同一条客户端连接复用（目标不变时）。
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

    // ===== 字节工具 =====

    private static int headerEnd(byte[] a) {
        int i = indexOfSeq(a, 0, a.length, new byte[]{'\r', '\n', '\r', '\n'});
        if (i >= 0) return i;
        return indexOfSeq(a, 0, a.length, new byte[]{'\n', '\n'});
    }

    private static int indexOf(byte[] a, int from, int to, byte v) {
        for (int i = from; i < to; i++) if (a[i] == v) return i;
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

    private static byte[] copyOf(byte[] a, int from, int len) {
        byte[] b = new byte[len];
        System.arraycopy(a, from, b, 0, len);
        return b;
    }
}
