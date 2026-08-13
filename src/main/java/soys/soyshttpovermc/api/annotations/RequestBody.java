package soys.soyshttpovermc.api.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 请求体绑定注解（仿 Spring 的 @RequestBody）：标注在 String 参数上，绑定原始请求体。
 * <pre>
 *   &#64;PostMapping("/echo")
 *   public AjaxResult echo(&#64;RequestBody String body)
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface RequestBody {
}
