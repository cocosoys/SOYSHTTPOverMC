package soys.soyshttpovermc.api.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** DELETE 映射（仿 Spring MVC 的 @DeleteMapping）：等价于 @RequestMapping(method = RequestMethod.DELETE)。 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@RequestMapping(method = RequestMethod.DELETE)
public @interface DeleteMapping {

    /** 路径别名 */
    String value() default "";

    /** 路由路径（与 value 二选一） */
    String path() default "";
}
