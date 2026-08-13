package soys.soyshttpovermc.api.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * API 权限注解（仿 Spring 的 @PreAuthorize/@RequiresPermissions 简化版）：
 * 声明调用该 API 所需的权限标识。
 * <pre>
 *   &#64;ApiPermission("system:user:list")
 *   &#64;GetMapping("/users")
 * </pre>
 * 由注册的 {@link PermissionService} 判定（把凭证映射为主体再查权限）；
 * 未注册 PermissionService 时该注解不阻断（仅记录），鉴权仍以 auth 策略的 401 为底线。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface ApiPermission {

    /** 权限标识，如 "system:user:list"；空串表示不设限 */
    String value() default "";
}
