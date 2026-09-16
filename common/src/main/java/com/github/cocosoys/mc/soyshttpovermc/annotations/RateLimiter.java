package com.github.cocosoys.mc.soyshttpovermc.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 端点级限流（仿若依 {@code @RateLimiter}）：在时间窗口内按 IP / 玩家 / 双键计数，
 * 超限请求返回 <b>429 Too Many Requests</b>。与网关全局 rate-limit 策略互补——
 * 本注解是细粒度端点级限制，全局策略是粗粒度保护层。
 *
 * <pre>{@code
 * @RateLimiter(count = 5, time = 10, by = RateLimiter.LimitBy.IP) // 同一 IP 10 秒最多 5 次
 * @PostMapping("/submit")
 * public ApiResponse submit(@RequestBody String body) { ... }
 * }</pre>
 *
 * <p>方法级缺失时回退到类级注解；客户端 IP 不可得（本地回环调用）时不限流。</p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimiter {

    /**
     * 时间窗口内最大请求数（默认 10）。
     */
    int count() default 10;

    /**
     * 时间窗口长度（秒，默认 60）。
     */
    int time() default 60;

    /**
     * 计数维度：按客户端 IP / 按玩家（需有效凭证解析出玩家名）/ 双键（IP + 玩家）。
     */
    LimitBy by() default LimitBy.IP;

    enum LimitBy {
        /** 按客户端 IP 计数（匿名与登录请求均适用） */
        IP,
        /** 按玩家名计数（需有效凭证；无凭证时回退 IP 计数） */
        PLAYER,
        /** IP + 玩家双键计数 */
        BOTH
    }
}
