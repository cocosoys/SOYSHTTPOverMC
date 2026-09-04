package com.github.cocosoys.mc.soyshttpovermc.adapter.v1_6;

import lombok.CustomLog;
import com.github.cocosoys.mc.soyshttpovermc.web.ApiRequestContext;
import com.github.cocosoys.mc.soyshttpovermc.web.MimeTypes;
import com.github.cocosoys.mc.soyshttpovermc.web.RequestStats;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.Credential;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.GatewayContext;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.GatewayFilter;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.PolicyResult;
import com.github.cocosoys.mc.soyshttpovermc.web.http.HttpRequestHandler;
import com.github.cocosoys.mc.soyshttpovermc.web.http.sniffer.HttpByteProtocol;
import com.github.cocosoys.mc.soyshttpovermc.web.http.sniffer.HttpSnifferDeps;
import com.github.cocosoys.mc.soyshttpovermc.util.HttpFrames;
import com.github.cocosoys.mc.soyshttpovermc.web.proto.FrameProto;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.SequenceInputStream;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;

/**
 * 1.6.x 连接级接入嗅探器（无 Netty，反射替换监听 ServerSocket）。
 *
 * <p>1.6.4 网络层为自研阻塞 NIO（{@code DedicatedServerConnectionThread} 持有
 * {@code ServerSocket d} 并在 {@code run()} 循环中逐轮 {@code getfield d} 再 {@code accept()}；
 * 已 javap 实证其 accept 的 IOException 处理器会回到循环继续）。本实现：</p>
 * <ul>
 *   <li>运行期经 {@code Thread.getAllStackTraces()} 定位 {@code DedicatedServerConnectionThread}；
 *       反射替换其 final 字段 {@code d} 为 {@link SniffingServerSocket}（内部起转发线程
 *       包装 {@code accept()} 返回 {@link SmartSocket}）；</li>
 *   <li>{@link SmartSocket#getInputStream()} 首次调用时嗅探首包：明文 HTTP → 把
 *       真实 Socket 交给插件线程池独立处理，向 MC 侧返回 EOF 流（PendingConnection 快速失败）；
 *       MC（含不支持的 TLS，按决策点④暂缓）→ 返回「首包 + 原流」拼接流，MC 流程不受影响；</li>
 *   <li>HTTP 处理为纯阻塞 Socket 流（复用 {@link HttpByteProtocol} 解析 + core 网关策略链 + 后端），
 *       不依赖 netty。</li>
 * </ul>
 */
@CustomLog
public class V1_6HttpSniffer {

    private static final String THREAD_CLASS_HINT = "DedicatedServerConnectionThread";
    private static final String FIELD_D = "d";
    private static final int SNIFF_TIMEOUT_MS = 5000;
    private static final int SNIFF_MAX_BYTES = 4096;

    // ===== core 依赖 =====
    private final HttpSnifferDeps deps;
    private final HttpRequestHandler handler;
    private final BooleanSupplier ready;
    private final RequestStats stats;
    private final GatewayFilter gateway;
    private final Supplier<javax.net.ssl.SSLEngine> tlsEngineSupplier;
    private final Supplier<javax.net.ssl.SSLContext> sslContextSupplier;
    private final boolean trustProxy;
    private final int maxBodyBytes;
    private final int keepAliveIdleSeconds;

    private final ThreadPoolExecutor executor;

    private static final class Replaced {
        Thread thread;
        Field dField;
        ServerSocket original;
        SniffingServerSocket sniffing;
    }

    private final List<Replaced> replaced = new ArrayList<>();

    public V1_6HttpSniffer(HttpSnifferDeps deps) {
        this.deps = deps;
        this.handler = deps.handler();
        this.ready = deps.ready();
        this.stats = deps.stats();
        this.gateway = deps.gateway();
        this.tlsEngineSupplier = deps.tlsEngineSupplier();
        this.sslContextSupplier = deps.sslContextSupplier();
        this.trustProxy = deps.trustProxy();
        this.maxBodyBytes = deps.maxBody();
        this.keepAliveIdleSeconds = Math.max(1, deps.keepAliveIdleSeconds());
        int concurrency = Math.max(1, deps.httpConcurrency());
        int queue = Math.max(1, deps.httpQueue());
        this.executor = new ThreadPoolExecutor(concurrency, concurrency, 60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(queue),
                r -> {
                    Thread t = new Thread(r, "HTTP-Over-MC-Sniffer-1_6");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    // ===== 安装 / 卸载 =====

    public Object install() throws Exception {
        List<Thread> threads = findConnectionThreads();
        if (threads.isEmpty()) {
            throw new IllegalStateException("未找到 DedicatedServerConnectionThread（1.6 连接级接入安装失败）");
        }
        int n = 0;
        for (Thread t : threads) {
            try {
                Field d = t.getClass().getDeclaredField(FIELD_D);
                d.setAccessible(true);
                ServerSocket orig = (ServerSocket) d.get(t);
                if (orig instanceof SniffingServerSocket) {
                    continue; // 已替换过
                }
                SniffingServerSocket sniffing = new SniffingServerSocket(orig);
                d.set(t, sniffing); // final 实例字段反射 set（服务端 Java 8 运行）
                // 竞态修复：Listen thread 可能正阻塞在旧 ServerSocket.accept()（字段替换不影响已发出的 accept）。
                // 实证：setSoTimeout 无法唤醒已阻塞的原生 accept0（Java8/Windows），旧 accept 只在拿到连接后 goto 0
                // 才重新 getfield 新字段。因此：此时 forward 线程尚未启动，主动向旧端口发一个占位连接，让旧 accept
                // 返回（run() 处理占位连接→goto 0→getfield 新字段 SniffingServerSocket），随后再启动转发线程。
                Socket wake = null;
                try {
                    wake = new Socket();
                    wake.connect(new java.net.InetSocketAddress("127.0.0.1", orig.getLocalPort()), 1000);
                    Thread.sleep(100); // 等旧 accept 返回并 goto 0 完成切换
                } catch (Throwable wakeEx) {
                    log.warn("[adapter/v1_6] 占位连接唤醒旧 accept 失败", wakeEx);
                } finally {
                    try {
                        if (wake != null) wake.close();
                    } catch (Throwable ignored) {
                    }
                }
                sniffing.startForward(); // 旧 accept 已切换到新字段后再启动转发线程
                Replaced r = new Replaced();
                r.thread = t;
                r.dField = d;
                r.original = orig;
                r.sniffing = sniffing;
                replaced.add(r);
                n++;
                log.info("[adapter/v1_6] 已替换监听 ServerSocket → 连接级接入嗅探器生效（" + t.getName() + "）");
            } catch (Throwable ex) {
                log.warn("[adapter/v1_6] 替换连接线程失败: " + t.getName(), ex);
            }
        }
        if (n == 0) {
            throw new IllegalStateException("所有连接线程替换均失败");
        }
        return this;
    }

    public void uninstall() {
        for (Replaced r : replaced) {
            try {
                r.dField.set(r.thread, r.original); // 恢复原 ServerSocket
                r.sniffing.stop();
            } catch (Throwable ignored) {
            }
        }
        replaced.clear();
        executor.shutdownNow();
    }

    private List<Thread> findConnectionThreads() {
        List<Thread> out = new ArrayList<>();
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (t != null && t.getClass().getName().contains(THREAD_CLASS_HINT)) {
                out.add(t);
            }
        }
        return out;
    }

    // ===== HTTP 处理（纯阻塞 Socket 流） =====

    private void handleHttpConnection(InputStream in, OutputStream out, Socket socket, InetAddress addr, boolean tls) {
        final String ip = addr == null ? "0.0.0.0" : addr.getHostAddress();
        try {
            executor.submit(() -> serveHttp(in, out, socket, ip, tls));
        } catch (RejectedExecutionException e) {
            log.warn("[adapter/v1_6] HTTP 并发已达上限，拒绝新连接: " + ip);
            try {
                socket.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private void serveHttp(InputStream in, OutputStream out, Socket socket, String ip, boolean tls) {
        try {
            BufferedInputStream bin = new BufferedInputStream(in);
            ByteArrayOutputStream buf = new ByteArrayOutputStream(1024);
            while (true) {
                int b = bin.read();
                if (b < 0) {
                    break;
                }
                buf.write(b);
                byte[] all = buf.toByteArray();
                HttpByteProtocol.ParsedRequest p = HttpByteProtocol.tryParseHttp(all, 0, all.length);
                if (p == null) {
                    if (buf.size() > maxBodyBytes + 1024 * 1024) {
                        out.write(buildRaw("Payload Too Large", 413));
                        out.flush();
                        break;
                    }
                    continue;
                }
                boolean keepAlive = HttpByteProtocol.isKeepAlive(p.version, p.headers);
                byte[] resp = handleHttp(p, ip, tls);
                out.write(resp);
                out.flush();
                if (!keepAlive) {
                    break;
                }
                buf.reset();
            }
        } catch (Exception e) {
            log.warn("[adapter/v1_6] HTTP 连接处理异常: " + ip + " - " + e, e);
        } finally {
            try {
                socket.close();
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * 处理单个 HTTP 请求（core 网关策略链 + 后端），返回完整响应字节。
     */
    private byte[] handleHttp(HttpByteProtocol.ParsedRequest p, String ip, boolean tls) {
        long t0 = System.nanoTime();
        int code = 200;
        try {
            GatewayFilter gw = gateway;
            if (gw != null) {
                Credential cred = gw.resolveCredential(p.headers);
                if (cred != null && !tls && tlsEngineSupplier != null) {
                    log.warn("[adapter/v1_6] 凭证经明文 HTTP 传输（建议启用 TLS）: " + ip);
                }
                GatewayContext gctx = new GatewayContext(p.method, handler.policyPath(p.path),
                        p.headers, ip, tls, cred, p.path);
                GatewayFilter.Outcome oc = gw.filterDetailed(gctx);
                PolicyResult res = oc.result;
                if (!res.isAllow()) {
                    code = res.getStatusCode();
                    return buildDeny(res);
                }
            }
            if (!ready.getAsBoolean()) {
                code = 503;
                return buildRaw("HTTP-Over-MC tunnel not ready", 503);
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
                    return buildNotModified(etag, HttpByteProtocol.cacheControlFor(p.path, contentType));
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
            return out;
        } catch (Exception e) {
            code = 502;
            log.warn("[adapter/v1_6] 隧道转换失败", e);
            return buildRaw("HTTP-Over-MC tunnel error: "
                    + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()), 502);
        } finally {
            long dtUs = (System.nanoTime() - t0) / 1000;
            stats.recordRequest(p.method, p.path, code, dtUs);
        }
    }

    private byte[] buildRaw(String bodyText, int code) {
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
            return out;
        } catch (Exception e) {
            return ("HTTP/1.1 " + code + " Error\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII);
        }
    }

    private byte[] buildNotModified(String etag, String cc) {
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
        return sb.toString().getBytes(StandardCharsets.US_ASCII);
    }

    private byte[] buildDeny(PolicyResult res) {
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
            return out;
        } catch (Exception e) {
            return ("HTTP/1.1 " + res.getStatusCode() + " Error\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII);
        }
    }

    /**
     * 回放首包字节的 Socket 包装：SSLSocketFactory.createSocket(Socket, host, port, autoClose) 需要已连接的
     * Socket；嗅探器已消费了 ClientHello 前若干字节，这里把其副本与原输入流拼接，供 SSLSocket 继续握手。
     */
    private static final class ReplaySocket extends Socket {
        private final Socket real;
        private final InputStream in;
        private final OutputStream out;

        ReplaySocket(Socket real, byte[] replay) throws IOException {
            this.real = real;
            this.in = new SequenceInputStream(new ByteArrayInputStream(replay), real.getInputStream());
            this.out = real.getOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return in;
        }

        @Override
        public OutputStream getOutputStream() {
            return out;
        }

        @Override
        public InetAddress getInetAddress() {
            return real.getInetAddress();
        }

        @Override
        public int getPort() {
            return real.getPort();
        }

        @Override
        public InetAddress getLocalAddress() {
            return real.getLocalAddress();
        }

        @Override
        public int getLocalPort() {
            return real.getLocalPort();
        }

        @Override
        public SocketAddress getRemoteSocketAddress() {
            return real.getRemoteSocketAddress();
        }

        @Override
        public SocketAddress getLocalSocketAddress() {
            return real.getLocalSocketAddress();
        }

        @Override
        public boolean isConnected() {
            return real.isConnected();
        }

        @Override
        public boolean isBound() {
            return real.isBound();
        }

        @Override
        public boolean isClosed() {
            return real.isClosed();
        }

        @Override
        public void close() throws IOException {
            real.close();
        }
    }

    // ===== 内部类：转发型 ServerSocket =====

    /**
     * 包装原监听 ServerSocket：独立线程 accept，转发为 SmartSocket；不重新绑定端口。
     */
    final class SniffingServerSocket extends ServerSocket {
        private final ServerSocket real;
        private final BlockingQueue<SmartSocket> queue = new LinkedBlockingQueue<>();
        private volatile boolean stopped;
        private volatile Thread acceptThread;

        SniffingServerSocket(ServerSocket real) throws IOException {
            super();
            this.real = real;
            // 不自动启动转发线程：install() 先用占位连接唤醒旧 accept（此时旧 accept 是唯一 accept 者，
            // 保证占位连接被它拿到并 goto 0 切换新字段），随后显式 startForward()。
        }

        synchronized void startForward() {
            if (acceptThread != null) {
                return;
            }
            acceptThread = new Thread(this::acceptLoop, "SOYS-1_6-forward");
            acceptThread.setDaemon(true);
            acceptThread.start();
        }

        private void acceptLoop() {
            while (!stopped) {
                try {
                    Socket s = real.accept();
                    queue.put(new SmartSocket(s));
                } catch (Exception e) {
                    if (stopped) {
                        break;
                    }
                    log.debug("[adapter/v1_6] 转发 accept 异常", e);
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ie) {
                        break;
                    }
                }
            }
        }

        @Override
        public Socket accept() throws IOException {
            while (!stopped) {
                try {
                    SmartSocket s = queue.poll(200, TimeUnit.MILLISECONDS);
                    if (s != null) {
                        return s;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new SocketException("interrupted");
                }
            }
            throw new SocketException("sniffing server socket stopped");
        }

        @Override
        public void close() throws IOException {
            stop();
            try {
                real.close();
            } catch (Throwable ignored) {
            }
        }

        void stop() {
            stopped = true;
            Thread at = acceptThread;
            if (at != null) {
                try {
                    at.interrupt();
                } catch (Throwable ignored) {
                }
            }
        }

        // 委托给 real（run() 循环可能查询端口信息）
        @Override
        public InetAddress getInetAddress() {
            return real.getInetAddress();
        }

        @Override
        public int getLocalPort() {
            return real.getLocalPort();
        }

        @Override
        public SocketAddress getLocalSocketAddress() {
            return real.getLocalSocketAddress();
        }

        @Override
        public boolean isBound() {
            return real.isBound();
        }
    }

    // ===== 内部类：智能 Socket（首包嗅探分流） =====

    /**
     * 包装真实 Socket：首次 getInputStream() 嗅探首包；HTTP 分流、MC 放行。
     */
    final class SmartSocket extends Socket {
        private final Socket real;
        private volatile InputStream sniffedStream;
        private volatile boolean httpTaken;

        SmartSocket(Socket real) {
            this.real = real;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            InputStream s = sniffedStream;
            if (s != null) {
                return s;
            }
            synchronized (this) {
                if (sniffedStream == null) {
                    sniffedStream = doSniff();
                }
                return sniffedStream;
            }
        }

        private InputStream doSniff() throws IOException {
            try {
                return doSniffInner();
            } catch (Exception e) {
                log.warn("[adapter/v1_6] doSniff 异常", e);
                throw e;
            }
        }

        private InputStream doSniffInner() throws IOException {
            log.debug("[adapter/v1_6] doSniff 开始: " + real.getRemoteSocketAddress());
            InputStream raw = real.getInputStream();
            int oldTimeout;
            try {
                oldTimeout = real.getSoTimeout();
            } catch (SocketException se) {
                oldTimeout = 0;
            }
            real.setSoTimeout(SNIFF_TIMEOUT_MS);
            // TLS 识别：仅当插件已配置 TLS 上下文时开启，避免把真实 MC 连接（首字节 0x02/0xFE/VarInt）误判为 TLS
            final boolean tlsEnabled = sslContextSupplier != null && sslContextSupplier.get() != null;
            ByteArrayOutputStream head = new ByteArrayOutputStream(SNIFF_MAX_BYTES);
            boolean http = false;
            boolean tls = false;
            try {
                while (true) {
                    int b = raw.read();
                    if (b < 0) {
                        break;
                    }
                    head.write(b);
                    byte[] h = head.toByteArray();
                    HttpByteProtocol.State st = HttpByteProtocol.classify(h, h.length, tlsEnabled);
                    log.debug("[adapter/v1_6] doSniff classify: bytes=" + h.length + " state=" + st + " data=" + new String(h, java.nio.charset.StandardCharsets.ISO_8859_1));
                    if (st == HttpByteProtocol.State.HTTP_PLAIN) {
                        http = true;
                        break;
                    }
                    if (st == HttpByteProtocol.State.HTTP_TLS) {
                        tls = true;
                        break;
                    }
                    if (st == HttpByteProtocol.State.MC || h.length >= SNIFF_MAX_BYTES) {
                        break;
                    }
                }
            } catch (SocketTimeoutException e) {
                // 超时：按已读内容分类（不足则按 MC 放行）
            } finally {
                try {
                    real.setSoTimeout(oldTimeout);
                } catch (SocketException ignored) {
                }
            }
            byte[] h = head.toByteArray();
            if (tls) {
                // TLS 握手：在原生 Socket 上就地终止 TLS，解密后走现有 HTTP 链（与 1.7/1.8 同端口 HTTPS 一致）
                return doTls(h);
            }
            if (!http) {
                log.debug("[adapter/v1_6] doSniff 判定非HTTP放行MC: bytes=" + h.length);
                // MC / UNKNOWN（无 TLS 上下文时 TLS 也落此分支）→ 首包+原流拼接
                return new SequenceInputStream(new ByteArrayInputStream(h), raw);
            }
            log.debug("[adapter/v1_6] doSniff 判定明文HTTP，分流HTTP线程: bytes=" + h.length);
            // HTTP 分流：真实 Socket 交给插件线程池；向 MC 侧返回 EOF 流（PendingConnection 快速失败）
            httpTaken = true;
            InputStream httpIn = new SequenceInputStream(new ByteArrayInputStream(h), raw);
            OutputStream httpOut = real.getOutputStream();
            handleHttpConnection(httpIn, httpOut, real, real.getInetAddress(), false);
            return new ByteArrayInputStream(new byte[0]);
        }

        /**
         * TLS 分支：把已嗅探出的 ClientHello 首包回放给 SSLSocket，在原生连接上就地完成 TLS 握手，
         * 解密后的明文流交给 HTTP 线程池（与 1.7/1.8 同端口 HTTPS 语义一致）。MC 侧拿到 EOF 流快速失败。
         */
        private InputStream doTls(byte[] peeked) throws IOException {
            SSLContext ctx = sslContextSupplier == null ? null : sslContextSupplier.get();
            if (ctx == null) {
                // 防御：上下文在并发下不可用，回退 MC 放行
                log.warn("[adapter/v1_6] TLS 上下文不可用，按 MC 放行: bytes=" + peeked.length);
                return new SequenceInputStream(new ByteArrayInputStream(peeked), real.getInputStream());
            }
            Socket replay = new ReplaySocket(real, peeked);
            SSLSocket ssl = (SSLSocket) ctx.getSocketFactory()
                    .createSocket(replay, real.getInetAddress().getHostAddress(), real.getPort(), true);
            ssl.setUseClientMode(false);
            log.debug("[adapter/v1_6] doSniff 判定TLS，就地升级 HTTPS: bytes=" + peeked.length);
            httpTaken = true;
            handleHttpConnection(ssl.getInputStream(), ssl.getOutputStream(), ssl, real.getInetAddress(), true);
            return new ByteArrayInputStream(new byte[0]);
        }

        // ===== 委托给真实 Socket（PendingConnection 对 Socket 的所有访问透明） =====

        @Override
        public OutputStream getOutputStream() throws IOException {
            OutputStream realOut = real.getOutputStream();
            if (httpTaken) {
                // HTTP 分流后：MC 侧 NetworkManager 断开时会 close() 该流（DataOutputStream→BufferedOutputStream→本流），
                // 若放行会关闭 real 写端，导致 HTTP 线程写响应失败（Socket closed）。
                // 拦截 close（仅 flush），连接生命周期由 HTTP 线程管理。
                return new FilterOutputStream(realOut) {
                    @Override
                    public void close() throws IOException {
                        flush();
                    }
                };
            }
            return realOut;
        }

        @Override
        public InetAddress getInetAddress() {
            return real.getInetAddress();
        }

        @Override
        public int getPort() {
            return real.getPort();
        }

        @Override
        public InetAddress getLocalAddress() {
            return real.getLocalAddress();
        }

        @Override
        public int getLocalPort() {
            return real.getLocalPort();
        }

        @Override
        public SocketAddress getRemoteSocketAddress() {
            return real.getRemoteSocketAddress();
        }

        @Override
        public SocketAddress getLocalSocketAddress() {
            return real.getLocalSocketAddress();
        }

        @Override
        public boolean isConnected() {
            return real.isConnected();
        }

        @Override
        public boolean isClosed() {
            return real.isClosed();
        }

        @Override
        public boolean isBound() {
            return real.isBound();
        }

        @Override
        public boolean isInputShutdown() {
            return real.isInputShutdown();
        }

        @Override
        public boolean isOutputShutdown() {
            return real.isOutputShutdown();
        }

        @Override
        public void setSoTimeout(int timeout) throws SocketException {
            real.setSoTimeout(timeout);
        }

        @Override
        public int getSoTimeout() throws SocketException {
            return real.getSoTimeout();
        }

        @Override
        public void setTcpNoDelay(boolean on) throws SocketException {
            real.setTcpNoDelay(on);
        }

        @Override
        public boolean getTcpNoDelay() throws SocketException {
            return real.getTcpNoDelay();
        }

        @Override
        public void shutdownInput() throws IOException {
            real.shutdownInput();
        }

        @Override
        public void shutdownOutput() throws IOException {
            real.shutdownOutput();
        }

        @Override
        public void close() throws IOException {
            if (httpTaken) {
                return; // 已移交给 HTTP 线程，由 HTTP 线程管理连接生命周期
            }
            real.close();
        }
    }
}
