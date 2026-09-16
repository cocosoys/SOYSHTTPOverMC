package com.github.cocosoys.mc.soyshttpovermc.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记端点为「已废弃」：路由与访问照常，但在 API 清单 / 自动文档中标注废弃提示，
 * 提醒维护者该端点将被移除或替换。注意本注解与 JDK 自带
 * {@link java.lang.Deprecated} 无关——SOYS 需要 RUNTIME 保留以读取元数据，
 * 且可携带 since / reason 说明；请勿与 JDK 注解混淆（同名不同包）。
 *
 * <pre>{@code
 * @Deprecated(since = "1.4.0", reason = "改用 /api/v2/orders")
 * @GetMapping("/orders")
 * public ApiResponse orders() { ... }
 * }</pre>
 *
 * <p>方法级缺失时回退到类级注解。</p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Deprecated {

    /**
     * 自哪个版本起废弃（如 "1.4.0"）。
     */
    String since() default "";

    /**
     * 废弃原因 / 替代方案说明。
     */
    String reason() default "";
}
