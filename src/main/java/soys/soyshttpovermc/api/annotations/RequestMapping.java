package soys.soyshttpovermc.api.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 通用映射注解（仿 Spring MVC 的 @RequestMapping）：
 * <ul>
 *   <li><b>标注在方法上</b>：把方法注册为网关 API 路由，可指定多个 HTTP 方法与路径；</li>
 *   <li><b>标注在类上</b>：为其下声明的所有 API 方法统一加路径前缀（位于全局 api-prefix 之后）。
 *       例如类 &#64;RequestMapping("/admin") + 方法 &#64;GetMapping("/users") → 实际路由 /api/admin/users。</li>
 * </ul>
 * <pre>
 *   &#64;RequestMapping(path = "/admin")            // 类级前缀
 *   public class AdminApi {
 *       &#64;GetMapping("/users")                   // → /api/admin/users
 *       public AjaxResult users() { ... }
 *   }
 * </pre>
 * 更常用的写法是组合注解 {@link GetMapping} / {@link PostMapping} / {@link PutMapping} /
 * {@link DeleteMapping} / {@link PatchMapping}（仅方法级）。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
public @interface RequestMapping {

    /** 路径别名（与 path 二选一；都为空则注册为 /） */
    String value() default "";

    /** 路由路径，如 /users（不含全局前缀；auth 开启时自动加 /api 前缀） */
    String path() default "";

    /** 允许的 HTTP 方法；为空表示不限定（匹配任意方法） */
    RequestMethod[] method() default {};
}
