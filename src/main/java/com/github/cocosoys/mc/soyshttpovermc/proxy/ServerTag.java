package com.github.cocosoys.mc.soyshttpovermc.proxy;

import lombok.Data;

/**
 * 群组服中一台子服务器的标识标签。
 * 当探测到位于群组服之后，每个子服都会携带本标签（服务器名称 / host / port），
 * 便于网关或其他插件经 {@link ServerRegistry} 定位某服以转发/回程。
 */
@Data
public class ServerTag {

    /** BungeeCord / Velocity 中的服务器名（config.yml 的 proxy.server-name，群组服内唯一） */
    private String serverName;
    /** 该子服对外可达 host（同 getMcHost） */
    private String host;
    /** 该子服对外可达 port（同 getMcPort） */
    private int port;
    /** 是否在线（最近一次 discovery 心跳在有效期内） */
    private boolean online;
    /** 最近一次发现时间戳（毫秒），用于过期清理 */
    private long lastSeen = System.currentTimeMillis();

    public ServerTag() {
    }

    public ServerTag(String serverName, String host, int port) {
        this.serverName = serverName;
        this.host = host;
        this.port = port;
        this.online = true;
        this.lastSeen = System.currentTimeMillis();
    }

    /** 序列化为 NUL 分隔串（用于 discovery 广播载荷；各字段不含 NUL，安全） */
    public String encode() {
        return serverName + "\u0000" + host + "\u0000" + port;
    }

    /** 反序列化 NUL 分隔串 */
    public static ServerTag decode(String s) {
        if (s == null) return null;
        String[] p = s.split("\u0000", -1);
        if (p.length < 3) return null;
        try {
            return new ServerTag(p[0], p[1], Integer.parseInt(p[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
