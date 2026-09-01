package com.github.cocosoys.mc.soyshttpovermc.enums;

import lombok.Getter;

/**
 * 同端口嗅探连接的类型状态：一条连接的分类结果（首包判定）与当前所处通道。
 *
 * <ul>
 *   <li>{@link #UNKNOWN}    —— 尚未分类（首包不足以判定，等待更多数据 / keep-alive 复用重置）；</li>
 *   <li>{@link #HTTP_PLAIN} —— 明文 HTTP，走网关策略链；</li>
 *   <li>{@link #HTTP_TLS}   —— 就地升级为 TLS（SslHandler 解密后按明文 HTTP 处理）；</li>
 *   <li>{@link #MC}         —— Minecraft 协议，原样放行给 Spigot 的 MC 解码器。</li>
 * </ul>
 */
@Getter
public enum SnifferChannelState {
    UNKNOWN(0),
    HTTP_PLAIN(1),
    HTTP_TLS(2),
    MC(3);

    public final int value;

    SnifferChannelState(int value) {
        this.value = value;
    }

    /**
     * 是否为 HTTP 处理路径（明文或 TLS 解密后）。
     */
    public boolean isHttp() {
        return this == HTTP_PLAIN || this == HTTP_TLS;
    }

    /**
     * 是否为 TLS 就地升级连接（写响应时需经 SslHandler 出口）。
     */
    public boolean isTls() {
        return this == HTTP_TLS;
    }
}