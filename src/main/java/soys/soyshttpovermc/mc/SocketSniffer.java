package soys.soyshttpovermc.mc;

import soys.soyshttpovermc.log.LogKit;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.ReferenceCountUtil;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import soys.soyshttpovermc.annotations.RequestMethod;
import soys.soyshttpovermc.api.event.GatewayAccessDeniedEvent;
import soys.soyshttpovermc.api.event.GatewayRequestEvent;
import soys.soyshttpovermc.api.event.GatewayRequestServedEvent;
import soys.soyshttpovermc.gateway.GatewayContext;
import soys.soyshttpovermc.gateway.GatewayFilter;
import soys.soyshttpovermc.gateway.Credential;
import soys.soyshttpovermc.gateway.PolicyResult;
import soys.soyshttpovermc.http.HttpMcTranslator;
import soys.soyshttpovermc.proto.FrameProto;
import soys.soyshttpovermc.web.RequestStats;

import javax.net.ssl.SSLEngine;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * 同端口嗅探器：在 Spigot 自身监听的 socket 上做 Geyser 式流量分流。
 *
 * 原理（深度挂接 Spigot 的 Netty pipeline，访问端口 == Spigot 的 server-port）：
 *  - Spigot 的 {@code ServerConnection} 把每个监听端口存在字段 {@code g}(List<ChannelFuture>)，
 *    其 channel 是父(Server)Channel；每接受一个子连接，父 Channel 的 pipeline 会以
 *    {@code channelRead(childChannel)} 的形式把子 Channel 透传给内部的 ServerBootstrapAcceptor。
 *  - 我们在父 Channel pipeline 最前插入 ParentInjectorHandler：拿到每个子 Channel 后，
 *    在其 pipeline 最前插入 HttpSnifferHandler，再向下游 fire（让 Spigot 的 MC 解码器照常工作）。
 *  - HttpSnifferHandler 嗅探首包，三协议分流（25564 为三协议端口）：
 *      明文 HTTP（A-Z 方法词）→ 网关策略链 → Bot 隧道转换并写回 HTTP 响应；
 *      TLS（0x16 0x03）→ 就地 addFirst(SslHandler) 解密 → 同一策略链 → 服务；
 *      MC（0xFE / varint+0x00）→ 原样放行给 Spigot 的 MC 解码器。
 *
 * 关键约束：本类使用 io.netty.* 必须复用 Spigot 运行时的 netty（pom 里 netty 为 provided），
 * 否则嗅探器里的 ByteBuf 与 Spigot pipeline 里的不是同一个 Class，instanceof 失效。
 */
public class SocketSniffer {

    /** 判断隧道是否就绪（Bot 已连接并 REGISTER 通道），未就绪时 HTTP 返回 503 */
    public interface ReadyChecker {
        boolean isReady();
    }

    private static final String[] METHODS = RequestMethod.toList();
    private static final int CLASSIFY_HTTP = 1;
    private static final int CLASSIFY_MC = 2;
    private static final int CLASSIFY_TLS = 3;
    private static final int CLASSIFY_UNKNOWN = 0;

    private final JavaPlugin plugin;
    private final Logger log;
    private final HttpMcTranslator translator;
    private final BooleanSupplier ready;
    private final int maxBodyBytes;
    private final RequestStats stats;
    private volatile GatewayFilter gateway;
    /** TLS 引擎提供者；null 表示未启用 HTTPS（0x16 0x03 不再判 TLS，直接按 MC 处理） */
    private volatile Supplier<SSLEngine> tlsEngineSupplier;

    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "HTTP-Over-MC-Sniffer");
        t.setDaemon(true);
        return t;
    });
    private final java.util.List<Channel> installedParents = new java.util.ArrayList<>();

    public SocketSniffer(JavaPlugin plugin, HttpMcTranslator translator, BooleanSupplier ready,
                         int maxBodyBytes, RequestStats stats,
                         GatewayFilter gateway, Supplier<SSLEngine> tlsEngineSupplier) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
        this.translator = translator;
        this.ready = ready;
        this.maxBodyBytes = maxBodyBytes;
        this.stats = stats;
        this.gateway = gateway;
        this.tlsEngineSupplier = tlsEngineSupplier;
    }

    /** 热重载网关策略链（/soyshttp reload 调用） */
    public void setGateway(GatewayFilter gateway) {
        this.gateway = gateway;
    }

    /** 热重载 TLS 引擎提供者（/soyshttp reload 调用） */
    public void setTlsEngineSupplier(Supplier<SSLEngine> tlsEngineSupplier) {
        this.tlsEngineSupplier = tlsEngineSupplier;
    }

    /** 在 Spigot 自身监听的端口上安装 HTTP 嗅探器 */
    public void install() {
        try {
            Object serverConnection = getServerConnection();
            if (serverConnection == null) {
                LogKit.error("[HTTP-Over-MC] 无法获取 ServerConnection，HTTP 同端口嗅探器安装失败");
                return;
            }
            @SuppressWarnings("unchecked")
            List<io.netty.channel.ChannelFuture> futures =
                    (List<io.netty.channel.ChannelFuture>) getField(serverConnection, "g");
            if (futures == null) {
                LogKit.error("[HTTP-Over-MC] 无法获取监听 channel 列表（字段 g），安装失败");
                return;
            }
            int n = 0;
            for (io.netty.channel.ChannelFuture cf : futures) {
                if (cf == null) continue;
                Channel parent = cf.channel();
                if (parent == null || !parent.isActive()) continue;
                ChannelPipeline pipe = parent.pipeline();
                if (pipe.get("http-over-mc-parent") == null) {
                    pipe.addFirst("http-over-mc-parent", new ParentInjectorHandler());
                    installedParents.add(parent);
                    n++;
                    LogKit.info("[HTTP-Over-MC] 已在 Spigot 监听端口 " + parent.localAddress() + " 上安装 HTTP 嗅探器");
                }
            }
            if (n == 0) {
                LogKit.warn("[HTTP-Over-MC] 未找到任何活跃监听端口，嗅探器未生效（请确认 Spigot 已绑定端口）");
            } else {
                LogKit.info("[HTTP-Over-MC] 同端口嗅探器已安装：" + n + " 个端口。访问端口 == Spigot server-port，MC 与 HTTP 共用");
            }
        } catch (Throwable t) {
            LogKit.error("[HTTP-Over-MC] 安装嗅探器异常", t);
        }
    }

    public void uninstall() {
        for (Channel parent : installedParents) {
            try {
                ChannelHandler h = parent.pipeline().get("http-over-mc-parent");
                if (h != null) parent.pipeline().remove(h);
            } catch (Throwable ignored) {
            }
        }
        installedParents.clear();
        executor.shutdownNow();
    }

    // ===== 父 Channel 处理器：为每一个新子连接注入 HttpSnifferHandler =====
    private class ParentInjectorHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof Channel) {
                Channel child = (Channel) msg;
                ChannelPipeline cp = child.pipeline();
                if (cp.get("http-over-mc-sniffer") == null) {
                    cp.addFirst("http-over-mc-sniffer", new HttpSnifferHandler());
                }
            }
            ctx.fireChannelRead(msg);
        }
    }

    // ===== 子连接处理器：嗅探首包决定 HTTP / TLS / MC =====
    private class HttpSnifferHandler extends ChannelInboundHandlerAdapter {
        private ByteBuf buffer;
        private boolean decided = false;
        private boolean isHttp = false;
        private boolean mcMode = false;
        private boolean httpHandled = false;
        /** true=连接已就地升级为 TLS（后续收到的都是解密后的明文 HTTP） */
        private boolean tlsMode = false;

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
            buffer = ctx.alloc().buffer(1024);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            releaseBuffer();
            ctx.fireChannelInactive();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            if (mcMode) {
                ctx.fireExceptionCaught(cause);
            } else {
                ctx.close();
            }
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (mcMode) {
                ctx.fireChannelRead(msg);
                return;
            }
            if (httpHandled) {
                // HTTP 已处理并关闭，丢弃后续字节
                ReferenceCountUtil.release(msg);
                return;
            }
            if (!(msg instanceof ByteBuf)) {
                ctx.fireChannelRead(msg);
                return;
            }
            ByteBuf in = (ByteBuf) msg;
            if (buffer == null) {
                buffer = ctx.alloc().buffer(1024);
            }
            buffer.writeBytes(in);
            in.release();

            if (!decided) {
                int c = classify(buffer);
                if (c == CLASSIFY_HTTP) {
                    decided = true;
                    isHttp = true;
                } else if (c == CLASSIFY_TLS) {
                    // 就地 TLS 升级：管道最前加 SslHandler，把缓冲的 ClientHello 首包重放给它，
                    // 握手完成后解密出的明文 HTTP 会以 tlsMode 形式继续流入本 handler。
                    decided = true;
                    isHttp = true;
                    tlsMode = true;
                    upgradeToTls(ctx);
                    return;
                } else if (c == CLASSIFY_MC) {
                    switchToMc(ctx);
                    return;
                } else {
                    // 数据不足以判定：首字节像方法但行未收全，继续等；
                    // 但若缓冲区已很大仍无完整请求行，按 MC 处理避免饿死。
                    if (buffer.readableBytes() > 64 * 1024) {
                        switchToMc(ctx);
                    }
                    return;
                }
            }

            if (isHttp) {
                RequestParsed parsed = tryParseHttp(buffer);
                if (parsed == null) {
                    if (buffer.readableBytes() > maxBodyBytes + 1024 * 1024) {
                        writeRaw(ctx, statusLine(413) + "Payload Too Large\r\n", 413, tlsMode);
                    }
                    return; // 等待更多数据
                }
                // 已提取完整请求，buffer 不再需要
                releaseBuffer();
                httpHandled = true;
                final boolean tls = tlsMode;
                executor.submit(() -> handleHttp(ctx, parsed, tls));
            }
        }

        /** 就地 TLS 升级：管道最前插入 SslHandler 并重放缓冲的 ClientHello 首包。 */
        private void upgradeToTls(ChannelHandlerContext ctx) {
            SSLEngine engine = tlsEngineSupplier.get();
            SslHandler ssl = new SslHandler(engine);
            ctx.pipeline().addFirst("http-over-mc-ssl", ssl);
            // 缓冲首包所有权移交给 SslHandler（它负责消费并驱动握手），本 handler 置空。
            ByteBuf replay = buffer;
            buffer = null;
            ctx.pipeline().fireChannelRead(replay);
        }

        private void switchToMc(ChannelHandlerContext ctx) {
            mcMode = true;
            ByteBuf copy = buffer;
            buffer = null;
            // 把已缓冲的字节原样交给下游（Spigot 的 MC 解码器），后续读取直接放行
            ctx.fireChannelRead(copy);
            try {
                ctx.pipeline().remove(this);
            } catch (Throwable ignored) {
            }
        }

        private void releaseBuffer() {
            if (buffer != null) {
                buffer.release();
                buffer = null;
            }
        }
    }

    // ===== HTTP 请求解析 =====
    private static class RequestParsed {
        String method;
        String path;
        String version;
        Map<String, String> headers = new HashMap<>();
        byte[] body;
    }

    /** 嗅探分类：依据首包前几个字节判断为明文 HTTP / TLS / MC */
    private int classify(ByteBuf buf) {
        int len = buf.readableBytes();
        if (len == 0) return CLASSIFY_UNKNOWN;
        int idx = buf.readerIndex();
        byte b0 = buf.getByte(idx);

        // TLS 签名：首字节 0x16(Handshake 记录) + 0x03(版本) + 0x01|0x03(TLS1.0/1.1/1.2/1.3)。
        // 注意 0x16 也可能是 MC 握手 varint 长度（0x16=22）的巧合，但 MC 第 2 字节必为包 ID 0x00，
        // 而 TLS 第 2 字节恒为 0x03 —— 0x16 0x03 组合在合法 MC 流中不存在，可稳定区分。
        if (tlsEngineSupplier != null && b0 == 0x16) {
            if (len < 3) return CLASSIFY_UNKNOWN; // 等第 2-3 字节再定
            byte b1 = buf.getByte(idx + 1);
            byte b2 = buf.getByte(idx + 2);
            if (b1 == 0x03 && (b2 == 0x01 || b2 == 0x03)) return CLASSIFY_TLS;
            // 否则是 MC 长度巧合，落入下方 MC 判定
        }

        // HTTP 方法首字母必为大写字母；MC 握手首字节是 varint 长度，绝大多数非字母
        if (b0 < 'A' || b0 > 'Z') return CLASSIFY_MC;

        // 扫描方法 token（直到空格，最多 16 字节）
        int i = 0;
        StringBuilder tok = new StringBuilder();
        boolean tokenComplete = false;
        for (; i < len && i < 16; i++) {
            byte b = buf.getByte(idx + i);
            if (b == ' ') { tokenComplete = true; break; }
            if (b < 'A' || b > 'Z') return CLASSIFY_MC; // 出现非大写字母（且非空格）→ 必为非 HTTP
            tok.append((char) b);
        }
        if (!tokenComplete) {
            // 缓冲区在方法 token 结束前就用完（且前面全是字母）：可能是 "GE" 这样的方法前缀，
            // 也可能是 MC 巧合，不足以判定 → 继续等更多数据。
            return CLASSIFY_UNKNOWN;
        }
        boolean known = false;
        for (String m : METHODS) {
            if (m.equals(tok.toString())) { known = true; break; }
        }
        if (!known) return CLASSIFY_MC;

        // 已知方法 + 后接空格：在首行剩余部分查找 " HTTP/" 确认是 HTTP 请求行
        int j = i + 1; // 跳过空格
        int k = j;
        int limit = Math.min(len, j + 200);
        boolean foundNewline = false;
        for (; k < limit; k++) {
            if (buf.getByte(idx + k) == '\n') { foundNewline = true; break; }
        }
        String line = buf.toString(idx + j, Math.max(0, k - j), StandardCharsets.US_ASCII);
        if (line.contains(" HTTP/")) return CLASSIFY_HTTP;
        if (foundNewline) return CLASSIFY_MC; // 首行已完整但无 " HTTP/" → 非 HTTP
        return CLASSIFY_UNKNOWN;              // 首行尚未收全 → 继续等
    }

    /** 尝试从缓冲区解析完整 HTTP 请求；不完整返回 null */
    private RequestParsed tryParseHttp(ByteBuf buf) {
        int len = buf.readableBytes();
        int idx = buf.readerIndex();
        int headerEnd = indexOf(buf, idx, len, new byte[]{'\r', '\n', '\r', '\n'});
        int sepLen;
        if (headerEnd >= 0) {
            sepLen = 4;
        } else {
            headerEnd = indexOf(buf, idx, len, new byte[]{'\n', '\n'});
            if (headerEnd < 0) return null;
            sepLen = 2;
        }
        int headerLen = headerEnd - idx;
        if (headerLen < 0) return null;
        byte[] headerBytes = new byte[headerLen];
        buf.getBytes(idx, headerBytes);
        String headerText = new String(headerBytes, StandardCharsets.US_ASCII);
        String[] lines = headerText.split("\r\n");
        if (lines.length == 0) return null;
        String[] reqLine = lines[0].split(" ");
        if (reqLine.length < 3) return null;
        String method = reqLine[0];
        String path = reqLine[1];
        String version = reqLine[2];

        Map<String, String> headers = new HashMap<>();
        int contentLength = 0;
        for (int l = 1; l < lines.length; l++) {
            int colon = lines[l].indexOf(':');
            if (colon > 0) {
                String k = lines[l].substring(0, colon).trim();
                String v = lines[l].substring(colon + 1).trim();
                headers.put(k, v);
                if (k.equalsIgnoreCase("Content-Length")) {
                    try {
                        contentLength = Integer.parseInt(v);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        int bodyStart = headerEnd + sepLen;
        int available = len - bodyStart;
        if (available < contentLength) return null; // body 未收全，等更多
        if (contentLength < 0) contentLength = 0;
        byte[] body = new byte[contentLength];
        if (contentLength > 0) buf.getBytes(bodyStart, body);

        RequestParsed r = new RequestParsed();
        r.method = method;
        r.path = path;
        r.version = version;
        r.headers = headers;
        r.body = body;
        return r;
    }

    private static int indexOf(ByteBuf buf, int from, int to, byte[] seq) {
        int sl = seq.length;
        if (sl == 0) return from;
        for (int i = from; i + sl <= to; i++) {
            boolean ok = true;
            for (int j = 0; j < sl; j++) {
                if (buf.getByte(i + j) != seq[j]) {
                    ok = false;
                    break;
                }
            }
            if (ok) return i;
        }
        return -1;
    }

    // ===== HTTP 处理（在线程池中执行，不阻塞 Netty IO 线程）=====
    private void handleHttp(ChannelHandlerContext ctx, RequestParsed p, boolean tls) {
        long t0 = System.nanoTime();
        int code = 200;
        final String ip = clientIp(ctx);
        fire(new GatewayRequestEvent(p.method, p.path, ip, tls, p.headers));
        try {
            // 1) 网关安全策略链：任一策略拒绝即短路，直接写响应，不占隧道、无 30s 超时风险
            GatewayFilter gw = gateway;
            if (gw != null) {
                // 预先解析凭证（权限控制抽象）：携带有效 X-API-Key 时允许明文 HTTP 旁路 HTTPS 强制升级
                Credential cred = gw.resolveCredential(p.headers);
                // ④ 鉴权端点强制/建议 TLS：带凭证却走明文 HTTP（且服务端支持 TLS）属明文泄露风险，
                // 按确认仅日志告警、不拒绝（宽松兼容旧客户端）。
                if (cred != null && !tls && tlsEngineSupplier != null) {
                    LogKit.warn("[HTTP-Over-MC] 凭证经明文 HTTP 传输（建议启用 TLS）: " + ip);
                }
                GatewayContext gctx = new GatewayContext(p.method, p.path, p.headers, ip, tls, cred);
                GatewayFilter.Outcome oc = gw.filterDetailed(gctx);
                PolicyResult res = oc.result;
                if (!res.isAllow()) {
                    code = res.getStatusCode();
                    fire(new GatewayAccessDeniedEvent(p.method, p.path, ip, tls,
                            oc.policy == null ? "unknown" : oc.policy.name(), code, res.getBody()));
                    writeDeny(ctx, res, tls);
                    return;
                }
            }
            if (!ready.getAsBoolean()) {
                code = 503;
                writeRaw(ctx, statusLine(503) + "HTTP-Over-MC tunnel not ready\r\n", 503, tls);
                return;
            }
            FrameProto.HttpResponseFrame resp = translator.translate(p.method, p.path, p.headers, p.body);
            code = resp.getStatusCode();
            byte[] body = resp.getBody().toByteArray();
            String contentType = resp.getHeadersMap().getOrDefault("Content-Type", "application/octet-stream");

            StringBuilder sb = new StringBuilder();
            sb.append("HTTP/1.1 ").append(code).append(' ').append(statusText(code)).append("\r\n");
            sb.append("Content-Type: ").append(contentType).append("\r\n");
            sb.append("Content-Length: ").append(body.length).append("\r\n");
            sb.append("Connection: close\r\n");
            sb.append("\r\n");
            byte[] head = sb.toString().getBytes(StandardCharsets.US_ASCII);

            ByteBuf out = Unpooled.buffer(head.length + body.length);
            out.writeBytes(head);
            out.writeBytes(body);
            writeResponse(ctx, out, tls);
        } catch (Exception e) {
            code = 502;
            LogKit.warn("[HTTP-Over-MC] 隧道转换失败: " + e, e);
            writeRaw(ctx, statusLine(502) + "HTTP-Over-MC tunnel error: "
                    + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()) + "\r\n", 502, tls);
        } finally {
            long dtUs = (System.nanoTime() - t0) / 1000;
            stats.recordRequest(p.method, p.path, code, dtUs);
            fire(new GatewayRequestServedEvent(p.method, p.path, ip, tls, code, dtUs));
        }
    }

    /** 触发网关事件（异步线程；监听器异常不影响请求处理） */
    private static void fire(org.bukkit.event.Event e) {
        try {
            Bukkit.getPluginManager().callEvent(e);
        } catch (Throwable ignored) {
        }
    }

    /**
     * 写出 HTTP 响应。
     * - 明文连接：经管道 HeadContext 直接写原始字节到 socket，绕过 Spigot 的 MC 出站编码器
     *   （prepender/encoder 会给响应套上 MC 包帧，导致 curl 收到乱码报 HTTP/0.9）；
     * - TLS 连接：必须经本 handler 的 ctx 出站（先过最前的 SslHandler 加密再出 socket），
     *   绝不能走 firstContext()，否则会绕过 ssl 明文外发。
     */
    private void writeResponse(ChannelHandlerContext ctx, ByteBuf out, boolean tls) {
        if (tls) {
            ctx.writeAndFlush(out).addListener(ChannelFutureListener.CLOSE);
        } else {
            ctx.pipeline().firstContext().writeAndFlush(out).addListener(ChannelFutureListener.CLOSE);
        }
    }

    private void writeRaw(ChannelHandlerContext ctx, String text, int code, boolean tls) {
        writeResponse(ctx, Unpooled.wrappedBuffer(text.getBytes(StandardCharsets.US_ASCII)), tls);
    }

    /** 写出网关策略拒绝响应（401/403/426/429/500），附带策略指定的响应头。 */
    private void writeDeny(ChannelHandlerContext ctx, PolicyResult res, boolean tls) {
        byte[] body = res.getBodyBytes();
        StringBuilder sb = new StringBuilder();
        sb.append(statusLine(res.getStatusCode()));
        for (Map.Entry<String, String> h : res.getHeaders().entrySet()) {
            sb.append(h.getKey()).append(": ").append(h.getValue()).append("\r\n");
        }
        sb.append("Content-Type: text/plain; charset=utf-8\r\n");
        sb.append("Content-Length: ").append(body.length).append("\r\n");
        sb.append("Connection: close\r\n\r\n");
        ByteBuf out = Unpooled.buffer(sb.length() + body.length);
        out.writeBytes(sb.toString().getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(body);
        writeResponse(ctx, out, tls);
    }

    /** TCP socket 上的客户端源 IP（HTTPS/HTTP 均为真实对端；无代理时即公网 IP） */
    private static String clientIp(ChannelHandlerContext ctx) {
        try {
            java.net.SocketAddress sa = ctx.channel().remoteAddress();
            if (sa instanceof InetSocketAddress) {
                return ((InetSocketAddress) sa).getAddress().getHostAddress();
            }
        } catch (Exception ignored) {
        }
        return "0.0.0.0";
    }

    private static String statusLine(int code) {
        return "HTTP/1.1 " + code + " " + statusText(code) + "\r\n";
    }

    private static String statusText(int code) {
        switch (code) {
            case 200: return "OK";
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 403: return "Forbidden";
            case 413: return "Payload Too Large";
            case 426: return "Upgrade Required";
            case 429: return "Too Many Requests";
            case 500: return "Internal Server Error";
            case 502: return "Bad Gateway";
            case 503: return "Service Unavailable";
            default: return "Status";
        }
    }

    // ===== 反射辅助 =====
    private Object getServerConnection() {
        try {
            Object craftServer = Bukkit.getServer();
            Method getServer = craftServer.getClass().getMethod("getServer");
            Object mcServer = getServer.invoke(craftServer);
            Method getServerConnection = mcServer.getClass().getMethod("getServerConnection");
            return getServerConnection.invoke(mcServer);
        } catch (Throwable t) {
            LogKit.error("[HTTP-Over-MC] 反射获取 ServerConnection 失败", t);
            return null;
        }
    }

    private static Object getField(Object obj, String name) {
        try {
            Field f = obj.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(obj);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
