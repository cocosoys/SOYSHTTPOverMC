package com.github.cocosoys.mc.soyshttpovermc.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * API 命名注解（仿 Spring 的 @Operation/中文名）：给 API 一个人类可读的名字，
 * 用于接口文档、日志与错误提示。
 * <pre>
 *   &#64;ApiName("隧道状态")
 *   &#64;GetMapping("/status")
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface ApiName {

    /**
     * API 显示名
     */
    String value();
}
