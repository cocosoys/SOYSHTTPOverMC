package soys.soyshttpovermc.bot;

import lombok.Data;
import lombok.Getter;
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

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 内部无头 Bot：使用 MCProtocolLib 作为虚拟客户端，回环连接本服
 * (127.0.0.1:serverPort)，离线登录后一旦进入 GAME 子协议就 REGISTER 自定义通道，
 * 使服务端能够把 HttpResponseFrame 通过 PluginMessage 回发回来。
 */
public class InternalBot {

    public interface RawMessageListener {
        void onRawMessage(String channel, byte[] data);
    }

    /**
     * PluginMessage 通道注册包所用的通道名。
     * <b>注意</b>：Spigot 1.12.2 使用 legacy 通道名 {@code REGISTER}（服务端向 Bot 下发的注册包即为该名，
     * 见 Bot 日志 "channel=REGISTER"）；{@code minecraft:register} 是 1.13+ 的命名。若用错名字，
     * 1.12.2 会静默忽略客户端的注册，导致 Bot 不被加入监听集合——BungeeCord 的 {@code Forward}
     * 因“无玩家监听目标通道”被丢弃（跨服请求/发现全部失效，目标子服侧零 [CrossServer] 日志）。
     * 主通道此前靠服务端侧 {@code Player.addChannel} 反射兜底才工作，这里一并纠正为 legacy 名。
     */
    private static final String REGISTER_CHANNEL = "REGISTER";
    private static final String UNREGISTER_CHANNEL = "UNREGISTER";
    /** Bot 控制通道：Bot 经代理落在默认服后，向当前服务端请求“代发 BungeeCord Connect 切到本服”。 */
    public static final String CHANNEL_BOT_CTL = "httpproxy:botctl";

    private final JavaPlugin plugin;
    @Getter
    private final String username;
    @Getter
    private final String channel;
    @Getter
    private final String host;
    @Getter
    private final int port;
    /** 群组服转发兼容：true 时握手 host 附加 legacy IP 转发数据（host\0ip\0uuid），以通过后端 bungee:true / Velocity 转发校验 */
    private volatile boolean proxyForwarding = false;
    /** 群组服下本 Bot 所属的服务器名（于 ServerTag 中携带，便于“获取并与 Bot 通讯”） */
    private String serverName = "";
    /** 群组服跨服枢纽：true 时 Bot 经代理(BungeeCord/Velocity)连接，而非直连后端。
     *  原因：BungeeCord 的 Forward 插件消息只会对“经代理连接的玩家”做跨服中继；
     *  直连后端的 Bot 其 Forward 不会被代理投递，导致跨服请求/发现全部失效。 */
    private volatile boolean connectViaProxy = false;
    /** 代理监听地址（host:port），connectViaProxy 时用于 TCP 连接与明文握手（转发数据由代理自行附加） */
    private String proxyHost = null;
    private int proxyPort = 0;

    private Client client;
    private Session session;
    private RawMessageListener listener;
    /** Bot 发包/心跳线程（daemon：不阻止 JVM/服务器卸载退出） */
    private ScheduledExecutorService sender = newSender();
    /** 关闭标志：onDisable 后不再自动重连（避免僵尸重连） */
    private volatile boolean closed = false;
    /** 周期重注册任务句柄（避免重复调度） */
    private ScheduledFuture<?> reRegTask = null;
    /** 额外需登记的监听通道（群组服跨服 fwd-req/fwd-resp/discovery），供服务端侧把对应通道消息投递给本 Bot。 */
    private final Set<String> extraChannels = new LinkedHashSet<>();
    /** 自动重连次数上限（0=不自动重连；默认 3）。达到上限仍未连上/被踢则放弃，不再调度重连。 */
    private volatile int maxReconnectAttempts = 3;
    /** 已连续失败（连接失败 / 断开 / 被踢）次数 */
    private volatile int reconnectAttempts = 0;

    private volatile boolean registered = false;
    private volatile boolean inGame = false;

    /** daemon 单线程调度器（发包串行 + 周期重注册）。 */
    private static ScheduledExecutorService newSender() {
        return Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "HTTP-Over-MC-Bot-Sender");
            t.setDaemon(true);
            return t;
        });
    }

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

    /** 设置群组服转发兼容标志（后端 bungee:true / Velocity 转发时置 true，使握手附加 !<uuid>）。 */
    public void setProxyForwarding(boolean proxyForwarding) {
        this.proxyForwarding = proxyForwarding;
    }

    /** 设置本 Bot 所属服务器名（群组服发现标签用）。 */
    public void setServerName(String serverName) {
        this.serverName = serverName == null ? "" : serverName;
    }

    /** 设置 proxy 模式：经代理连接（host:port），并自动在进 GAME 后发 Connect 切换到本服。 */
    public void setProxyAddress(String addr) {
        if (addr == null || addr.trim().isEmpty()) {
            this.connectViaProxy = false;
            this.proxyHost = null;
            this.proxyPort = 0;
            return;
        }
        int idx = addr.lastIndexOf(':');
        if (idx < 0) {
            LogKit.warn("[HTTP-Over-MC] proxy-address 格式错误（应为 host:port）: " + addr);
            return;
        }
        try {
            this.proxyHost = addr.substring(0, idx).trim();
            this.proxyPort = Integer.parseInt(addr.substring(idx + 1).trim());
            this.connectViaProxy = this.proxyHost.length() > 0 && this.proxyPort > 0;
        } catch (NumberFormatException e) {
            LogKit.warn("[HTTP-Over-MC] proxy-address 端口解析失败: " + addr);
        }
    }

    /** 隧道是否就绪：Bot 已连接且已 REGISTER 自定义通道 */
    public boolean isReady() {
        return registered && session != null && session.isConnected();
    }

    public void connect() {
        MinecraftProtocol protocol = new MinecraftProtocol(getUsername());
        // 握手 host：群组服转发模式（后端 bungee:true / Velocity legacy 转发）下附加转发数据，
        // 格式与 BungeeCord ServerConnector 一致：host + "\0" + ip + "\0" + uuid（3 段，NULL 字节 \0 分隔）。
        // Spigot 1.12.2 在 bungee:true 时按 \0 切分握手 host，要求恰好 3~4 段，否则直接断开 Bot。
        // 注意：TCP 仍连 getHost()（实际后端地址），\0 后的 ip/uuid 仅出现在握手包中（packetlib 的
        // TcpClientSession 已本地魔改：host 含 \0 时 TCP 用 \0 之前部分、握手用完整 host）。
        String handshakeHost = getHost();
        int connectPort = getPort();
        String connectHost = getHost();
        if (connectViaProxy && proxyHost != null && proxyPort > 0) {
            // 经代理连接：TCP 连代理监听地址，握手用明文 host（转发数据由代理在连后端时自行附加）。
            // 这样 Bot 成为“经代理连接的玩家”，其 BungeeCord 频道 Forward 才能被代理正确跨服中继。
            connectHost = proxyHost;
            connectPort = proxyPort;
            handshakeHost = proxyHost;
            LogKit.info("[HTTP-Over-MC] Bot 经代理连接 " + connectHost + ":" + connectPort + " user=" + getUsername());
        } else {
            if (proxyForwarding) {
                // Bot 经回环直连后端，其真实 socket IP 即 127.0.0.1；uuid 用 Bot 名的离线 UUID（与 Spigot 离线模式一致）
                handshakeHost = getHost() + "\u0000" + "127.0.0.1" + "\u0000" + offlinePlayerUuid(getUsername());
                LogKit.info("[HTTP-Over-MC] 群组服转发模式：Bot 握手附加转发数据 -> "
                        + handshakeHost.replace("\u0000", "<NUL>"));
            }
            LogKit.info("[HTTP-Over-MC] Bot 正在连接 " + connectHost + ":" + connectPort + " user=" + getUsername());
        }
        client = new Client(handshakeHost, connectPort, protocol, new TcpSessionFactory());
        session = client.getSession();
        session.addListener(new BotSessionListener());
        try {
            session.connect();
        } catch (Throwable t) {
            LogKit.warn("[HTTP-Over-MC] Bot 连接失败，稍后自动重连: " + t);
            scheduleReconnect(60L);
        }
    }

    /** 离线玩家 UUID（与 Spigot 离线模式生成规则一致：nameUUIDFromBytes("OfflinePlayer:"+name)）。 */
    private static String offlinePlayerUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)).toString();
    }

    public void disconnect() {
        closed = true;
        registered = false;
        inGame = false;
        if (reRegTask != null) {
            try { reRegTask.cancel(true); } catch (Throwable ignored) {}
            reRegTask = null;
        }
        sender.shutdownNow();
        if (session != null && session.isConnected()) {
            session.disconnect("HTTP-Over-MC shutting down");
        }
    }

    /**
     * 重新连接（被踢出游戏等特殊情况后恢复隧道）：不断销毁 sender 线程池（便于立即重连），
     * 仅断开旧会话并在其已关闭时重建，再发起一次 connect()；主通道与 McLink 引用保持不变。
     */
    public void reconnect() {
        LogKit.info("[HTTP-Over-MC] Bot 重新连接 user=" + getUsername());
        registered = false;
        inGame = false;
        if (session != null && session.isConnected()) {
            try {
                session.disconnect("HTTP-Over-MC reconnect");
            } catch (Throwable ignored) {
            }
        }
        if (sender.isShutdown() || sender.isTerminated()) {
            sender = newSender();
        }
        reconnectAttempts = 0; // 人工 /soyshttp reconnect 视为新周期
        connect();
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
            s.send(new ClientPluginMessagePacket(REGISTER_CHANNEL, getChannel().getBytes(StandardCharsets.UTF_8)));
            LogKit.info("[HTTP-Over-MC] Bot 已发送通道注册 " + getChannel());
            if (connectViaProxy && proxyHost != null && proxyPort > 0 && serverName != null && !serverName.isEmpty()) {
                try {
                    // 注册 BungeeCord 通道（供服务端侧代发 Connect / 本服 Forward 透传）。
                    s.send(new ClientPluginMessagePacket(REGISTER_CHANNEL, "BungeeCord".getBytes(StandardCharsets.UTF_8)));
                    // 注册 botctl 控制通道（用于向当前所在服请求“代发 BungeeCord Connect 切到本服”）。
                    s.send(new ClientPluginMessagePacket(REGISTER_CHANNEL, CHANNEL_BOT_CTL.getBytes(StandardCharsets.UTF_8)));
                    // 经代理的 Bot 默认落在代理默认子服；而 BungeeCord 1.x 会丢弃客户端直发的
                    // BungeeCord 通道 Connect，因此改为向“当前服务端”发 botctl 控制消息，由服务端侧
                    // player.sendPluginMessage("BungeeCord", Connect) 可靠地切换到本服（详见 HttpOverMcPlugin）。
                    ByteArrayOutputStream cb = new ByteArrayOutputStream();
                    DataOutputStream cout = new DataOutputStream(cb);
                    cout.writeUTF(serverName);
                    cout.flush();
                    s.send(new ClientPluginMessagePacket(CHANNEL_BOT_CTL, cb.toByteArray()));
                    LogKit.info("[HTTP-Over-MC] Bot 已请求服务端代发 Connect 切到本服: " + serverName);
                } catch (Throwable t) {
                    LogKit.warn("[HTTP-Over-MC] Bot 发送 botctl 失败: " + t);
                }
            }
            // 登记跨服枢纽所需的额外监听通道（fwd-req/fwd-resp/discovery）。
            // 关键：Spigot 1.12.2 仅当“玩家(Bot)正监听该通道”时才会把入站 PluginMessage 投递给
            // 服务端侧 PluginMessageListener；若 Bot 未监听 fwd-req，BungeeCord 的 Forward 会被静默丢弃，
            // 导致跨服请求/响应/发现全部失效（目标子服侧零 [CrossServer] 日志）。
            synchronized (extraChannels) {
                if (!extraChannels.isEmpty()) {
                    for (String ec : extraChannels) {
                        try {
                            s.send(new ClientPluginMessagePacket(REGISTER_CHANNEL, ec.getBytes(StandardCharsets.UTF_8)));
                        } catch (Throwable ignored) {
                        }
                    }
                    LogKit.info("[HTTP-Over-MC] Bot 已注册跨服通道(共 " + extraChannels.size() + " 个): " + extraChannels);
                }
            }
            // 周期补注册主通道（代理模式下经 Connect 切换后端会重置通道，需周期性补注册；
            // 即便不切换，周期补注册也对后端重启/重连更稳健；minecraft:register 幂等无副作用）。
            // 仅在群组服相关模式（经代理或转发兼容）下启用，独立服行为保持不变。
            if (connectViaProxy || proxyForwarding) {
                try {
                    if (reRegTask == null || reRegTask.isCancelled() || reRegTask.isDone()) {
                        reRegTask = sender.scheduleWithFixedDelay(this::reRegisterChannels, 3, 3, TimeUnit.SECONDS);
                    }
                } catch (Throwable ignored) {
                }
            }
        });
    }

    /** 设置自动重连次数上限（0=禁用自动重连；达到上限仍未连上/被踢则放弃）。 */
    public void setMaxReconnectAttempts(int maxAttempts) {
        this.maxReconnectAttempts = Math.max(0, maxAttempts);
    }

    /** 延迟自动重连（网络抖动 / 代理未就绪 / 被踢），保证隧道自愈；插件关闭后不再重连。
     *  连续失败（连不上或被踢）累计超过 {@code bot.reconnect-attempts} 则放弃，等待人工 /soyshttp reconnect。 */
    private void scheduleReconnect(long delayTicks) {
        if (closed) return;
        if (maxReconnectAttempts > 0 && ++reconnectAttempts > maxReconnectAttempts) {
            LogKit.warn("[HTTP-Over-MC] Bot 自动重连已达上限(" + maxReconnectAttempts + " 次)，放弃重连 user="
                    + getUsername() + "（可执行 /soyshttp reconnect 手动恢复）");
            return;
        }
        LogKit.info("[HTTP-Over-MC] Bot 将在 " + delayTicks + " tick 后重连（第 " + reconnectAttempts
                + "/" + (maxReconnectAttempts <= 0 ? "∞" : String.valueOf(maxReconnectAttempts)) + " 次）");
        try {
            plugin.getServer().getScheduler().runTaskLater(plugin, this::reconnect, delayTicks);
        } catch (Throwable t) {
            // 插件已禁用或调度器不可用：放弃重连
        }
    }

    /** 周期补注册主通道（及代理模式下的 BungeeCord 通道），覆盖后端切换/重启后通道丢失。 */
    private void reRegisterChannels() {
        Session s = session;
        if (s == null || !s.isConnected()) {
            return;
        }
        try {
            s.send(new ClientPluginMessagePacket(REGISTER_CHANNEL, getChannel().getBytes(StandardCharsets.UTF_8)));
            if (connectViaProxy && proxyHost != null && proxyPort > 0) {
                s.send(new ClientPluginMessagePacket(REGISTER_CHANNEL, "BungeeCord".getBytes(StandardCharsets.UTF_8)));
                s.send(new ClientPluginMessagePacket(REGISTER_CHANNEL, CHANNEL_BOT_CTL.getBytes(StandardCharsets.UTF_8)));
            }
            synchronized (extraChannels) {
                for (String ec : extraChannels) {
                    s.send(new ClientPluginMessagePacket(REGISTER_CHANNEL, ec.getBytes(StandardCharsets.UTF_8)));
                }
            }
        } catch (Throwable t) {
            LogKit.warn("[HTTP-Over-MC] Bot 周期重注册通道失败: " + t);
        }
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

    /**
     * 登记额外监听通道（非阻塞，供跨服枢纽调用）。仅存储通道名，待 Bot 进入 GAME 后由
     * {@link #registerChannel()} 统一 {@code minecraft:register}；若 Bot 已就绪则立即补注册，
     * 覆盖 {@code /soyshttp reload} 等 Bot 复用的场景。重复登记幂等。
     */
    public void addExtraChannel(String ch) {
        if (ch == null || ch.isEmpty()) {
            return;
        }
        boolean changed;
        synchronized (extraChannels) {
            changed = extraChannels.add(ch);
        }
        if (changed && registered && session != null && session.isConnected()) {
            final Session s = session;
            sender.submit(() -> {
                try {
                    s.send(new ClientPluginMessagePacket(REGISTER_CHANNEL, ch.getBytes(StandardCharsets.UTF_8)));
                    LogKit.info("[HTTP-Over-MC] Bot 已补注册额外通道 " + ch);
                } catch (Throwable ignored) {
                }
            });
        }
    }

    /** 注销额外 PluginMessage 通道（发送 minecraft:unregister）。 */
    public void unregisterExtraChannel(String ch) {
        if (ch == null || ch.isEmpty() || session == null || !session.isConnected()) {
            return;
        }
        final Session s = session;
        sender.submit(() -> s.send(new ClientPluginMessagePacket(UNREGISTER_CHANNEL, ch.getBytes(StandardCharsets.UTF_8))));
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
            // 自动重连（非主动关闭）：覆盖代理未就绪 / 网络抖动 / 被踢，保证隧道自愈
            scheduleReconnect(60L);
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
                    // 仅主通道帧交由隧道分发(BotManager→McLink)；fwd-req/fwd-resp/botctl/BungeeCord 等
                    // 通道由服务端侧 PluginMessageListener 处理，不应回灌客户端侧 McLink。否则跨服响应
                    // (fwd-resp) 会被 Bot 与服务端双重处理，竞争同一 pending 表导致响应错乱/超时。
                    if (listener != null && getChannel().equals(sp.getChannel())) {
                        listener.onRawMessage(sp.getChannel(), sp.getData());
                    }
                }
            } catch (Throwable t) {
                LogKit.warn("[HTTP-Over-MC] Bot 处理数据包异常", t);
            }
        }
    }
}
