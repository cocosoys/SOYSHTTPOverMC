package com.github.cocosoys.mc.soyshttpovermc.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记端点为「匿名」：无需任何凭证即可访问（认证门 401 放行），同时拥有 {@link ApiPublic}
 * 的免权限效果（授权门 403 放行、显式 {@link ApiPermission} 不生效）。
 *
 * <p>与 {@link ApiPublic} 的区别：{@code @ApiPublic} 仅免权限、<b>仍需登录凭证</b>
 * （无凭证仍被认证门 401 拒绝，适合 logout/me 等依赖凭证参数的端点）；
 * {@code @Anonymous} 完全匿名，适合验证码/登录/探活等必须在无凭证下可达的端点。</p>
 *
 * <p>方法级缺失时回退到类级注解；与 {@link ApiPermission} 共存时 {@code @Anonymous} 优先（放行）。</p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Anonymous {
}
