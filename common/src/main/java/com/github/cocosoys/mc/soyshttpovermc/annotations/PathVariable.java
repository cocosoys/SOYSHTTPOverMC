package com.github.cocosoys.mc.soyshttpovermc.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 路径参数绑定注解（仿 Spring MVC 的 @PathVariable）：
 * 标注在方法参数上，从 URL 路径模板段 {name} 取值并做类型转换。
 * 需配合含 {name} 占位符的映射路径使用，如：
 * <pre>
 *   &#64;GetMapping("/system/user/{id}")
 *   public AjaxResult getUser(&#64;PathVariable(name = "id") Long id)
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface PathVariable {

    /**
     * 路径变量名（对应路径模板中的 {name}）
     */
    String name();

    /**
     * 是否必填；路径未匹配出该变量且必填时返回 400
     */
    boolean required() default true;
}