package soys.soyshttpovermc.api.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 通用映射注解（仿 Spring MVC 的 @RequestMapping）：
 * 标注在方法上，把方法注册为网关 API 路由，可指定多个 HTTP 方法与路径。
 * <pre>
 *   &#64;RequestMapping(path = "/users", method = {RequestMethod.GET, RequestMethod.POST})
 *   public AjaxResult users() { ... }
 * </pre>
 * 更常用的写法是组合注解 {@link GetMapping} / {@link PostMapping} / {@link PutMapping} /
 * {@link DeleteMapping} / {@link PatchMapping}。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
public @interface RequestMapping {

    /** 路径别名（与 path 二选一；都为空则注册为 /） */
    String value() default "";

    /** 路由路径，如 /users（不含全局前缀；auth 开启时自动加 /api 前缀） */
    String path() default "";

    /** 允许的 HTTP 方法；为空表示不限定（匹配任意方法） */
    RequestMethod[] method() default {};
}
