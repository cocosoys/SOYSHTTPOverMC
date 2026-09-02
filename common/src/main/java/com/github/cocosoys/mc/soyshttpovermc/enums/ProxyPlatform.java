package com.github.cocosoys.mc.soyshttpovermc.enums;

/**
 * 当前后端所在的运行拓扑。
 * <ul>
 *   <li>{@link #STANDALONE}：独立 Spigot / 普通服（无代理）</li>
 *   <li>{@link #BUNGEECORD}：位于 BungeeCord / Waterfall 之后</li>
 *   <li>{@link #VELOCITY}：位于 Velocity 之后</li>
 * </ul>
 */
public enum ProxyPlatform {
    STANDALONE,
    BUNGEECORD,
    VELOCITY
}