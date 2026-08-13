package soys.soyshttpovermc.mc;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import soys.soyshttpovermc.proto.FrameProto;

import com.google.protobuf.ByteString;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 服务端侧：接收来自虚拟 Bot 的 PluginMessage，解析 HttpRequestFrame，
 * 第一版做可读回显，证明隧道端到端打通。安全规则：只处理来自虚拟 Bot 的消息。
 */
public class McMessageHandler implements PluginMessageListener {

    private final JavaPlugin plugin;
    private final String botUsername;
    private final String channel;

    public McMessageHandler(JavaPlugin plugin, String botUsername, String channel) {
        this.plugin = plugin;
        this.botUsername = botUsername;
        this.channel = channel;
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
            FrameProto.HttpRequestFrame req = FrameProto.HttpRequestFrame.parseFrom(message);

            // 关键修复：Spigot 仅在 player.getListeningPluginChannels() 含该通道时，才会向客户端
            // 投递 sendPluginMessage；无头 Bot 经 MCProtocolLib 发出的 "minecraft:register" 在
            // 1.12.2 下未被服务端识别（实测 listening=[]），导致响应被静默丢弃、translate() 超时、
            // curl 无响应。这里直接调用 CraftPlayer.addChannel（即服务端处理 register 时使用的同一
            // 内部方法）强制把 Bot 加入 listening 集合，保证响应可达。幂等、无副作用。
            if (!player.getListeningPluginChannels().contains(channel)) {
                ensureListening(player, channel);
                plugin.getLogger().info("[HTTP-Over-MC] 已为 Bot 强制登记监听通道 " + channel
                        + " -> listening=" + player.getListeningPluginChannels());
            }

            // 重建原始请求帧（单分片时 chunk body 即原始请求帧的序列化），用于可读回显
            FrameProto.HttpRequestFrame original = req;
            try {
                if (req.getTotalFragments() <= 1) {
                    original = FrameProto.HttpRequestFrame.parseFrom(req.getBody());
                }
            } catch (Exception ignored) {
            }

            String bodyStr;
            try {
                bodyStr = new String(original.getBody().toByteArray(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                bodyStr = "<binary:" + original.getBody().size() + " bytes>";
            }

            plugin.getLogger().info("[HTTP-Over-MC] 收到请求 method=" + original.getMethod()
                    + " path=" + original.getPath());

            // 第一版简单回显：把请求以可读文本返回，证明隧道端到端打通
            StringBuilder sb = new StringBuilder();
            sb.append("HTTP-Over-MC echo\r\nmethod=").append(original.getMethod())
                    .append("\r\npath=").append(original.getPath()).append("\r\nheaders:\r\n");
            for (Map.Entry<String, String> e : original.getHeadersMap().entrySet()) {
                sb.append("  ").append(e.getKey()).append(": ").append(e.getValue()).append("\r\n");
            }
            sb.append("body: ").append(bodyStr.isEmpty() ? "(empty)" : bodyStr).append("\r\n");
            byte[] echoBody = sb.toString().getBytes(StandardCharsets.UTF_8);

            FrameProto.HttpResponseFrame resp = FrameProto.HttpResponseFrame.newBuilder()
                    .setRequestId(req.getRequestId())
                    .setStatusCode(200)
                    .putHeaders("Content-Type", "text/plain; charset=utf-8")
                    .setBody(ByteString.copyFrom(echoBody))
                    .setFragmentIndex(0)
                    .setTotalFragments(1)
                    .build();

            // 分片发送（第一版 body 很小，通常单帧）
            List<byte[]> chunks = split(resp.toByteArray());
            int total = chunks.size();
            for (int i = 0; i < total; i++) {
                FrameProto.HttpResponseFrame chunk = FrameProto.HttpResponseFrame.newBuilder()
                        .setRequestId(req.getRequestId())
                        .setStatusCode(200)
                        .putHeaders("Content-Type", "text/plain; charset=utf-8")
                        .setBody(ByteString.copyFrom(chunks.get(i)))
                        .setFragmentIndex(i)
                        .setTotalFragments(total)
                        .build();
                player.sendPluginMessage(plugin, channel, chunk.toByteArray());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[HTTP-Over-MC] 处理消息失败: " + e);
        }
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
            plugin.getLogger().warning("[HTTP-Over-MC] 无法强制登记监听通道 " + ch + ": " + e);
        }
    }
}
