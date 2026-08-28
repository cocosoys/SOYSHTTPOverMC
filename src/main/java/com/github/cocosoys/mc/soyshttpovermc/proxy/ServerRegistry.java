package com.github.cocosoys.mc.soyshttpovermc.proxy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 群组服服务器标签注册表（内存）：serverName -> {@link ServerTag}。
 * <ul>
 *   <li>每台子服 onEnable 时<b>自注册</b>本服标签（保证单服即可验证）；</li>
 *   <li>经 {@code httpproxy:discovery} 通道的 BungeeCord Forward(ALL) 广播，各服会收集到<b>其他子服</b>的标签，
 *       形成全网联视图，按 serverName 即可取得目标服的 host/port 用于转发/回程。</li>
 * </ul>
 */
public class ServerRegistry {

    private final Map<String, ServerTag> tags = new ConcurrentHashMap<>();
    /** discovery 心跳有效期（毫秒）：超过未刷新的标签标记为离线 */
    private static final long TTL_MS = 90_000;

    public void register(ServerTag tag) {
        if (tag == null || tag.getServerName() == null) return;
        tag.setLastSeen(System.currentTimeMillis());
        tag.setOnline(true);
        tags.put(tag.getServerName(), tag);
    }

    public ServerTag get(String serverName) {
        return serverName == null ? null : tags.get(serverName);
    }

    public boolean contains(String serverName) {
        return serverName != null && tags.containsKey(serverName);
    }

    public List<ServerTag> all() {
        return new ArrayList<>(tags.values());
    }

    /** 过期清理：超过 TTL 未刷新的标签置离线（不删除，保留最后已知信息）。 */
    public void sweep() {
        long now = System.currentTimeMillis();
        for (ServerTag t : tags.values()) {
            if (now - t.getLastSeen() > TTL_MS) {
                t.setOnline(false);
            }
        }
    }
}
