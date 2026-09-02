package com.github.cocosoys.mc.soyshttpovermc.annotations;

import com.github.cocosoys.mc.soyshttpovermc.enums.RequestMethod;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * GET 映射（仿 Spring MVC 的 @GetMapping）：等价于
 * &#64;RequestMapping(path = value, method = RequestMethod.GET)。
 * <pre>
 *   &#64;GetMapping("/status")
 *   public AjaxResult status() { ... }   // auth 开启时实际路由为 /api/status
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@RequestMapping(method = RequestMethod.GET)
public @interface GetMapping {

    /**
     * 路径别名
     */
    String value() default "";

    /**
     * 路由路径（与 value 二选一）
     */
    String path() default "";
}
