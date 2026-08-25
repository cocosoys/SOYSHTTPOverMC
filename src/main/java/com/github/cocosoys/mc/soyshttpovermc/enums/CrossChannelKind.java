package com.github.cocosoys.mc.soyshttpovermc.enums;

/**
 * 跨服插件消息的内层通道类型（经 BungeeCord Forward 投递时写在载荷 writeUTF 的内层 channel）。
 *
 * <ul>
 *   <li>{@link #FWD_REQ}     —— 跨服请求转发（网关→目标，目标在此服务）；</li>
 *   <li>{@link #FWD_RESP}    —— 跨服响应回程（目标→源，源在此完成 McLink future）；</li>
 *   <li>{@link #DISCOVERY}   —— 服务器标签发现广播。</li>
 * </ul>
 */
public enum CrossChannelKind {

    FWD_REQ("httpproxy:fwd-req"),
    FWD_RESP("httpproxy:fwd-resp"),
    DISCOVERY("httpproxy:discovery");

    private final String channel;

    CrossChannelKind(String channel) {
        this.channel = channel;
    }

    /** 内层通道名字符串（写入 BungeeCord Forward 载荷 writeUTF）。 */
    public String channel() {
        return channel;
    }

    /** 按内层通道名解析；未匹配返回 null。 */
    public static CrossChannelKind fromName(String name) {
        if (name == null) return null;
        for (CrossChannelKind k : values()) {
            if (k.channel.equals(name)) return k;
        }
        return null;
    }
}