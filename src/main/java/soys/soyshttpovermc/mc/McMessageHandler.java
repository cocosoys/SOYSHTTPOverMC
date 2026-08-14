package soys.soyshttpovermc.mc;

import soys.soyshttpovermc.bot.BotTier;
import soys.soyshttpovermc.log.LogKit;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import soys.soyshttpovermc.proto.FrameProto;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端侧：接收来自虚拟 Bot 的 PluginMessage，解析 HttpRequestFrame，
 * 路由为静态资源 / 动态接口（替代第一版的可读回显），证明隧道端到端打通。
 * 安全规则：只处理来自虚拟 Bot 的消息。支持大请求多分片重组（与客户端 McLink 对称）。
 *
 * 本次重构：重组完成后不再同步处理，而是按请求携带的 tier（X-SOYS-TIER 头）提交给
 * {@link RequestScheduler} 的对应逻辑队列，由 worker 线程按优先级处理；本回调线程只做
 * 解析/重组/入队，不执行 web.handle，从而避免重活阻塞入站与主线程。
 * 分片重组表（pending）增加 TTL 淘汰与分片数上限，防止半截分片堆积导致内存耗尽。
 */
public class McMessageHandler implements PluginMessageListener {

    private final JavaPlugin plugin;
    private final String botUsername;
    private final String channel;
    private final RequestScheduler scheduler;

    private final Map<Long, PendingReq> pending = new ConcurrentHashMap<>();
    /** 单次请求最大分片数（防 pending.buffers 超大分配），超过则丢弃 */
    private static final int MAX_FRAGMENTS = 4096;
    /** 分片重组超时（毫秒）：超过未补齐即淘汰，防内存堆积 */
    private static final long FRAGMENT_TTL_MS = 30_000;

    public McMessageHandler(JavaPlugin plugin, String botUsername, String channel, RequestScheduler scheduler) {
        this.plugin = plugin;
        this.botUsername = botUsername;
        this.channel = channel;
        this.scheduler = scheduler;
        // 分片 pending 表 TTL 淘汰（异步调度，不占主线程）
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::sweepFragments, 200L, 200L);
    }

    @Override
    public void onPluginMessageReceived(String ch, Player player, byte[] message) {
        if (!channel.equals(ch)) {
            return;
        }
        // 安全规则：忽略所有非虚拟 Bot 发来的该通道消息，防止伪造请求
        if (!botUsername.equals(player.getName())) {
            return;
        }
        try {
            FrameProto.HttpRequestFrame chunk = FrameProto.HttpRequestFrame.parseFrom(message);

            // 关键修复：见原实现说明——强制把 Bot 加入 listening 集合，保证响应可达。
            if (!player.getListeningPluginChannels().contains(channel)) {
                ensureListening(player, channel);
                LogKit.info("[HTTP-Over-MC] 已为 Bot 强制登记监听通道 " + channel
                        + " -> listening=" + player.getListeningPluginChannels());
            }

            long id = chunk.getRequestId();
            int rawTotal = chunk.getTotalFragments();
            final int total = rawTotal < 1 ? 1 : rawTotal;
            if (total > MAX_FRAGMENTS) {
                LogKit.warn("[HTTP-Over-MC] 分片数超限，丢弃请求 id=" + id + " total=" + total);
                return;
            }
            PendingReq pr = pending.computeIfAbsent(id, k -> new PendingReq(total));
            int idx = Math.min(chunk.getFragmentIndex(), total - 1);
            pr.buffers[idx] = chunk.getBody().toByteArray();
            pr.lastTouch = System.currentTimeMillis();
            if (++pr.received < total) {
                return; // 等待更多分片
            }
            pending.remove(id);

            // 重组出原始请求帧（chunk body 是原始请求帧序列化的一个分片）
            byte[] full = join(pr.buffers);
            FrameProto.HttpRequestFrame req = FrameProto.HttpRequestFrame.parseFrom(full);

            // 按规则控制器写入的 tier 头决定逻辑队列（默认 COMMON）
            BotTier tier = BotTier.COMMON;
            String tierName = req.getHeadersMap().get("X-SOYS-TIER");
            if (tierName != null) {
                try {
                    tier = BotTier.valueOf(tierName);
                } catch (IllegalArgumentException ignored) {
                }
            }
            // 去掉内部头，避免泄漏给业务 handler
            Map<String, String> clean = new java.util.HashMap<>(req.getHeadersMap());
            clean.remove("X-SOYS-TIER");

            // 提交到对应 tier 队列，由 RequestScheduler 按优先级异步处理（不再同步执行 web.handle）
            scheduler.submit(tier, player, id, req.getMethod(), req.getPath(), clean, req.getBody().toByteArray());
        } catch (Exception e) {
            LogKit.warn("[HTTP-Over-MC] 处理消息失败: " + e);
        }
    }

    /** 分片 pending 表定时淘汰：超过 TTL 未补齐的请求直接丢弃（客户端侧会 30s 超时回 502）。 */
    private void sweepFragments() {
        long now = System.currentTimeMillis();
        for (Map.Entry<Long, PendingReq> en : pending.entrySet()) {
            if (now - en.getValue().lastTouch > FRAGMENT_TTL_MS) {
                pending.remove(en.getKey());
            }
        }
    }

    private static byte[] join(byte[][] parts) {
        int len = 0;
        for (byte[] p : parts) {
            if (p != null) len += p.length;
        }
        byte[] out = new byte[len];
        int off = 0;
        for (byte[] p : parts) {
            if (p != null) {
                System.arraycopy(p, 0, out, off, p.length);
                off += p.length;
            }
        }
        return out;
    }

    /**
     * 强制把玩家加入某通道的 listening 集合（1.12.2 仅 CraftPlayer 提供 addChannel，反射兼容）。
     */
    private void ensureListening(Player player, String ch) {
        try {
            java.lang.reflect.Method m;
            try {
                m = Player.class.getMethod("addListeningPluginChannel", String.class);
            } catch (NoSuchMethodException e) {
                m = player.getClass().getMethod("addChannel", String.class);
            }
            m.invoke(player, ch);
        } catch (Exception e) {
            LogKit.warn("[HTTP-Over-MC] 无法强制登记监听通道 " + ch + ": " + e);
        }
    }

    private static class PendingReq {
        final byte[][] buffers;
        int received = 0;
        long lastTouch = System.currentTimeMillis();

        PendingReq(int total) {
            this.buffers = new byte[total][];
        }
    }
}
