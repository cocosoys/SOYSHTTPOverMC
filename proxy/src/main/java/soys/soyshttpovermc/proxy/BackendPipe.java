package soys.soyshttpovermc.proxy;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.plugin.Plugin;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 客户端(25577) ↔ 目标后端 MC 端口 的 TCP 桥（反向代理 legs）。
 *
 * <p><b>设计：每条客户端 TLS 连接对应一条全新的后端 TLS 连接（一对一，secure=true）。</b>
 * 代理自身终止客户端 TLS（{@link HttpClassifier} + SslHandler），明文 HTTP 由
 * {@link PlainHttpHandler} 路由后，经本条桥转发到后端既有 HTTPS 栈；响应明文回写，出站再由
 * SslHandler 加密给客户端。
 *
 * <p>为何不复用后端连接池：后端 HTTPS 栈对每个 TLS 会话有空闲超时（实测 ~10-15s），复用常驻连接
 * 服务第二条客户端时其 SSL 会话可能已失效 → 空响应(size=0)；且透明透传复用会引发第二条客户端 TLS
 * 会话冲突（后端 RST）。故采用“每客户端一条新后端 TLS 连接 + 并发建连串行最小间隔”
 * （{@link ProxyConfig#getConnectionSpacingMs()}），既避开会话复用冲突，又消除微秒级密集建连触发的
 * 后端 MC 端口 spike-drop（静默 RST 第 4+ 连接 → 客户端 ERR_CONNECTION_ABORTED）。
 *
 * <p>客户端方向字节由 {@link HttpClassifier} 接管后逐段 {@link #writeClient} 写入后端；
 * 后端方向字节在独立线程读回并 writeAndFlush 给客户端 channel。
 */
public class BackendPipe {

    private final Plugin plugin;
    private final Socket backend;
    private final OutputStream out;
    private final InputStream in;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final ChannelHandlerContext clientCtx;
    private final Thread backendToClient;
    private Runnable onCloseOrig = null;

    /** 直连构造：自行以 TLS 客户端(secure=true)连接后端。 */
    public BackendPipe(Plugin plugin, ServerInfo target, int timeoutMs, ChannelHandlerContext clientCtx)
            throws IOException {
        this(plugin, target, timeoutMs, clientCtx, false);
    }

    public BackendPipe(Plugin plugin, ServerInfo target, int timeoutMs, ChannelHandlerContext clientCtx,
                       boolean secure) throws IOException {
        this(plugin, target, timeoutMs, clientCtx, secure, null);
    }

    public BackendPipe(Plugin plugin, ServerInfo target, int timeoutMs, ChannelHandlerContext clientCtx,
                       boolean secure, Runnable onClose) throws IOException {
        this.plugin = plugin;
        this.clientCtx = clientCtx;
        this.onCloseOrig = onClose;
        InetSocketAddress addr = target.getAddress();
        if (secure) {
            try {
                javax.net.ssl.SSLSocket s = (javax.net.ssl.SSLSocket) trustAllFactory().createSocket();
                s.connect(addr, timeoutMs);
                s.startHandshake();
                this.backend = s;
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException("TLS 连接后端失败: " + e, e);
            }
        } else {
            this.backend = new Socket();
            this.backend.connect(addr, timeoutMs);
        }
        this.out = backend.getOutputStream();
        this.in = backend.getInputStream();
        this.backendToClient = new Thread(this::pumpBackendToClient, "soys-pipe-" + target.getName());
        this.backendToClient.setDaemon(true);
        this.backendToClient.start();
    }

    /** 客户端→后端：把截获的字节写入后端 socket。 */
    public void writeClient(byte[] data) {
        if (closed.get() || data == null || data.length == 0) return;
        try {
            out.write(data);
            out.flush();
        } catch (IOException e) {
            close();
        }
    }

    private void pumpBackendToClient() {
        byte[] b = new byte[16384];
        try {
            int n;
            while (!closed.get() && (n = in.read(b)) != -1) {
                if (n <= 0) continue;
                final byte[] copy = new byte[n];
                System.arraycopy(b, 0, copy, 0, n);
                if (clientCtx != null && clientCtx.channel().isActive()) {
                    clientCtx.writeAndFlush(Unpooled.copiedBuffer(copy));
                }
            }
        } catch (IOException ignored) {
            // 连接关闭或后端 RST
        } finally {
            close();
        }
    }

    public void close() {
        if (closed.compareAndSet(false, true)) {
            try { out.close(); } catch (Exception ignore) {}
            try { in.close(); } catch (Exception ignore) {}
            try { backend.close(); } catch (Exception ignore) {}
            if (onCloseOrig != null) {
                try { onCloseOrig.run(); } catch (Throwable ignore) {}
            }
            if (clientCtx != null) clientCtx.close();
        }
    }

    /**
     * 信任全部证书的 SSLSocketFactory（后端证书通常是自签内网证书，且目标由代理配置决定，非用户输入）。
     * 等价于 curl -k。
     */
    static SSLSocketFactory trustAllFactory() throws Exception {
        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, new TrustManager[]{new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] c, String a) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] c, String a) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        }}, new SecureRandom());
        return sc.getSocketFactory();
    }
}
