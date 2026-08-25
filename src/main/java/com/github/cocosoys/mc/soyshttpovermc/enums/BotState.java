package com.github.cocosoys.mc.soyshttpovermc.enums;

/**
 * Bot 连接生命周期状态。
 *
 * <ul>
 *   <li>{@link #DISCONNECTED} —— 初始 / 已断开 / 重连重置：TCP 尚未建立或已退出，不在游戏内；</li>
 *   <li>{@link #CONNECTING}   —— TCP 已建立，正在登录，等待进入 GAME 子协议；</li>
 *   <li>{@link #IN_GAME}      —— 已进入 GAME，但自定义通道尚未登记就绪；</li>
 *   <li>{@link #READY}        —— 通道已 REGISTER，隧道就绪可通信；</li>
 *   <li>{@link #CLOSED}       —— 永久关闭（插件禁用后不再自动重连）。</li>
 * </ul>
 */
public enum BotState {

    DISCONNECTED,
    CONNECTING,
    IN_GAME,
    READY,
    CLOSED;

    /** 是否已进入 GAME 子协议（通道登记就绪前 {@link #IN_GAME}，就绪后 {@link #READY}）。 */
    public boolean isInGame() {
        return this == IN_GAME || this == READY;
    }
}