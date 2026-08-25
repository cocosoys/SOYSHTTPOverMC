package com.github.cocosoys.mc.soyshttpovermc.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记端点为「公开」：在默认拒绝策略下，未声明 {@code @ApiPermission} 但标注 {@code @ApiPublic}
 * 的端点仍允许匿名访问；方法级缺失时回退到类级注解。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiPublic {
}
