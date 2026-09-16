package com.github.cocosoys.mc.soyshttpovermc.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 防重复提交（仿若依 {@code @RepeatSubmit}）：同一请求键（IP + 方法 + 路径 + 请求体哈希）
 * 在间隔内重复到达时返回 <b>409 Conflict</b>。适合支付/领奖/表单提交等必须幂等的写操作。
 *
 * <pre>{@code
 * @RepeatSubmit(interval = 5) // 同一键 5 秒内重复提交被拒
 * @PostMapping("/claim")
 * public ApiResponse claim(@RequestBody String body) { ... }
 * }</pre>
 *
 * <p>默认只对写方法生效（POST / PUT / DELETE / PATCH；GET 天然幂等不防重），
 * 设置 {@code force = true} 可强制对 GET 也生效。方法级缺失时回退到类级注解。</p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RepeatSubmit {

    /**
     * 防重间隔（秒，默认 10）：同一请求键在此间隔内的重复提交被拒绝。
     */
    int interval() default 10;

    /**
     * true = 对 GET 也生效（默认 false：仅写方法生效）。
     */
    boolean force() default false;
}
