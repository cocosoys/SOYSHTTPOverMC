package soys.soyshttpovermc.cross;

import soys.soyshttpovermc.link.McLink;
import soys.soyshttpovermc.log.LogKit;
import soys.soyshttpovermc.proto.FrameProto;
import soys.soyshttpovermc.proxy.ServerRegistry;
import soys.soyshttpovermc.proxy.ServerTag;
import soys.soyshttpovermc.util.HttpFrames;
import soys.soyshttpovermc.web.WebFrontendHandler;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import com.google.protobuf.ByteString;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 群组服跨服枢纽（拓扑2：网关后端模式）。
 * <p>每台子服都运行本枢纽，职责统一：
 * <ul>
 *   <li><b>请求中继</b>：网关收到指向其他服的请求时，逐分片经 BungeeCord {@code Forward} 发到目标服的
 *       {@code httpproxy:fwd-req} 通道（{@link #relayRequestFragment}）；</li>
 *   <li><b>请求服务</b>：目标服在 {@code httpproxy:fwd-req} 收到转发请求，重组后走本服 {@code web.handle}，
 *       响应再经 {@code httpproxy:fwd-resp} 原路发回源服（源服名取自请求头 {@code X-Soys-Source-Server}）；</li>
 *   <li><b>响应关联</b>：源服在 {@code httpproxy:fwd-resp} 收到响应分片，重组后喂给 {@link McLink}
 *       （按 request_id 完成 await 的 future），从而 {@code HttpMcTranslator.translate} / 跨服 API 调用得到结果；</li>
 *   <li><b>发现</b>：经 {@code httpproxy:discovery} 广播/收集各服 {@link ServerTag}，供“获取并与 Bot 通讯”。</li>
 * </ul>
 * 仅网关（sniffer 开启的服）会真正把外部 HTTP 收进来并触发中继；其余子服仅作为“被调用方”存在，
 * 但任一服都可直接经 {@code CrossServerHttpClient} 调另一服（本枢纽统一转发）。
 */
public class CrossServerHub {

    /** BungeeCord 插件消息主通道（用于发送 Forward 指令） */
    public static final String BUNGEECORD_CHANNEL = "BungeeCord";
    /** 跨服请求转发通道（网关→目标，目标在此服务） */
    public static final String CHANNEL_FWD_REQ = "httpproxy:fwd-req";
    /** 跨服响应回程通道（目标→源，源在此完成 McLink future） */
    public static final String CHANNEL_FWD_RESP = "httpproxy:fwd-resp";
    /** 服务器标签发现广播通道 */
    public static final String CHANNEL_DISCOVERY = "httpproxy:discovery";

    /** 路由/关联内部头（均经 headers 承载，避免改动 FrameProto） */
    public static final String HEADER_TARGET = "X-Soys-Target-Server";
    public static final String HEADER_SOURCE = "X-Soys-Source-Server";
    public static final String HEADER_TRACE = "X-Soys-Trace-Id";
    /** 内部队列标记头（同 HttpMcTranslator，转发前剥离避免泄漏） */
    public static final String HEADER_TIER = "X-SOYS-TIER";

    private static final int MAX_PAYLOAD = 32000;

    private final JavaPlugin plugin;
    private final McLink mcLink;
    private final WebFrontendHandler web;
    private final String botName;
    private final ServerRegistry registry;
    private String localServerName = "";

    /** 入站请求分片重组表（fwd-req 通道）：request_id -> 重组状态 */
    private final Map<Long, Frag> reqFrags = new ConcurrentHashMap<>();
    public CrossServerHub(JavaPlugin plugin, McLink mcLink, WebFrontendHandler web, String botName, ServerRegistry registry) {
        this.plugin = plugin;
        this.mcLink = mcLink;
        this.web = web;
        this.botName = botName;
        this.registry = registry;
    }

    public void setLocalServerName(String name) {
        this.localServerName = name == null ? "" : name;
    }

    public String getLocalServerName() {
        return localServerName;
    }

    /** 判断某个请求分片是否指向“其他服”（需中继） */
    public boolean isCrossServer(FrameProto.HttpRequestFrame chunk) {
        String target = chunk.getHeadersMap().get(HEADER_TARGET);
        return target != null && !target.isEmpty() && !target.equals(localServerName);
    }

    /** 网关侧：把收到的请求分片原样经 BungeeCord Forward 发到目标服（目标会重组后服务）。 */
    public void relayRequestFragment(byte[] chunkBytes, String target) {
        LogKit.info("[CrossServer] 网关侧 relayRequestFragment -> target=" + target
                + " len=" + (chunkBytes == null ? -1 : chunkBytes.length)
                + " bot=" + botName + " online=" + (Bukkit.getPlayerExact(botName) != null));
        sendBungeeForward(target, CHANNEL_FWD_REQ, chunkBytes);
    }

    // ============================ BungeeCord Forward 封装 ============================

    /** 经本服 Bot 玩家向 BungeeCord 发送 Forward 指令（target=服务器名 或 "ALL"）。需在<b>主线程</b>调用。 */
    private void sendBungeeForward(String target, String channel, byte[] data) {
        if (data == null) return;
        Player bot = Bukkit.getPlayerExact(botName);
        if (bot == null) {
            LogKit.warn("[CrossServer] 无法转发到 " + target + "：Bot(" + botName + ") 未在线");
            return;
        }
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bos);
            out.writeUTF("Forward");
            out.writeUTF(target);
            out.writeUTF(channel);
            out.writeShort(data.length);
            out.write(data);
            out.flush();
            final byte[] pkt = bos.toByteArray();
            // sendPluginMessage 必须在主线程
            Bukkit.getScheduler().runTask(plugin, () -> bot.sendPluginMessage(plugin, BUNGEECORD_CHANNEL, pkt));
            LogKit.info("[CrossServer] 已发送 Forward target=" + target + " ch=" + channel + " via bot=" + botName
                    + " len=" + data.length);
        } catch (Throwable t) {
            LogKit.warn("[CrossServer] 发送 Forward 失败 target=" + target + " ch=" + channel + ": " + t);
        }
    }

    // ============================ 入站监听（服务端侧 PluginMessageListener） ============================

    /**
     * 统一监听器：注册在服务端 {@code BungeeCord} 通道（而非 httpproxy:fwd-*）。
     * 关键机制：BungeeCord 收到 {@code Forward} 指令后，会把内层载荷重新包装为
     * {@code 内层channel}(writeUTF) + len(writeShort) + data，并经由目标服的
     * {@code BungeeCord} 通道投递；Spigot(bungeecord:true) 不会自动解 Forward，
     * 仅把这条 BungeeCord 通道消息透传给已注册的 listener。因此本监听器必须读
     * innerChannel+len+data，再按内层 channel 路由到对应的处理逻辑。
     * （早期版本错误地在 httpproxy:fwd-* 上注册 listener，而 BungeeCord 从不向
     * 这些通道直投，导致跨服请求/响应/发现被全部静默丢弃。）
     */
    public PluginMessageListener listener() {
        return new PluginMessageListener() {
            @Override
            public void onPluginMessageReceived(String ch, Player player, byte[] message) {
                if (!BUNGEECORD_CHANNEL.equals(ch)) return;
                if (message == null || message.length < 3) return;
                try {
                    DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));
                    String inner = in.readUTF();
                    short len = in.readShort();
                    byte[] data = new byte[len < 0 ? 0 : len];
                    if (len > 0) in.readFully(data);
                    LogKit.info("[CrossServer] 收到 BungeeCord 跨服转发 inner=" + inner
                            + " player=" + (player == null ? "null" : player.getName())
                            + " len=" + len);
                    if (CHANNEL_FWD_REQ.equals(inner)) {
                        onForwardRequest(data);
                    } else if (CHANNEL_FWD_RESP.equals(inner)) {
                        onForwardResponse(data);
                    } else if (CHANNEL_DISCOVERY.equals(inner)) {
                        onDiscovery(data);
                    }
                } catch (Throwable t) {
                    LogKit.warn("[CrossServer] BungeeCord 跨服载荷解析失败: " + t);
                }
            }
        };
    }

    /** 目标服：收到转发请求分片 → 重组 → 本服服务 → 响应回程 */
    private void onForwardRequest(byte[] chunkBytes) {
        FrameProto.HttpRequestFrame chunk;
        try {
            chunk = FrameProto.HttpRequestFrame.parseFrom(chunkBytes);
        } catch (Exception e) {
            return;
        }
        long id = chunk.getRequestId();
        int total = chunk.getTotalFragments() < 1 ? 1 : chunk.getTotalFragments();
        Frag f = reqFrags.computeIfAbsent(id, k -> new Frag(total));
        int idx = Math.min(chunk.getFragmentIndex(), total - 1);
        f.buffers[idx] = chunk.getBody().toByteArray();
        if (++f.received < total) return; // 等待更多分片
        reqFrags.remove(id);
        byte[] full;
        try {
            full = join(f.buffers);
            chunk = FrameProto.HttpRequestFrame.parseFrom(full);
        } catch (Exception e) {
            LogKit.warn("[CrossServer] 转发请求重组失败 id=" + id);
            return;
        }
        String trace = chunk.getHeadersMap().get(HEADER_TRACE);
        LogKit.info("[CrossServer] 收到跨服请求 id=" + id + " trace=" + trace
                + " " + chunk.getMethod() + " " + chunk.getPath() + " -> 本服服务");
        // 服务必须在异步线程（web.handle 可能触及 Bukkit），完成后回程响应（主线程 sendPluginMessage）
        final FrameProto.HttpRequestFrame req = chunk;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> serveAndRespond(req, trace));
    }

    private void serveAndRespond(FrameProto.HttpRequestFrame req, String trace) {
        try {
            String source = req.getHeadersMap().get(HEADER_SOURCE);
            // 剥离路由指令头(HEADER_TARGET)与队列标记(HEADER_TIER)，避免重复路由/泄漏；
            // 保留 HEADER_SOURCE / HEADER_TRACE：供业务(ApiRegistry)关联来源服与链路追踪（与单服 McMessageHandler 一致）。
            Map<String, String> clean = new java.util.HashMap<>(req.getHeadersMap());
            clean.remove(HEADER_TARGET);
            clean.remove(HEADER_TIER);
            FrameProto.HttpResponseFrame resp = web.handle(req.getMethod(), req.getPath(), clean, req.getBody().toByteArray());
            if (resp == null) {
                resp = HttpFrames.jsonError(500, "internal error")
                        .toBuilder().setRequestId(req.getRequestId()).build();
            }
            resp = resp.toBuilder().setRequestId(req.getRequestId()).build();
            // 分片后逐片经 BungeeCord 回程到源服
            byte[] raw = resp.toByteArray();
            int n = (raw.length <= MAX_PAYLOAD) ? 1 : (raw.length + MAX_PAYLOAD - 1) / MAX_PAYLOAD;
            final String dst = source == null ? localServerName : source;
            final long rid = req.getRequestId();
            for (int i = 0; i < n; i++) {
                int s = i * MAX_PAYLOAD, e = Math.min(raw.length, s + MAX_PAYLOAD);
                FrameProto.HttpResponseFrame c = resp.toBuilder().clearBody()
                        .setBody(ByteString.copyFrom(Arrays.copyOfRange(raw, s, e)))
                        .setFragmentIndex(i).setTotalFragments(n).build();
                sendBungeeForward(dst, CHANNEL_FWD_RESP, c.toByteArray());
            }
            LogKit.info("[CrossServer] 已回程响应 id=" + rid + " trace=" + trace + " frags=" + n + " -> " + dst);
        } catch (Throwable t) {
            LogKit.warn("[CrossServer] 跨服服务异常 id=" + req.getRequestId() + ": " + t, t);
        }
    }

    /** 源服：收到跨服响应分片 → 直接喂给 McLink（其按 request_id 重组并完成 pending future）。
     * <p>与单服回程契约完全一致：单服模式下 {@code RequestScheduler.writeResponse} 把响应分片经 Bot 回发，
     * 主 Bot 的 {@code RawMessageListener(dispatch)} 把<b>每个分片</b>直接交给 {@code mainLink.onRawMessage} 重组；
     * 跨服回程(fwd-resp)以同一 {@code mainLink} 为收件方，故每个分片原样交给它即可，<b>不可</b>在此先自行重组
     * （否则会让 McLink 再做一次重组而把应用层响应体误当 protobuf 解析，导致 future 永不完成→30s 超时 502）。 */
    private void onForwardResponse(byte[] chunkBytes) {
        // 仅做轻量日志（解析失败不影响回程，直接透传）
        try {
            FrameProto.HttpResponseFrame peek = FrameProto.HttpResponseFrame.parseFrom(chunkBytes);
            LogKit.info("[CrossServer] 收到跨服响应分片 id=" + peek.getRequestId()
                    + " trace=" + peek.getHeadersMap().get(HEADER_TRACE));
        } catch (Exception ignored) {
        }
        mcLink.onRawMessage(CHANNEL_FWD_RESP, chunkBytes);
    }

    /** 收到 discovery 广播 → 注册其他服标签 */
    private void onDiscovery(byte[] data) {
        String s = new String(data, StandardCharsets.UTF_8);
        ServerTag tag = ServerTag.decode(s);
        if (tag != null && !tag.getServerName().equals(localServerName)) {
            registry.register(tag);
            LogKit.info("[CrossServer] 发现子服标签: " + tag.getServerName() + " (" + tag.getHost() + ":" + tag.getPort()
                    + " bot=" + tag.getBotName() + ")");
        }
    }

    /** 广播本服标签（经 BungeeCord Forward ALL）；bot 未就绪时静默跳过。 */
    public void broadcastDiscovery(ServerTag self) {
        if (self == null) return;
        if (Bukkit.getPlayerExact(botName) == null) return;
        sendBungeeForward("ALL", CHANNEL_DISCOVERY, self.encode().getBytes(StandardCharsets.UTF_8));
    }

    // ============================ 分片重组辅助 ============================

    private static byte[] join(byte[][] parts) {
        int len = 0;
        for (byte[] p : parts) if (p != null) len += p.length;
        byte[] out = new byte[len];
        int off = 0;
        for (byte[] p : parts) if (p != null) {
            System.arraycopy(p, 0, out, off, p.length);
            off += p.length;
        }
        return out;
    }

    private static class Frag {
        final byte[][] buffers;
        int received = 0;
        Frag(int total) { this.buffers = new byte[total][]; }
    }

    /** 生成 trace id（网关侧请求入口调用） */
    public static String newTraceId() {
        return UUID.randomUUID().toString();
    }
}
