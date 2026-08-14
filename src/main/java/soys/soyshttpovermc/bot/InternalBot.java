package soys.soyshttpovermc.bot;

import soys.soyshttpovermc.log.LogKit;

import com.github.steveice10.mc.protocol.MinecraftProtocol;
import com.github.steveice10.mc.protocol.data.SubProtocol;
import com.github.steveice10.mc.protocol.packet.ingame.client.ClientPluginMessagePacket;
import com.github.steveice10.mc.protocol.packet.ingame.client.world.ClientTeleportConfirmPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerPluginMessagePacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerKeepAlivePacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.player.ServerPlayerPositionRotationPacket;
import com.github.steveice10.packetlib.Client;
import com.github.steveice10.packetlib.Session;
import com.github.steveice10.packetlib.event.session.PacketReceivedEvent;
import com.github.steveice10.packetlib.event.session.SessionAdapter;
import com.github.steveice10.packetlib.event.session.ConnectedEvent;
import com.github.steveice10.packetlib.event.session.DisconnectedEvent;
import com.github.steveice10.packetlib.packet.Packet;
import com.github.steveice10.packetlib.tcp.TcpSessionFactory;

import org.bukkit.plugin.java.JavaPlugin;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 内部无头 Bot：使用 MCProtocolLib 作为虚拟客户端，回环连接本服
 * (127.0.0.1:serverPort)，离线登录后一旦进入 GAME 子协议就 REGISTER 自定义通道，
 * 使服务端能够把 HttpResponseFrame 通过 PluginMessage 回发回来。
 */
public class InternalBot {

    public interface RawMessageListener {
        void onRawMessage(String channel, byte[] data);
    }

    private static final String REGISTER_CHANNEL = "minecraft:register";

    private final JavaPlugin plugin;
    private final String username;
    private final String channel;
    private final String host;
    private final int port;

    private Client client;
    private Session session;
    private RawMessageListener listener;
    private final ExecutorService sender = Executors.newSingleThreadExecutor();

    private volatile boolean registered = false;
    private volatile boolean inGame = false;

    public InternalBot(JavaPlugin plugin, String username, String channel, String host, int port) {
        this.plugin = plugin;
        this.username = username;
        this.channel = channel;
        this.host = host;
        this.port = port;
    }

    public void setRawMessageListener(RawMessageListener listener) {
        this.listener = listener;
    }

    /** 隧道是否就绪：Bot 已连接且已 REGISTER 自定义通道 */
    public boolean isReady() {
        return registered && session != null && session.isConnected();
    }

    public void connect() {
        LogKit.info("[HTTP-Over-MC] Bot 正在连接 " + host + ":" + port + " user=" + username);
        MinecraftProtocol protocol = new MinecraftProtocol(username);
        client = new Client(host, port, protocol, new TcpSessionFactory());
        session = client.getSession();
        session.addListener(new BotSessionListener());
        session.connect();
    }

    public void disconnect() {
        registered = false;
        inGame = false;
        sender.shutdownNow();
        if (session != null && session.isConnected()) {
            session.disconnect("HTTP-Over-MC shutting down");
        }
    }

    public void sendChannelMessage(String ch, byte[] data) {
        if (!waitUntilRegistered()) {
            LogKit.warn("[HTTP-Over-MC] Bot 尚未就绪，丢弃请求帧（通道=" + ch + "）");
            return;
        }
        if (session != null && session.isConnected()) {
            sender.submit(() -> session.send(new ClientPluginMessagePacket(ch, data)));
        }
    }

    private boolean waitUntilRegistered() {
        long deadline = System.currentTimeMillis() + 15000;
        while (!registered && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return registered;
    }

    private void registerChannel() {
        if (registered || session == null || !session.isConnected()) {
            return;
        }
        registered = true;
        final Session s = session;
        sender.submit(() -> {
            s.send(new ClientPluginMessagePacket(REGISTER_CHANNEL, channel.getBytes(StandardCharsets.UTF_8)));
            LogKit.info("[HTTP-Over-MC] Bot 已发送通道注册 " + channel);
        });
    }

    /**
     * 向服务端注册额外 PluginMessage 通道（供门面 registerChannel 使用）。
     * 主通道已在进入 GAME 时自动注册；此方法用于让同一 Bot 监听更多自定义通道，
     * 服务端才会把对应通道消息投递给该 Bot。需在 Bot 就绪后调用。
     */
    public void registerExtraChannel(String ch) {
        if (ch == null || ch.isEmpty() || !waitUntilRegistered()) {
            return;
        }
        final Session s = session;
        if (s != null && s.isConnected()) {
            sender.submit(() -> s.send(new ClientPluginMessagePacket(REGISTER_CHANNEL, ch.getBytes(StandardCharsets.UTF_8))));
            LogKit.info("[HTTP-Over-MC] Bot 已注册额外通道 " + ch);
        }
    }

    /** 注销额外 PluginMessage 通道（发送 minecraft:unregister）。 */
    public void unregisterExtraChannel(String ch) {
        if (ch == null || ch.isEmpty() || session == null || !session.isConnected()) {
            return;
        }
        final Session s = session;
        sender.submit(() -> s.send(new ClientPluginMessagePacket("minecraft:unregister", ch.getBytes(StandardCharsets.UTF_8))));
        LogKit.info("[HTTP-Over-MC] Bot 已注销通道 " + ch);
    }

    private void asyncSend(Packet p, String label) {
        final Session s = session;
        if (s == null || !s.isConnected()) {
            return;
        }
        sender.submit(() -> {
            s.send(p);
            LogKit.info("[HTTP-Over-MC] 已发送 " + label);
        });
    }

    private class BotSessionListener extends SessionAdapter {
        @Override
        public void connected(ConnectedEvent event) {
            LogKit.info("[HTTP-Over-MC] Bot TCP 已连接，等待登录完成");
        }

        @Override
        public void disconnected(DisconnectedEvent event) {
            registered = false;
            inGame = false;
            Throwable cause = event.getCause();
            LogKit.warn("[HTTP-Over-MC] Bot 断开连接: reason=" + event.getReason()
                    + (cause != null ? " | cause=" + cause : " | cause=null"));
            if (cause != null) {
                cause.printStackTrace();
            }
        }

        @Override
        public void packetReceived(PacketReceivedEvent event) {
            try {
                Session s = event.getSession();
                Packet p = event.getPacket();

                if (s.getPacketProtocol() instanceof MinecraftProtocol) {
                    MinecraftProtocol mp = (MinecraftProtocol) s.getPacketProtocol();
                    if (!inGame && mp.getSubProtocol() == SubProtocol.GAME) {
                        inGame = true;
                        LogKit.info("[HTTP-Over-MC] Bot 进入 GAME，开始登记通道");
                        registerChannel();
                    }
                }

                // 注意：MCProtocolLib 的 ClientListener 已自动回应 ServerKeepAlivePacket，
                // 这里【不能】再手动回 KeepAlive，否则重复回应会让服务端 keepAlivePending 已被清除后
                // 又收到一条，触发 Spigot 的 disconnect.timeout 立即踢人。
                if (p instanceof ServerKeepAlivePacket) {
                    return;
                }

                if (p instanceof ServerPlayerPositionRotationPacket) {
                    int tid = ((ServerPlayerPositionRotationPacket) p).getTeleportId();
                    asyncSend(new ClientTeleportConfirmPacket(tid), "TeleportConfirm id=" + tid);
                    return;
                }

                if (p instanceof ServerPluginMessagePacket) {
                    ServerPluginMessagePacket sp = (ServerPluginMessagePacket) p;
                    byte[] d = sp.getData();
                    LogKit.info("[HTTP-Over-MC] Bot 收到服务端 PluginMessage channel="
                            + sp.getChannel() + " len=" + (d == null ? -1 : d.length));
                    if (listener != null) {
                        listener.onRawMessage(sp.getChannel(), sp.getData());
                    }
                }
            } catch (Throwable t) {
                LogKit.warn("[HTTP-Over-MC] Bot 处理数据包异常", t);
            }
        }
    }
}
