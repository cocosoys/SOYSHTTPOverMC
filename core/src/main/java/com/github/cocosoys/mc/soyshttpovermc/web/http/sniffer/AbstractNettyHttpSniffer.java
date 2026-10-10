package com.github.cocosoys.mc.soyshttpovermc.web.http.sniffer;

import com.github.cocosoys.mc.soyshttpovermc.web.ApiRequestContext;
import com.github.cocosoys.mc.soyshttpovermc.web.MimeTypes;
import com.github.cocosoys.mc.soyshttpovermc.web.RequestStats;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.Credential;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.GatewayContext;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.GatewayFilter;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.PolicyResult;
import com.github.cocosoys.mc.soyshttpovermc.web.http.HttpRequestHandler;
import com.github.cocosoys.mc.soyshttpovermc.util.HttpFrames;
import com.github.cocosoys.mc.soyshttpovermc.web.proto.FrameProto;
import lombok.CustomLog;

import javax.net.ssl.SSLEngine;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Netty 同端口嗅探器抽象基类（纯反射 + JDK 动态代理，版本无关）。
 *
 * <p>把 v1_7 已验证的「父 Channel 注入 ParentInjector → 子连接 pipeline 注入首包嗅探 →
 * 明文 HTTP / TLS / MC 三分流」整套机制固化在 core，版本子类只需提供：</p>
 * <ul>
 *   <li>{@link #nettyPackagePrefix()}：服务端 netty 类名前缀
 *       （1.7 = {@code net.minecraft.util.io.netty.}（relocate）；1.16+ = {@code io.netty.}）；</li>
 *   <li>{@link #locateListenerChannels()}：定位父 ServerChannel 的 ChannelFuture 列表
 *       （各版本经自己的 SocketSnifferAdapter 反射 NMS 取得）；</li>
 *   <li>{@link #logTag()}：日志前缀（如 {@code "v1_20"}）。</li>
 * </ul>
 *
 * <p>协议解析、响应构建、网关策略链全部复用 core（{@link HttpByteProtocol} /
 * {@link HttpRequestHandler} / {@link GatewayFilter}）；仅传输层（pipeline 挂接 / 写回）经反射完成。</p>
 */
@CustomLog
public abstract class AbstractNettyHttpSniffer {

    // 模式常量
    protected static final int MODE_UNKNOWN = 0;
    protected static final int MODE_MC = 1;
    protected static final int MODE_HTTP_PLAIN = 2;
    protected static final int MODE_HTTP_TLS = 3;

    private static final String NAME_INJECTOR = "soys-parent-injector";
    private static final String NAME_SNIFFER = "soys-http-sniffer";
    private static final String NAME_SSL = "soys-ssl";

    // ===== netty Class 缓存（按类加载器隔离；子类各自静态加载） =====
    private volatile Class<?> C_PIPELINE;
    private volatile Class<?> C_CTX;
    private volatile Class<?> C_CHANNEL;
    private volatile Class<?> C_BYTEBUF;
    private volatile Class<?> C_UNPOOLED;
    private volatile Class<?> C_INBOUND;
    private volatile Class<?> C_HANDLER;
    private volatile Class<?> C_SSLHANDLER;
    private volatile Class<?> C_CFLISTENER;
    private volatile Class<?> C_GFLISTENER;
    private volatile ClassLoader nettyLoader;

    // ===== core 依赖 =====
    protected final HttpSnifferDeps deps;
    private final HttpRequestHandler handler;
    private final BooleanSupplier ready;
    private final RequestStats stats;
    private final GatewayFilter gateway;
    private final Supplier<SSLEngine> tlsEngineSupplier;
    private final boolean trustProxy;
    private final int maxBodyBytes;
    private final int keepAliveIdleSeconds;

    private final ThreadPoolExecutor executor;
    private final ScheduledExecutorService idleExecutor;
    private final List<Object> installedParents = new ArrayList<>();

    /**
     * 每连接嗅探状态（由 JDK Proxy 的 InvocationHandler 持有）。
     */
    protected static final class SnifferState {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(1024);
        int mode = MODE_UNKNOWN;
        boolean httpHandled = false;
        ScheduledFuture<?> idleFuture;
    }

    protected AbstractNettyHttpSniffer(HttpSnifferDeps deps) {
        this.deps = deps;
        this.handler = deps.handler();
        this.ready = deps.ready();
        this.stats = deps.stats();
        this.gateway = deps.gateway();
        this.tlsEngineSupplier = deps.tlsEngineSupplier();
        this.trustProxy = deps.trustProxy();
        this.maxBodyBytes = deps.maxBody();
        this.keepAliveIdleSeconds = Math.max(1, deps.keepAliveIdleSeconds());
        String tag = logTag();
        int concurrency = Math.max(1, deps.httpConcurrency());
        int queue = Math.max(1, deps.httpQueue());
        this.executor = new ThreadPoolExecutor(concurrency, concurrency, 60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(queue),
                r -> {
                    Thread t = new Thread(r, "HTTP-Over-MC-Sniffer-" + tag);
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.AbortPolicy());
        this.idleExecutor = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "HTTP-Over-MC-Idle-" + tag);
            t.setDaemon(true);
            return t;
        });
    }

    // ===== 子类钩子 =====

    /** 服务端 netty 类名前缀（末尾带点）：1.7 = {@code "net.minecraft.util.io.netty."}；1.16+ = {@code "io.netty."}。 */
    protected abstract String nettyPackagePrefix();

    /** 日志标签（如 {@code "v1_20"}），用于线程名与日志前缀。 */
    protected abstract String logTag();

    /** 定位父 ServerChannel 的 ChannelFuture 列表（子类经自己的 SocketSnifferAdapter 反射 NMS 取得）。 */
    protected abstract List<?> locateListenerChannels();

    // ===== 安装 / 卸载 =====

    /**
     * 定位父 ServerChannel 并在其 pipeline 前插 ParentInjector。
     *
     * @return 安装句柄（本实例）；失败抛异常由调用方回退内置嗅探器
     */
    public Object install() throws Exception {
        loadNettyClasses();
        Object serverChannel = findServerChannel();
        if (serverChannel == null) {
            throw new IllegalStateException("无法定位服务端 ServerChannel（netty 反射桥安装失败，" + logTag() + "）");
        }
        Object pipeline = pipeline(serverChannel);
        Object injector = createParentInjectorProxy();
        findMethod(pipeline.getClass(), "addFirst", String.class, C_HANDLER)
                .invoke(pipeline, NAME_INJECTOR, injector);
        installedParents.add(serverChannel);
        log.infoT("log.adapter." + logTag().replace(".", "") + ".installed",
                "[adapter/{0}] 已在父 ServerChannel 上安装同端口嗅探器（netty 反射桥）", logTag());
        return this;
    }

    public void uninstall() {
        for (Object parent : installedParents) {
            try {
                Object pipe = pipeline(parent);
                findMethod(pipe.getClass(), "remove", String.class).invoke(pipe, NAME_INJECTOR);
            } catch (Throwable ignored) {
            }
        }
        installedParents.clear();
        executor.shutdownNow();
        idleExecutor.shutdownNow();
    }

    // ===== 定位父 ServerChannel =====

    private Object findServerChannel() throws Exception {
        List<?> futures = locateListenerChannels();
        for (Object f : futures) {
            if (f == null) {
                continue;
            }
            try {
                Object ch = findMethod(f.getClass(), "channel").invoke(f);
                if (ch != null && C_CHANNEL.isInstance(ch)
                        && Boolean.TRUE.equals(findMethod(ch.getClass(), "isActive").invoke(ch))) {
                    log.infoT("log.adapter." + logTag().replace(".", "") + ".parent-channel-found",
                            "[adapter/{0}] 定位父 ServerChannel 成功: {1}", logTag(), ch.getClass().getName());
                    return ch;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    /**
     * 反射查找方法并强制可访问（netty 实现类多为 package-private，需 setAccessible 才能 invoke）。
     */
    protected static java.lang.reflect.Method findMethod(Class<?> owner, String name, Class<?>... types)
            throws NoSuchMethodException {
        java.lang.reflect.Method m;
        try {
            m = owner.getMethod(name, types);
        } catch (NoSuchMethodException e) {
            m = owner.getDeclaredMethod(name, types);
        }
        m.setAccessible(true);
        return m;
    }

    // ===== netty 类加载（前缀由子类提供） =====

    private synchronized void loadNettyClasses() throws Exception {
        if (C_INBOUND != null) {
            return;
        }
        String n = nettyPackagePrefix();
        C_PIPELINE = Class.forName(n + "channel.ChannelPipeline");
        C_CTX = Class.forName(n + "channel.ChannelHandlerContext");
        C_CHANNEL = Class.forName(n + "channel.Channel");
        C_BYTEBUF = Class.forName(n + "buffer.ByteBuf");
        C_UNPOOLED = Class.forName(n + "buffer.Unpooled");
        C_INBOUND = Class.forName(n + "channel.ChannelInboundHandler");
        C_HANDLER = Class.forName(n + "channel.ChannelHandler");
        C_SSLHANDLER = Class.forName(n + "handler.ssl.SslHandler");
        C_CFLISTENER = Class.forName(n + "channel.ChannelFutureListener");
        C_GFLISTENER = Class.forName(n + "util.concurrent.GenericFutureListener");
        nettyLoader = C_INBOUND.getClassLoader();
    }

    // ===== JDK Proxy：父 Channel ParentInjector =====

    private Object createParentInjectorProxy() {
        return Proxy.newProxyInstance(nettyLoader, new Class<?>[]{C_INBOUND}, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                String name = method.getName();
                if ("channelRead".equals(name)) {
                    Object ctx = args[0];
                    Object msg = args[1];
                    try {
                        if (C_CHANNEL.isInstance(msg)) {
                            Object childPipe = findMethod(msg.getClass(), "pipeline").invoke(msg);
                            Object childSniffer = createChildSnifferProxy();
                            findMethod(childPipe.getClass(), "addFirst", String.class, C_HANDLER)
                                    .invoke(childPipe, NAME_SNIFFER, childSniffer);
                        }
                        fireChannelRead(ctx, msg);
                    } catch (Throwable t) {
                        log.warnT("log.adapter." + logTag().replace(".", "") + ".parent-inject-failed",
                                "[adapter/{0}] ParentInjector.channelRead 异常", logTag(), t);
                    }
                }
                return null;
            }
        });
    }

    // ===== JDK Proxy：子连接 HttpSniffer =====

    private Object createChildSnifferProxy() {
        SnifferState st = new SnifferState();
        return Proxy.newProxyInstance(nettyLoader, new Class<?>[]{C_INBOUND}, new HttpSnifferInvoker(st));
    }

    private final class HttpSnifferInvoker implements InvocationHandler {
        private final SnifferState st;

        HttpSnifferInvoker(SnifferState st) {
            this.st = st;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            try {
                if ("channelRead".equals(name)) {
                    onChannelRead((Object) args[0], (Object) args[1]);
                } else if ("channelInactive".equals(name)) {
                    cancelIdle();
                    st.buf = null;
                } else if ("exceptionCaught".equals(name)) {
                    if (st.mode == MODE_MC) {
                        Object ctx = args[0];
                        findMethod(ctx.getClass(), "fireExceptionCaught", Throwable.class)
                                .invoke(ctx, (Throwable) args[1]);
                    } else {
                        closeChannel((Object) args[0]);
                    }
                }
            } catch (Throwable t) {
                log.warnT("log.adapter." + logTag().replace(".", "") + ".callback-failed",
                        "[adapter/{0}] HttpSniffer 回调异常({1})", logTag(), name, t);
                try {
                    closeChannel((Object) args[0]);
                } catch (Throwable ignored) {
                }
            }
            return null;
        }

        // ===== 核心：嗅探首包三分流 =====

        private void onChannelRead(Object ctx, Object msg) throws Exception {
            if (st.mode == MODE_MC) {
                fireChannelRead(ctx, msg);
                return;
            }
            if (st.httpHandled) {
                release(msg);
                return;
            }
            if (!C_BYTEBUF.isInstance(msg)) {
                fireChannelRead(ctx, msg);
                return;
            }
            byte[] in = toBytes(msg);
            release(msg);
            if (st.buf == null) {
                st.buf = new ByteArrayOutputStream(1024);
            }
            st.buf.write(in, 0, in.length);
            cancelIdle();

            if (st.mode == MODE_UNKNOWN) {
                int c = classify(st.buf);
                switch (c) {
                    case MODE_HTTP_TLS:
                        st.mode = MODE_HTTP_TLS;
                        upgradeToTls(ctx);
                        return;
                    case MODE_MC:
                        switchToMc(ctx);
                        return;
                    case MODE_HTTP_PLAIN:
                        st.mode = MODE_HTTP_PLAIN;
                        break;
                    default:
                        if (st.buf.size() > 64 * 1024) {
                            switchToMc(ctx);
                        }
                        return;
                }
            }

            if (st.mode == MODE_HTTP_PLAIN || st.mode == MODE_HTTP_TLS) {
                byte[] all = st.buf.toByteArray();
                HttpByteProtocol.ParsedRequest parsed = HttpByteProtocol.tryParseHttp(all, 0, all.length);
                if (parsed == null) {
                    if (st.buf.size() > maxBodyBytes + 1024 * 1024) {
                        writeRaw(ctx, st, "Payload Too Large", 413, st.mode == MODE_HTTP_TLS);
                    }
                    return;
                }
                st.buf = null;
                st.httpHandled = true;
                setAutoRead(ctx, false);
                final boolean tls = st.mode == MODE_HTTP_TLS;
                try {
                    executor.submit(() -> handleHttp(ctx, st, parsed, tls));
                } catch (RejectedExecutionException e) {
                    log.warnT("log.adapter." + logTag().replace(".", "") + ".concurrent-limit",
                            "[adapter/{0}] HTTP 并发已达上限，拒绝新请求: {1} {2}", logTag(), parsed.method, parsed.path);
                    writeRaw(ctx, st, "Service Unavailable (HTTP concurrency limit)", 503, tls);
                    st.httpHandled = false;
                    setAutoRead(ctx, true);
                }
            }
        }

        private void upgradeToTls(Object ctx) throws Exception {
            SSLEngine engine = tlsEngineSupplier.get();
            Object ssl = C_SSLHANDLER.getConstructor(SSLEngine.class).newInstance(engine);
            Object pipe = pipeline(ctx);
            findMethod(pipe.getClass(), "addFirst", String.class, C_HANDLER).invoke(pipe, NAME_SSL, ssl);
            byte[] replay = st.buf.toByteArray();
            st.buf = null;
            Object replayBuf = findMethod(C_UNPOOLED, "wrappedBuffer", byte[].class).invoke(null, replay);
            findMethod(pipe.getClass(), "fireChannelRead", Object.class).invoke(pipe, replayBuf);
        }

        private void switchToMc(Object ctx) throws Exception {
            st.mode = MODE_MC;
            byte[] copy = st.buf.toByteArray();
            st.buf = null;
            Object copyBuf = findMethod(C_UNPOOLED, "wrappedBuffer", byte[].class).invoke(null, copy);
            fireChannelRead(ctx, copyBuf);
            Object pipe = pipeline(ctx);
            findMethod(pipe.getClass(), "remove", String.class).invoke(pipe, NAME_SNIFFER);
        }

        private int classify(ByteArrayOutputStream baos) {
            byte[] raw = baos.toByteArray();
            switch (HttpByteProtocol.classify(raw, raw.length, tlsEngineSupplier != null)) {
                case HTTP_TLS:
                    return MODE_HTTP_TLS;
                case MC:
                    return MODE_MC;
                case HTTP_PLAIN:
                    return MODE_HTTP_PLAIN;
                default:
                    return MODE_UNKNOWN;
            }
        }

        private void cancelIdle() {
            if (st.idleFuture != null) {
                st.idleFuture.cancel(false);
                st.idleFuture = null;
            }
        }
    }

    // ===== HTTP 处理（worker 线程） =====

    private void handleHttp(Object ctx, SnifferState st, HttpByteProtocol.ParsedRequest p, boolean tls) {
        long t0 = System.nanoTime();
        int code = 200;
        final String ip = clientIp(ctx, p.headers);
        try {
            GatewayFilter gw = gateway;
            if (gw != null) {
                Credential cred = gw.resolveCredential(p.headers);
                if (cred != null && !tls && tlsEngineSupplier != null) {
                    log.warnT("log.adapter." + logTag().replace(".", "") + ".plaintext-cred",
                            "[adapter/{0}] 凭证经明文 HTTP 传输（建议启用 TLS）: {1}", logTag(), ip);
                }
                GatewayContext gctx = new GatewayContext(p.method, handler.policyPath(p.path),
                        p.headers, ip, tls, cred, p.path);
                GatewayFilter.Outcome oc = gw.filterDetailed(gctx);
                PolicyResult res = oc.result;
                if (!res.isAllow()) {
                    code = res.getStatusCode();
                    writeDeny(ctx, st, res, tls);
                    return;
                }
            }
            if (!ready.getAsBoolean()) {
                code = 503;
                writeRaw(ctx, st, "HTTP-Over-MC tunnel not ready", 503, tls);
                return;
            }
            if (p.headers != null) {
                p.headers.put(ApiRequestContext.HEADER_REMOTE_IP, ip);
            }
            FrameProto.HttpResponseFrame resp = handler.handle(p.method, p.path, p.headers, p.body);
            code = resp.getStatusCode();
            byte[] body = resp.getBody().toByteArray();
            String contentType = resp.getHeadersMap().get("Content-Type");
            if (contentType == null) {
                contentType = MimeTypes.OCTET_STREAM;
            }

            String etag = null;
            if ("GET".equals(p.method) && code == 200 && body.length > 0) {
                etag = '"' + HttpByteProtocol.sha256hex(body) + '"';
                String inm = p.headers.get("If-None-Match");
                if (inm != null && inm.trim().equals(etag)) {
                    writeNotModified(ctx, st, etag, HttpByteProtocol.cacheControlFor(p.path, contentType), tls);
                    return;
                }
            }

            boolean compressed = false;
            String ae = p.headers.get("Accept-Encoding");
            if (HttpByteProtocol.isCompressible(contentType) && body.length >= 512
                    && ae != null && ae.contains("gzip")) {
                byte[] gz = HttpByteProtocol.gzip(body);
                if (gz.length < body.length) {
                    body = gz;
                    compressed = true;
                }
            }

            boolean keepAlive = HttpByteProtocol.isKeepAlive(p.version, p.headers);
            StringBuilder sb = new StringBuilder();
            sb.append("HTTP/1.1 ").append(code).append(' ').append(HttpByteProtocol.statusText(code)).append("\r\n");
            for (Map.Entry<String, String> h : resp.getHeadersMap().entrySet()) {
                String k = h.getKey();
                if ("Content-Length".equalsIgnoreCase(k) || "Connection".equalsIgnoreCase(k)
                        || "Content-Encoding".equalsIgnoreCase(k) || "ETag".equalsIgnoreCase(k)
                        || "Cache-Control".equalsIgnoreCase(k)) {
                    continue;
                }
                sb.append(k).append(": ").append(h.getValue()).append("\r\n");
            }
            if (compressed) {
                sb.append("Content-Encoding: gzip\r\n");
            }
            if (etag != null) {
                sb.append("ETag: ").append(etag).append("\r\n");
            }
            String cc = HttpByteProtocol.cacheControlFor(p.path, contentType);
            if (cc != null) {
                sb.append("Cache-Control: ").append(cc).append("\r\n");
            }
            sb.append("Content-Length: ").append(body.length).append("\r\n");
            sb.append(keepAlive ? "Connection: keep-alive\r\n" : "Connection: close\r\n");
            sb.append("\r\n");
            byte[] head = sb.toString().getBytes(StandardCharsets.US_ASCII);
            byte[] out = new byte[head.length + body.length];
            System.arraycopy(head, 0, out, 0, head.length);
            System.arraycopy(body, 0, out, head.length, body.length);
            writeResponse(ctx, st, out, tls, keepAlive);
        } catch (Exception e) {
            code = 502;
            log.warnT("log.adapter." + logTag().replace(".", "") + ".tunnel-failed",
                    "[adapter/{0}] 隧道转换失败", logTag(), e);
            writeRaw(ctx, st, "HTTP-Over-MC tunnel error: "
                    + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()), 502, tls);
        } finally {
            long dtUs = (System.nanoTime() - t0) / 1000;
            stats.recordRequest(p.method, p.path, code, dtUs);
        }
    }

    // ===== 响应写出（反射 netty） =====

    private void writeResponse(Object ctx, SnifferState st, byte[] bytes, boolean tls, boolean keepAlive) {
        try {
            Object out = findMethod(C_UNPOOLED, "wrappedBuffer", byte[].class).invoke(null, bytes);
            Object pipe = pipeline(ctx);
            Object target = tls ? ctx : findMethod(pipe.getClass(), "firstContext").invoke(pipe);
            Object future = findMethod(target.getClass(), "writeAndFlush", Object.class).invoke(target, out);
            if (keepAlive) {
                Object listener = Proxy.newProxyInstance(nettyLoader, new Class<?>[]{C_GFLISTENER},
                        (p, m, a) -> {
                            if ("operationComplete".equals(m.getName())) {
                                onWriteDone(ctx, st);
                            }
                            return null;
                        });
                findMethod(future.getClass(), "addListener", C_GFLISTENER).invoke(future, listener);
            } else {
                Object closeL = C_CFLISTENER.getField("CLOSE").get(null);
                findMethod(future.getClass(), "addListener", C_GFLISTENER).invoke(future, closeL);
            }
        } catch (Throwable t) {
            log.warnT("log.adapter." + logTag().replace(".", "") + ".write-failed",
                    "[adapter/{0}] 写出 HTTP 响应失败", logTag(), t);
            closeChannel(ctx);
        }
    }

    private void onWriteDone(Object ctx, SnifferState st) {
        try {
            Object ch = channel(ctx);
            Object cfg = findMethod(ch.getClass(), "config").invoke(ch);
            findMethod(cfg.getClass(), "setAutoRead", boolean.class).invoke(cfg, true);
            findMethod(ctx.getClass(), "read").invoke(ctx);
            st.httpHandled = false;
            st.buf = new ByteArrayOutputStream(1024);
            scheduleIdle(ctx, st);
        } catch (Throwable t) {
            closeChannel(ctx);
        }
    }

    private void scheduleIdle(Object ctx, SnifferState st) {
        try {
            st.idleFuture = idleExecutor.schedule(() -> {
                if (!st.httpHandled) {
                    try {
                        closeChannel(ctx);
                    } catch (Throwable ignored) {
                    }
                }
            }, keepAliveIdleSeconds, TimeUnit.SECONDS);
        } catch (Throwable ignored) {
        }
    }

    private void writeRaw(Object ctx, SnifferState st, String bodyText, int code, boolean tls) {
        try {
            byte[] body = HttpFrames.jsonError(code, bodyText).getBody().toByteArray();
            StringBuilder sb = new StringBuilder();
            sb.append("HTTP/1.1 ").append(code).append(' ').append(HttpByteProtocol.statusText(code)).append("\r\n");
            sb.append("Content-Type: ").append(MimeTypes.forExt("json")).append("\r\n");
            sb.append("Content-Length: ").append(body.length).append("\r\n");
            sb.append("Connection: close\r\n\r\n");
            byte[] head = sb.toString().getBytes(StandardCharsets.US_ASCII);
            byte[] out = new byte[head.length + body.length];
            System.arraycopy(head, 0, out, 0, head.length);
            System.arraycopy(body, 0, out, head.length, body.length);
            writeResponse(ctx, st, out, tls, false);
        } catch (Throwable t) {
            closeChannel(ctx);
        }
    }

    private void writeNotModified(Object ctx, SnifferState st, String etag, String cc, boolean tls) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("HTTP/1.1 304 Not Modified\r\n");
            if (etag != null) {
                sb.append("ETag: ").append(etag).append("\r\n");
            }
            if (cc != null) {
                sb.append("Cache-Control: ").append(cc).append("\r\n");
            }
            sb.append("Content-Length: 0\r\n");
            sb.append("Connection: keep-alive\r\n\r\n");
            byte[] out = sb.toString().getBytes(StandardCharsets.US_ASCII);
            writeResponse(ctx, st, out, tls, true);
        } catch (Throwable t) {
            closeChannel(ctx);
        }
    }

    private void writeDeny(Object ctx, SnifferState st, PolicyResult res, boolean tls) {
        try {
            String msg = new String(res.getBodyBytes(), StandardCharsets.UTF_8);
            byte[] body = HttpFrames.jsonError(res.getStatusCode(), msg).getBody().toByteArray();
            StringBuilder sb = new StringBuilder();
            sb.append("HTTP/1.1 ").append(res.getStatusCode()).append(' ')
                    .append(HttpByteProtocol.statusText(res.getStatusCode())).append("\r\n");
            for (Map.Entry<String, String> h : res.getHeaders().entrySet()) {
                sb.append(h.getKey()).append(": ").append(h.getValue()).append("\r\n");
            }
            sb.append("Content-Type: ").append(MimeTypes.forExt("json")).append("\r\n");
            sb.append("Content-Length: ").append(body.length).append("\r\n");
            sb.append("Connection: close\r\n\r\n");
            byte[] head = sb.toString().getBytes(StandardCharsets.US_ASCII);
            byte[] out = new byte[head.length + body.length];
            System.arraycopy(head, 0, out, 0, head.length);
            System.arraycopy(body, 0, out, head.length, body.length);
            writeResponse(ctx, st, out, tls, false);
        } catch (Throwable t) {
            closeChannel(ctx);
        }
    }

    private String clientIp(Object ctx, Map<String, String> headers) {
        if (trustProxy && headers != null) {
            String xff = headers.get("X-Forwarded-For");
            if (xff != null && !xff.isEmpty()) {
                String first = xff.split(",")[0].trim();
                if (!first.isEmpty()) {
                    return first;
                }
            }
        }
        try {
            Object sa = findMethod(channel(ctx).getClass(), "remoteAddress").invoke(channel(ctx));
            if (sa instanceof InetSocketAddress) {
                return ((InetSocketAddress) sa).getAddress().getHostAddress();
            }
        } catch (Throwable ignored) {
        }
        return "0.0.0.0";
    }

    // ===== 反射工具 =====

    private static Object pipeline(Object target) throws Exception {
        return findMethod(target.getClass(), "pipeline").invoke(target);
    }

    private static Object channel(Object ctx) throws Exception {
        return findMethod(ctx.getClass(), "channel").invoke(ctx);
    }

    private static void closeChannel(Object ctx) {
        try {
            Object ch = channel(ctx);
            findMethod(ch.getClass(), "close").invoke(ch);
        } catch (Throwable ignored) {
        }
    }

    private static void fireChannelRead(Object ctx, Object msg) throws Exception {
        findMethod(ctx.getClass(), "fireChannelRead", Object.class).invoke(ctx, msg);
    }

    private static void setAutoRead(Object ctx, boolean auto) throws Exception {
        Object ch = channel(ctx);
        Object cfg = findMethod(ch.getClass(), "config").invoke(ch);
        findMethod(cfg.getClass(), "setAutoRead", boolean.class).invoke(cfg, auto);
    }

    private static void release(Object msg) {
        try {
            findMethod(msg.getClass(), "release").invoke(msg);
        } catch (Throwable ignored) {
        }
    }

    private static byte[] toBytes(Object buf) throws Exception {
        int len = ((Number) findMethod(buf.getClass(), "readableBytes").invoke(buf)).intValue();
        int ri = ((Number) findMethod(buf.getClass(), "readerIndex").invoke(buf)).intValue();
        byte[] raw = new byte[len];
        findMethod(buf.getClass(), "getBytes", int.class, byte[].class).invoke(buf, ri, raw);
        return raw;
    }
}
