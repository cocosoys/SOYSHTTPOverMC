package com.github.cocosoys.mc.soyshttpovermc.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 请求体绑定注解（仿 Spring 的 @RequestBody）：String 参数绑定原始请求体；其他实体参数由 {@link com.github.cocosoys.mc.soyshttpovermc.util.JsonReader} 反序列化绑定（JSON 键 -> setter）。
 * <pre>
 *   &#64;PostMapping("/echo")
 *   public AjaxResult echo(&#64;RequestBody String body)
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface RequestBody {
}
