package soys.soyshttpovermc.proxy;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.Listener;

import java.lang.reflect.Field;
import java.util.Collection;

/**
 * BungeeCord 端代理插件。在 BungeeCord 监听器（config.yml 中配置的端口）上做首包分类：
 * <ul>
 *   <li>MC 握手 → 交还 BungeeCord 正常代理玩家；</li>
 *   <li>HTTP / HTTPS → 按路由决策透传到目标后端 MC 端口（由后端既有 HTTP 栈处理并回写），
 *       或由本模块自身（home-server=self）托管静态页面。</li>
 * </ul>
 * 关键机制：BungeeCord 的 {@code listeners} 是 {@code Collection<Channel>}（各监听器父通道）。
 * 在每个父通道 pipeline 最前注入一个 handler，于其 {@code channelRead}（子连接到达时）对被接受的
 * {@code SocketChannel} 的 pipeline 最前再注入 {@code HttpClassifier} 首包分类器。
 * 因 BungeeCord 的 {@code PipelineUtils$Base} 把所有基础 handler（含 FRAME_DECODER）用 addLast 添加，
 * 故我们的 addFirst 分类器必在其之前，HTTP/TLS 字节先被我们截获。
 */
public class SoysProxyPlugin extends Plugin implements Listener {

    private ProxyConfig config;

    @Override
    public void onEnable() {
        config = ProxyConfig.load(this);
        if (!config.isEnabled()) {
            getLogger().info("[SOYS-Proxy] 未启用(enabled=false)。单服/未配置群组代理时默认关闭，仅做 MC 正常代理。");
            return;
        }
        getLogger().info("[SOYS-Proxy] 启用。home-server=" + config.getHomeServer()
                + " web-root=" + config.getWebRoot()
                + " connection-spacing-ms=" + config.getConnectionSpacingMs()
                + " backend-connect-timeout-ms=" + config.getPoolAcquireMs());
        // BungeeCord 在插件 onEnable 之后才启动监听器(startListeners)，此时 listeners 尚为空。
        // 故延迟注入：先尝试一次，再用调度器在 2 秒后重试（幂等，已注入则跳过）。
        inject();
        getProxy().getScheduler().schedule(this, this::inject, 2L, java.util.concurrent.TimeUnit.SECONDS);
        getProxy().getScheduler().schedule(this, this::inject, 5L, java.util.concurrent.TimeUnit.SECONDS);
    }

    @Override
    public void onDisable() {
        getLogger().info("[SOYS-Proxy] 已停用。");
    }

    @SuppressWarnings("unchecked")
    private void inject() {
        try {
            // 反射拿 BungeeCord.listeners（Collection<Channel> 父 server channel）
            Field f = getProxy().getClass().getDeclaredField("listeners");
            f.setAccessible(true);
            Collection<Channel> channels = (Collection<Channel>) f.get(getProxy());
            if (channels == null) {
                getLogger().severe("[SOYS-Proxy] 无法取得监听器通道集合(listeners 为 null)。");
                return;
            }
            int n = 0;
            for (Channel ch : channels) {
                if (ch.pipeline().get("soys-proxy-injector") != null) continue; // 防重复注入
                ch.pipeline().addFirst("soys-proxy-injector", new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                        if (msg instanceof SocketChannel) {
                            SocketChannel child = (SocketChannel) msg;
                            if (child.pipeline().get(HttpClassifier.NAME) == null) {
                                child.pipeline().addFirst(HttpClassifier.NAME,
                                        new HttpClassifier(SoysProxyPlugin.this, config));
                            }
                        }
                        ctx.fireChannelRead(msg);
                    }
                });
                n++;
            }
            getLogger().info("[SOYS-Proxy] 已在 " + n + " 个监听器通道注入首包分类器。");
        } catch (Throwable t) {
            getLogger().severe("[SOYS-Proxy] 注入监听器失败: " + t);
            t.printStackTrace();
        }
    }
}
