package com.github.cocosoys.mc.soyshttpovermc.annotations;
import com.github.cocosoys.mc.soyshttpovermc.enums.RequestMethod;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** PATCH 映射（仿 Spring MVC 的 @PatchMapping）：等价于 @RequestMapping(method = RequestMethod.PATCH)。 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@RequestMapping(method = RequestMethod.PATCH)
public @interface PatchMapping {

    /** 路径别名 */
    String value() default "";

    /** 路由路径（与 value 二选一） */
    String path() default "";
}
