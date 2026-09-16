package com.github.cocosoys.mc.soyshttpovermc.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 从 API 清单 / 自动文档中隐藏该端点（注册与路由照常生效，仅元数据层不可见）：
 * 适合内部调试端点、健康检查内部变体等不希望暴露给管理员的端点。
 *
 * <pre>{@code
 * @Hidden(reason = "内部调试端点，不对外展示")
 * @GetMapping("/debug/threads")
 * public AjaxResult threads() { ... }
 * }</pre>
 *
 * <p>方法级缺失时回退到类级注解。</p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Hidden {

    /**
     * 隐藏原因（写入端点元数据，便于维护者追溯）。
     */
    String reason() default "";
}
