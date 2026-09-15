package com.github.cocosoys.mc.soyshttpovermc.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记端点为「公开」：在默认拒绝策略下，未声明 {@code @ApiPermission} 但标注 {@code @ApiPublic}
 * 的端点免权限判定（授权门 403 放行）；<b>但仍需登录凭证</b>（认证门 401 照常，无凭证请求被拒）。
 * 适合 logout/me 等依赖凭证参数的端点。方法级缺失时回退到类级注解。
 *
 * <p>如需<b>完全匿名</b>（认证门也放行），请使用 {@link Anonymous}；二者同时存在时以
 * {@code @Anonymous} 语义为准。</p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiPublic {
}
