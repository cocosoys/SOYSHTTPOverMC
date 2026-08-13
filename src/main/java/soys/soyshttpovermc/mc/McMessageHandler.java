package soys.soyshttpovermc.mc;

import soys.soyshttpovermc.log.LogKit;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import soys.soyshttpovermc.proto.FrameProto;
import soys.soyshttpovermc.web.WebFrontendHandler;

import com.google.protobuf.ByteString;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端侧：接收来自虚拟 Bot 的 PluginMessage，解析 HttpRequestFrame，
 * 路由为静态资源 / 动态接口（替代第一版的可读回显），证明隧道端到端打通。
 * 安全规则：只处理来自虚拟 Bot 的消息。支持大请求多分片重组（与客户端 McLink 对称）。
 */
public class McMessageHandler implements PluginMessageListener {

    private final JavaPlugin plugin;
    private final String botUsername;
    private final String channel;
    private final WebFrontendHandler web;

    private final Map<Long, PendingReq> pending = new ConcurrentHashMap<>();

    public McMessageHandler(JavaPlugin plugin, String botUsername, String channel, WebFrontendHandler web) {
        this.plugin = plugin;
        this.botUsername = botUsername;
        this.channel = channel;
        this.web = web;
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

            // 关键修复：Spigot 仅在 player.getListeningPluginChannels() 含该通道时，才会向客户端
            // 投递 sendPluginMessage；无头 Bot 经 MCProtocolLib 发出的 "minecraft:register" 在
            // 1.12.2 下未被服务端识别（实测 listening=[]），导致响应被静默丢弃、translate() 超时、
            // curl 无响应。这里直接调用 CraftPlayer.addChannel 强制把 Bot 加入 listening 集合，
            // 保证响应可达。幂等、无副作用。
            if (!player.getListeningPluginChannels().contains(channel)) {
                ensureListening(player, channel);
                LogKit.info("[HTTP-Over-MC] 已为 Bot 强制登记监听通道 " + channel
                        + " -> listening=" + player.getListeningPluginChannels());
            }

            long id = chunk.getRequestId();
            int rawTotal = chunk.getTotalFragments();
            final int total = rawTotal < 1 ? 1 : rawTotal;
            PendingReq pr = pending.computeIfAbsent(id, k -> new PendingReq(total));
            int idx = Math.min(chunk.getFragmentIndex(), total - 1);
            pr.buffers[idx] = chunk.getBody().toByteArray();
            if (++pr.received < total) {
                return; // 等待更多分片
            }
            pending.remove(id);

            // 重组出原始请求帧（chunk body 是原始请求帧序列化的一个分片）
            byte[] full = join(pr.buffers);
            FrameProto.HttpRequestFrame req = FrameProto.HttpRequestFrame.parseFrom(full);

            LogKit.info("[HTTP-Over-MC] 收到请求 method=" + req.getMethod()
                    + " path=" + req.getPath() + " (分片=" + total + ")");

            // 路由为前端资源 / 动态接口
            FrameProto.HttpResponseFrame resp = web.handle(
                    req.getMethod(), req.getPath(), req.getHeadersMap(), req.getBody().toByteArray());
            // 关键：响应帧必须带回原始 request_id，否则客户端 McLink 的多路复用 pending 表
            // 按真实 id 查找会 miss（WebFrontendHandler 不感知 id，默认 0），导致响应被丢弃、
            // 隧道 future 超时、curl 收到 502。
            resp = resp.toBuilder().setRequestId(req.getRequestId()).build();

            // 按 32000 字节上限分片发回（响应帧整体切片，客户端 McLink 对称重组）
            List<byte[]> chunks = split(resp.toByteArray());
            int nt = chunks.size();
            for (int i = 0; i < nt; i++) {
                FrameProto.HttpResponseFrame c = resp.toBuilder()
                        .clearBody()
                        .setBody(ByteString.copyFrom(chunks.get(i)))
                        .setFragmentIndex(i)
                        .setTotalFragments(nt)
                        .build();
                player.sendPluginMessage(plugin, channel, c.toByteArray());
            }
        } catch (Exception e) {
            LogKit.warn("[HTTP-Over-MC] 处理消息失败: " + e);
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

    private static List<byte[]> split(byte[] data) {
        final int MAX = 32000;
        if (data.length <= MAX) {
            return Arrays.asList(data);
        }
        int n = (data.length + MAX - 1) / MAX;
        java.util.ArrayList<byte[]> list = new java.util.ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int s = i * MAX;
            int e = Math.min(data.length, s + MAX);
            list.add(Arrays.copyOfRange(data, s, e));
        }
        return list;
    }

    /**
     * 强制把玩家加入某通道的 listening 集合。
     * 高版本 Bukkit 在 Player 接口上提供 addListeningPluginChannel；1.12.2 仅 CraftPlayer 提供
     * addChannel(String)（且服务端处理 minecraft:register 时正是调用它）。两者都通过反射兼容调用。
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

        PendingReq(int total) {
            this.buffers = new byte[total][];
        }
    }
}
