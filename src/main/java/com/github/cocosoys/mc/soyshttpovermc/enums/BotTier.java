package com.github.cocosoys.mc.soyshttpovermc.enums;

/**
 * 逻辑队列层级（单物理 Bot 隧道下的处理优先级）。
 * ADMIN 队列由请求调度器优先 drain，普通请求走 COMMON，避免 admin 请求被 common 洪峰饿死。
 */
public enum BotTier {
    COMMON(0),
    ADMIN(10);

    /** 数值越大优先级越高（admin > common） */
    public final int priority;

    BotTier(int priority) {
        this.priority = priority;
    }
}