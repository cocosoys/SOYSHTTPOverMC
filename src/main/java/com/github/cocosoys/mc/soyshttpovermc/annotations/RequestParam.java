package com.github.cocosoys.mc.soyshttpovermc.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 请求参数绑定注解（仿 Spring MVC 的 @RequestParam）：
 * 标注在方法参数上，从 URL query string 取值并做类型转换。
 * <pre>
 *   &#64;GetMapping("/hello")
 *   public AjaxResult hello(&#64;RequestParam(name = "name", required = false, defaultValue = "world") String name)
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface RequestParam {

    /**
     * 参数名（query key）
     */
    String name();

    /**
     * 是否必填；缺失且必填时返回 400
     */
    boolean required() default false;

    /**
     * 默认值（缺失且非必填时使用）
     */
    String defaultValue() default "";
}
