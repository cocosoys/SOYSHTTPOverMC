package com.github.cocosoys.mc.soyshttpovermc.web.http.sniffer;
import com.github.cocosoys.mc.soyshttpovermc.enums.RequestMethod;
import com.github.cocosoys.mc.soyshttpovermc.enums.SnifferChannelState;
import lombok.CustomLog;

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

import com.github.cocosoys.mc.soyshttpovermc.api.event.GatewayAccessDeniedEvent;
import com.github.cocosoys.mc.soyshttpovermc.api.event.GatewayRequestEvent;
import com.github.cocosoys.mc.soyshttpovermc.api.event.GatewayRequestServedEvent;
import com.github.cocosoys.mc.soyshttpovermc.web.http.HttpRequestHandler;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.GatewayContext;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.GatewayFilter;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.Credential;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.PolicyResult;
import com.github.cocosoys.mc.soyshttpovermc.web.proto.FrameProto;
import com.github.cocosoys.mc.soyshttpovermc.util.HttpFrames;
import com.github.cocosoys.mc.soyshttpovermc.web.RequestStats;
import com.github.cocosoys.mc.soyshttpovermc.web.MimeTypes;
import com.github.cocosoys.mc.soyshttpovermc.web.ApiRequestContext;

import javax.net.ssl.SSLEngine;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.zip.GZIPOutputStream;

/**
 * 同端口嗅探器：在 Spigot 自身监听的 socket 上做 Geyser 式流量分流。
 *
 * 原理（深度挂接 Spigot 的 Netty pipeline，访问端口 == Spigot 的 server-port）：
 *  - Spigot 的 {@code ServerConnection} 把每个监听端口存在字段 {@code g}(List<ChannelFuture>)，
 *    其 channel 是父(Server)Channel；每接受一个子连接，父 Channel 的 pipeline 会以
 *    {@code channelRead(childChannel)} 的形式把子 Channel 透传给内部的 ServerBootstrapAcceptor。
 *  - 我们在父 Channel pipeline 最前插入 ParentInjectorHandler：拿到每个子 Channel 后，
 *    在其 pipeline 最前插入 HttpSnifferHandler，再向下游 fire（让 Spigot 的 MC 解码器照常工作）。
 *  - HttpSnifferHandler 嗅探首包，三协议分流（server-port 即三协议端口）：
 *      明文 HTTP（A-Z 方法词）→ 网关策略链 → HTTP 后端处理并写回 HTTP 响应；
 *      TLS（0x16 0x03）→ 就地 addFirst(SslHandler) 解密 → 同一策略链 → 服务；
 *      MC（0xFE / varint+0x00）→ 原样放行给 Spigot 的 MC 解码器。
 *
 * 关键约束：本类使用 io.netty.* 必须复用 Spigot 运行时的 netty（pom 里 netty 为 provided），
 * 否则嗅探器里的 ByteBuf 与 Spigot pipeline 里的不是同一个 Class，instanceof 失效。
 *
 * <p>响应优化（省流量 + 快速加载）：
 *  - gzip 压缩：当客户端 {@code Accept-Encoding} 含 gzip 且响应体为可压缩类型且超过阈值时压缩；
 *  - ETag + 304：GET 200 响应计算实体摘要，命中 {@code If-None-Match} 时直接 304（无响应体）；
 *  - Cache-Control：静态资源 {@code public, max-age=300}，API/鉴权 {@code no-store}；
 *  - keep-alive：HTTP/1.1 默认复用同一条连接处理多个请求（autoRead 暂停/恢复 + 空闲关闭）。
 */
@CustomLog
public class SocketSniffer {

    /** 判断 HTTP 后端是否就绪，未就绪时 HTTP 返回 503 */
    public interface ReadyChecker {
        boolean isReady();
    }

    private static final String[] METHODS = RequestMethod.toList();

    /**
     * 静态预加载内部类：避免在 Netty EventLoop 线程中延迟加载时，
     * 因线程上下文类加载器不是 PluginClassLoader 而抛出 ClassNotFoundException。
     * 触发时机：SocketSniffer 类首次被加载时（插件初始化阶段，类加载器正确）。
     */
    static {
        try {
            Class.forName(ParentInjectorHandler.class.getName());
            Class.forName(HttpSnifferHandler.class.getName());
            Class.forName(RequestParsed.class.getName());
        } catch (ClassNotFoundException ignored) {
            // 内部类必然存在，忽略
        }
    }

    /** 低于此字节数的响应体不压缩（压缩收益低于开销）。 */
    private static final int COMPRESS_THRESHOLD = 512;
    /** keep-alive 空闲超时（秒）：超过未收到下一个请求则关闭连接，避免长连接泄漏。
     *  可经 config 的 {@code sniffer.keep-alive-idle-seconds} 调整；适当延长可减少连接断开重建，
     *  从而减少自签证书场景下浏览器（Chrome）因新建连接重校验证书而拒绝（certificate_unknown）的触发点。 */
    private final int keepAliveIdleSeconds;

    private final JavaPlugin plugin;
    private final HttpRequestHandler handler;
    private final BooleanSupplier ready;
    private final int maxBodyBytes;
    private final RequestStats stats;
    private volatile GatewayFilter gateway;
    /** TLS 引擎提供者；null 表示未启用 HTTPS（0x16 0x03 不再判 TLS，直接按 MC 处理） */
    private volatile Supplier<SSLEngine> tlsEngineSupplier;
    /** 是否信任前置代理注入的 X-Forwarded-For（仅当本服确在可信代理之后时开启，避免客户端伪造）。 */
    private final boolean trustProxy;

    /**
     * 有界线程池（秒杀防护）：并发数 = {@code sniffer.http-concurrency}（默认 4），
     * 每个任务阻塞在隧道 future.get(30s) 上；工作队列 = {@code sniffer.http-queue-size}（默认 8）。
     * 队列满且全部 worker 忙 → 新任务被拒绝 → 立即 503 快速失败（不再接收，直到有空位），
     * 避免高并发下任务无限堆积打爆内存。
     */
    private final ExecutorService executor;
    /** 并发上限（构造注入；用于拒绝策略提示） */
    private final int httpConcurrency;
    /** keep-alive 空闲关闭调度（单线程，守护）。 */
    private static final ScheduledExecutorService idleExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "HTTP-Over-MC-KeepAlive-Idle");
        t.setDaemon(true);
        return t;
    });
    private final java.util.List<Channel> installedParents = new java.util.ArrayList<>();

    public SocketSniffer(JavaPlugin plugin, HttpRequestHandler handler, BooleanSupplier ready,
                         int maxBodyBytes, RequestStats stats,
                         GatewayFilter gateway, Supplier<SSLEngine> tlsEngineSupplier,
                         boolean trustProxy) {
        this(plugin, handler, ready, maxBodyBytes, stats, gateway, tlsEngineSupplier, trustProxy, 1, 8, 30);
    }

    public SocketSniffer(JavaPlugin plugin, HttpRequestHandler handler, BooleanSupplier ready,
                         int maxBodyBytes, RequestStats stats,
                         GatewayFilter gateway, Supplier<SSLEngine> tlsEngineSupplier,
                         boolean trustProxy, int concurrency, int queueSize, int keepAliveIdleSeconds) {
        this.plugin = plugin;
        this.handler = handler;
        this.ready = ready;
        this.maxBodyBytes = maxBodyBytes;
        this.keepAliveIdleSeconds = Math.max(1, keepAliveIdleSeconds);
        this.httpConcurrency = Math.max(1, concurrency);
        int queue = Math.max(1, queueSize);
        this.executor = new ThreadPoolExecutor(
                this.httpConcurrency, this.httpConcurrency, 60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(queue),
                r -> {
                    Thread t = new Thread(r, "HTTP-Over-MC-Sniffer");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.AbortPolicy()); // 拒绝：提交处捕获并直接 503
        log.infoT("log.sniffer.pool", "嗅探器线程池: concurrency={0} queue={1}", this.httpConcurrency, queue);
        this.stats = stats;
        this.gateway = gateway;
        this.tlsEngineSupplier = tlsEngineSupplier;
        this.trustProxy = trustProxy;
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
                log.errorT("log.sniffer.no-server-connection", "无法获取 ServerConnection，HTTP 同端口嗅探器安装失败");
                return;
            }
            @SuppressWarnings("unchecked")
            List<io.netty.channel.ChannelFuture> futures =
                    (List<io.netty.channel.ChannelFuture>) getField(serverConnection, "g");
            if (futures == null) {
                log.errorT("log.sniffer.no-channel-list", "无法获取监听 channel 列表（字段 g），安装失败");
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
                    log.infoT("log.sniffer.installed-on-port", "已在 Spigot 监听端口 {0} 上安装 HTTP 嗅探器", parent.localAddress());
                }
            }
            if (n == 0) {
                log.warnT("log.sniffer.no-active-port", "未找到任何活跃监听端口，嗅探器未生效（请确认 Spigot 已绑定端口）");
            } else {
                log.infoT("log.sniffer.installed", "同端口嗅探器已安装：{0} 个端口。访问端口 == Spigot server-port，MC 与 HTTP 共用", n);
            }
        } catch (Throwable t) {
            log.errorT("log.sniffer.install-error", "安装嗅探器异常", t);
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
                    // 确保使用插件的类加载器创建内部类实例（Netty EventLoop 线程的上下文类加载器可能不对）
                    ClassLoader oldCl = Thread.currentThread().getContextClassLoader();
                    try {
                        Thread.currentThread().setContextClassLoader(SocketSniffer.class.getClassLoader());
                        cp.addFirst("http-over-mc-sniffer", new HttpSnifferHandler());
                    } finally {
                        Thread.currentThread().setContextClassLoader(oldCl);
                    }
                }
            }
            ctx.fireChannelRead(msg);
        }
    }

    // ===== 子连接处理器：嗅探首包决定 HTTP / TLS / MC =====
    private class HttpSnifferHandler extends ChannelInboundHandlerAdapter {
        private ByteBuf buffer;
        /** 连接类型状态（首包分类结果；keep-alive 复用会重置回 {@link SnifferChannelState#UNKNOWN}）。 */
        private SnifferChannelState state = SnifferChannelState.UNKNOWN;
        /** 当前请求是否已处理（keep-alive 时等待下一请求 / 空闲关闭，见 onKeepAliveDone 重置）。 */
        private boolean httpHandled = false;
        /** keep-alive 空闲关闭任务（有则连接处于 keep-alive 等待下一请求状态）。 */
        private ScheduledFuture<?> idleFuture;

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
            buffer = ctx.alloc().buffer(1024);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            cancelIdle();
            releaseBuffer();
            ctx.fireChannelInactive();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            if (state == SnifferChannelState.MC) {
                ctx.fireExceptionCaught(cause);
            } else {
                ctx.close();
            }
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (state == SnifferChannelState.MC) {
                ctx.fireChannelRead(msg);
                return;
            }
            if (httpHandled) {
                // HTTP 已处理并关闭，或处于 keep-alive 等待（autoRead 已暂停，此分支一般不应触发）；
                // 若仍收到字节则丢弃，避免覆盖下一请求缓冲。
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

            // 收到新数据 → 连接仍有活动，取消空闲关闭（keep-alive 等待中时）。
            cancelIdle();

            if (state == SnifferChannelState.UNKNOWN) {
                SnifferChannelState c = classify(buffer);
                switch (c) {
                    case HTTP_TLS:
                        state = SnifferChannelState.HTTP_TLS;
                        upgradeToTls(ctx);
                        return;
                    case MC:
                        switchToMc(ctx);
                        return;
                    case HTTP_PLAIN:
                        state = SnifferChannelState.HTTP_PLAIN;
                        break;
                    default:
                        if (buffer.readableBytes() > 64 * 1024) {
                            switchToMc(ctx);
                        }
                        return;
                }
            }

            if (state.isHttp()) {
                RequestParsed parsed = tryParseHttp(buffer);
                if (parsed == null) {
                    if (buffer.readableBytes() > maxBodyBytes + 1024 * 1024) {
                        writeRaw(ctx, "Payload Too Large", 413, state.isTls());
                    }
                    return; // 等待更多数据
                }
                // 已提取完整请求，buffer 不再需要
                releaseBuffer();
                httpHandled = true;
                // keep-alive：暂停读取，待响应写完再恢复（避免并发解析同一条连接的下一条请求）
                ctx.channel().config().setAutoRead(false);
                final boolean tls = state.isTls();
                try {
                    executor.submit(() -> handleHttp(ctx, parsed, tls));
                } catch (java.util.concurrent.RejectedExecutionException e) {
                    // 秒杀防护：线程池与队列已满（并发上限 sniffer.http-concurrency）→ 不再接收，
                    // 立即 503 快速失败，直到有空位（避免任务无限堆积打爆内存）
                    log.warnT("log.sniffer.concurrency-limit", "HTTP 并发已达上限({0})，拒绝新请求: {1} {2}",
                            httpConcurrency, parsed.method, parsed.path);
                    writeRaw(ctx, "Service Unavailable (HTTP concurrency limit)", 503, tls);
                    releaseBuffer();
                    httpHandled = false;
                    ctx.channel().config().setAutoRead(true);
                }
            }
        }

        /** 就地 TLS 升级：管道最前插入 SslHandler 并重放缓冲的 ClientHello 首包；监听握手结果并打日志。 */
        private void upgradeToTls(ChannelHandlerContext ctx) {
            SSLEngine engine = tlsEngineSupplier.get();
            final SslHandler ssl = new SslHandler(engine);
            ctx.pipeline().addFirst("http-over-mc-ssl", ssl);
            final Object remote = ctx.channel().remoteAddress();
            ssl.handshakeFuture().addListener(f -> {
                if (f.isSuccess()) {
                    javax.net.ssl.SSLSession s = ssl.engine().getSession();
                    log.infoT("log.tls.handshake-success",
                            "TLS 握手成功 (remote={0}, protocol={1}, cipher={2})",
                            remote, s.getProtocol(), s.getCipherSuite());
                } else {
                    Throwable cause = f.cause();
                    log.warnT("log.tls.handshake-fail",
                            "TLS 握手失败 (remote={0}): {1}",
                            remote, cause == null ? f.toString() : cause.toString());
                }
            });
            ByteBuf replay = buffer;
            buffer = null;
            ctx.pipeline().fireChannelRead(replay);
        }

        private void switchToMc(ChannelHandlerContext ctx) {
            state = SnifferChannelState.MC;
            ByteBuf copy = buffer;
            buffer = null;
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

        private void cancelIdle() {
            if (idleFuture != null) {
                try { idleFuture.cancel(false); } catch (Throwable ignored) {}
                idleFuture = null;
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
    private SnifferChannelState classify(ByteBuf buf) {
        int len = buf.readableBytes();
        if (len == 0) return SnifferChannelState.UNKNOWN;
        int idx = buf.readerIndex();
        byte b0 = buf.getByte(idx);

        if (tlsEngineSupplier != null && b0 == 0x16) {
            if (len < 3) return SnifferChannelState.UNKNOWN;
            byte b1 = buf.getByte(idx + 1);
            byte b2 = buf.getByte(idx + 2);
            if (b1 == 0x03 && (b2 == 0x01 || b2 == 0x02 || b2 == 0x03)) return SnifferChannelState.HTTP_TLS;
        }

        if (b0 < 'A' || b0 > 'Z') return SnifferChannelState.MC;

        int i = 0;
        StringBuilder tok = new StringBuilder();
        boolean tokenComplete = false;
        for (; i < len && i < 16; i++) {
            byte b = buf.getByte(idx + i);
            if (b == ' ') { tokenComplete = true; break; }
            if (b < 'A' || b > 'Z') return SnifferChannelState.MC;
            tok.append((char) b);
        }
        if (!tokenComplete) {
            return SnifferChannelState.UNKNOWN;
        }
        boolean known = false;
        for (String m : METHODS) {
            if (m.equals(tok.toString())) { known = true; break; }
        }
        if (!known) return SnifferChannelState.MC;

        int j = i + 1;
        int k = j;
        int limit = Math.min(len, j + 200);
        boolean foundNewline = false;
        for (; k < limit; k++) {
            if (buf.getByte(idx + k) == '\n') { foundNewline = true; break; }
        }
        String line = buf.toString(idx + j, Math.max(0, k - j), StandardCharsets.US_ASCII);
        if (line.contains(" HTTP/")) return SnifferChannelState.HTTP_PLAIN;
        if (foundNewline) return SnifferChannelState.MC;
        return SnifferChannelState.UNKNOWN;
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
        if (available < contentLength) return null;
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
        final String ip = clientIp(ctx, p.headers);
        fire(new GatewayRequestEvent(p.method, p.path, ip, tls, p.headers));
        try {
            GatewayFilter gw = gateway;
            if (gw != null) {
                Credential cred = gw.resolveCredential(p.headers);
                if (cred != null && !tls && tlsEngineSupplier != null) {
                    log.warnT("log.sniffer.plaintext-cred", "凭证经明文 HTTP 传输（建议启用 TLS）: {0}", ip);
                }
                GatewayContext gctx = new GatewayContext(p.method, handler.policyPath(p.path),
                        p.headers, ip, tls, cred, p.path);
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
                writeRaw(ctx, "HTTP-Over-MC tunnel not ready", 503, tls);
                return;
            }
            if (p.headers != null) {
                p.headers.put(ApiRequestContext.HEADER_REMOTE_IP, ip);
            }
            FrameProto.HttpResponseFrame resp = handler.handle(p.method, p.path, p.headers, p.body);
            code = resp.getStatusCode();
            byte[] body = resp.getBody().toByteArray();
            String contentType = resp.getHeadersMap().get("Content-Type");
            if (contentType == null) contentType = MimeTypes.OCTET_STREAM;

            // ETag + 304（仅 GET 200 且有响应体）
            String etag = null;
            if ("GET".equals(p.method) && code == 200 && body.length > 0) {
                etag = '"' + sha256hex(body) + '"';
                String inm = p.headers.get("If-None-Match");
                if (inm != null && inm.trim().equals(etag)) {
                    writeNotModified(ctx, tls, etag, cacheControlFor(p, contentType));
                    return;
                }
            }

            // gzip 压缩（按 Accept-Encoding + 可压缩类型 + 阈值）
            boolean compressed = false;
            String ae = p.headers.get("Accept-Encoding");
            if (isCompressible(contentType) && body.length >= COMPRESS_THRESHOLD && ae != null && ae.contains("gzip")) {
                byte[] gz = gzip(body);
                if (gz.length < body.length) {
                    body = gz;
                    compressed = true;
                }
            }

            boolean keepAlive = isKeepAlive(p);
            StringBuilder sb = new StringBuilder();
            sb.append("HTTP/1.1 ").append(code).append(' ').append(statusText(code)).append("\r\n");
            for (Map.Entry<String, String> h : resp.getHeadersMap().entrySet()) {
                String k = h.getKey();
                if ("Content-Length".equalsIgnoreCase(k) || "Connection".equalsIgnoreCase(k)
                        || "Content-Encoding".equalsIgnoreCase(k) || "ETag".equalsIgnoreCase(k)
                        || "Cache-Control".equalsIgnoreCase(k)) continue;
                sb.append(k).append(": ").append(h.getValue()).append("\r\n");
            }
            if (compressed) sb.append("Content-Encoding: gzip\r\n");
            if (etag != null) sb.append("ETag: ").append(etag).append("\r\n");
            String cc = cacheControlFor(p, contentType);
            if (cc != null) sb.append("Cache-Control: ").append(cc).append("\r\n");
            sb.append("Content-Length: ").append(body.length).append("\r\n");
            sb.append(keepAlive ? "Connection: keep-alive\r\n" : "Connection: close\r\n");
            sb.append("\r\n");
            byte[] head = sb.toString().getBytes(StandardCharsets.US_ASCII);

            ByteBuf out = Unpooled.buffer(head.length + body.length);
            out.writeBytes(head);
            out.writeBytes(body);
            writeResponse(ctx, out, tls, keepAlive);
        } catch (Exception e) {
            code = 502;
            log.warnT("log.sniffer.tunnel-fail", "隧道转换失败: {0}", e);
            writeRaw(ctx, "HTTP-Over-MC tunnel error: "
                    + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()), 502, tls);
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
     * - 明文连接：经管道 HeadContext 直接写原始字节到 socket，绕过 Spigot 的 MC 出站编码器；
     * - TLS 连接：必须经本 handler 的 ctx 出站（先过最前的 SslHandler 加密再出 socket）。
     * keep-alive 时不关闭连接，并在写出完成后恢复读取（处理同一连接的后续请求）。
     */
    private void writeResponse(ChannelHandlerContext ctx, ByteBuf out, boolean tls, boolean keepAlive) {
        if (tls) {
            if (keepAlive) {
                ctx.writeAndFlush(out).addListener(f -> onKeepAliveDone(ctx));
            } else {
                ctx.writeAndFlush(out).addListener(ChannelFutureListener.CLOSE);
            }
        } else {
            ChannelHandlerContext fc = ctx.pipeline().firstContext();
            if (keepAlive) {
                fc.writeAndFlush(out).addListener(f -> onKeepAliveDone(ctx));
            } else {
                fc.writeAndFlush(out).addListener(ChannelFutureListener.CLOSE);
            }
        }
    }

    /** keep-alive 响应写完：重置状态、安排空闲关闭、恢复读取下一请求。 */
    private void onKeepAliveDone(ChannelHandlerContext ctx) {
        try {
            HttpSnifferHandler h = (HttpSnifferHandler) ctx.handler();
            // 保留协议类型（TLS/明文），不重置为 UNKNOWN：
            // TLS 连接上 SslHandler 已解密后续请求，若重置为 UNKNOWN，classify 会把解密后的明文
            // HTTP 首字节（如 'G'）误判为 HTTP_PLAIN，导致响应绕过 SslHandler 直接写明文到 socket，
            // 浏览器收到明文后断开连接 → keep-alive 复用时 API 间歇性失败。
            SnifferChannelState prev = h.state;
            h.state = prev.isTls() ? SnifferChannelState.HTTP_TLS : SnifferChannelState.HTTP_PLAIN;
            h.httpHandled = false;
            h.releaseBuffer();
            h.idleFuture = idleExecutor.schedule(() -> {
                // 仅在仍无新请求到达（httpHandled 仍 false）时关闭
                if (!h.httpHandled && ctx.channel().isActive()) {
                    ctx.close();
                }
            }, keepAliveIdleSeconds, TimeUnit.SECONDS);
            ctx.channel().config().setAutoRead(true);
            ctx.read();
        } catch (Throwable t) {
            ctx.close();
        }
    }

    /** 直接写出错误响应（413/503/502 等）：body 统一 JSON 信封（真实状态码 + {code,msg,data}）。 */
    private void writeRaw(ChannelHandlerContext ctx, String bodyText, int code, boolean tls) {
        byte[] body = HttpFrames.jsonError(code, bodyText).getBody().toByteArray();
        StringBuilder sb = new StringBuilder();
        sb.append(statusLine(code));
        sb.append("Content-Type: ").append(MimeTypes.forExt("json")).append("\r\n");
        sb.append("Content-Length: ").append(body.length).append("\r\n");
        sb.append("Connection: close\r\n");
        sb.append("\r\n");
        byte[] head = sb.toString().getBytes(StandardCharsets.US_ASCII);
        ByteBuf out = Unpooled.buffer(head.length + body.length);
        out.writeBytes(head);
        out.writeBytes(body);
        writeResponse(ctx, out, tls, false);
    }

    private void writeNotModified(ChannelHandlerContext ctx, boolean tls, String etag, String cc) {
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 304 Not Modified\r\n");
        if (etag != null) sb.append("ETag: ").append(etag).append("\r\n");
        if (cc != null) sb.append("Cache-Control: ").append(cc).append("\r\n");
        sb.append("Content-Length: 0\r\n");
        sb.append("Connection: keep-alive\r\n\r\n");
        byte[] head = sb.toString().getBytes(StandardCharsets.US_ASCII);
        ByteBuf out = Unpooled.copiedBuffer(head);
        writeResponse(ctx, out, tls, true);
    }

    /** 写出网关策略拒绝响应（401/403/426/429/500），附带策略指定的响应头；body 统一 JSON 信封。 */
    private void writeDeny(ChannelHandlerContext ctx, PolicyResult res, boolean tls) {
        String msg = new String(res.getBodyBytes(), StandardCharsets.UTF_8);
        byte[] body = HttpFrames.jsonError(res.getStatusCode(), msg).getBody().toByteArray();
        StringBuilder sb = new StringBuilder();
        sb.append(statusLine(res.getStatusCode()));
        for (Map.Entry<String, String> h : res.getHeaders().entrySet()) {
            sb.append(h.getKey()).append(": ").append(h.getValue()).append("\r\n");
        }
        sb.append("Content-Type: ").append(MimeTypes.forExt("json")).append("\r\n");
        sb.append("Content-Length: ").append(body.length).append("\r\n");
        sb.append("Connection: close\r\n\r\n");
        ByteBuf out = Unpooled.buffer(sb.length() + body.length);
        out.writeBytes(sb.toString().getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(body);
        writeResponse(ctx, out, tls, false);
    }

    /** TCP socket 上的客户端源 IP；trust-proxy 时优先取 X-Forwarded-For 首个 IP（真实访客 IP）。 */
    private String clientIp(ChannelHandlerContext ctx, Map<String, String> headers) {
        if (trustProxy && headers != null) {
            String xff = headers.get("X-Forwarded-For");
            if (xff != null && !xff.isEmpty()) {
                String first = xff.split(",")[0].trim();
                if (!first.isEmpty()) return first;
            }
        }
        return socketIp(ctx);
    }

    private static String socketIp(ChannelHandlerContext ctx) {
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
            case 304: return "Not Modified";
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

    // ===== 压缩 / 缓存辅助 =====

    /** 是否为可压缩的响应内容类型（二进制媒体如 png/jpg/woff 已自带压缩，不重复压缩）。 */
    private static boolean isCompressible(String contentType) {
        if (contentType == null) return false;
        String c = contentType.toLowerCase();
        if (c.startsWith("text/")) return true;
        if (c.startsWith("application/javascript") || c.startsWith("application/json")
                || c.startsWith("application/xml") || c.startsWith("application/atom+xml")
                || c.startsWith("application/ld+json") || c.startsWith("application/x-javascript")
                || c.startsWith("image/svg+xml")) return true;
        return false;
    }

    /** gzip 压缩（失败时返回原字节）。 */
    private static byte[] gzip(byte[] data) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length);
            try (GZIPOutputStream gz = new GZIPOutputStream(bos)) {
                gz.write(data);
            }
            return bos.toByteArray();
        } catch (IOException e) {
            return data;
        }
    }

    /** 实体摘要（SHA-256 十六进制），用作 ETag 基准（基于压缩前原文，避免编码不一致）。 */
    private static String sha256hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(data);
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte x : d) sb.append(String.format("%02x", x & 0xFF));
            return sb.toString();
        } catch (Exception e) {
            // 兜底：简单长度+hash，保证 304 仍可工作
            int h = java.util.Arrays.hashCode(data);
            return String.format("%08x", h);
        }
    }

    /** 是否 keep-alive：HTTP/1.1 默认复用，除非客户端显式 Connection: close；HTTP/1.0 需显式 keep-alive。 */
    private static boolean isKeepAlive(RequestParsed p) {
        String conn = p.headers.get("Connection");
        boolean connClose = conn != null && conn.toLowerCase().contains("close");
        if (connClose) return false;
        boolean connKeep = conn != null && conn.toLowerCase().contains("keep-alive");
        boolean http11 = p.version != null && p.version.contains("HTTP/1.1");
        return http11 || connKeep;
    }

    /** 缓存策略：静态资源可公开缓存；API / 鉴权端点禁止缓存。 */
    private static String cacheControlFor(RequestParsed p, String contentType) {
        String path = p.path == null ? "" : p.path;
        if (path.startsWith("/api/") || path.startsWith("/auth/")) return "no-store";
        if (contentType.startsWith("application/json")) return "no-store";
        return "public, max-age=300";
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
            log.errorT("log.sniffer.reflect-server-connection-fail", "反射获取 ServerConnection 失败", t);
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
