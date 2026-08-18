package com.dlz.kit.util.system;

import com.dlz.kit.fn.DlzFn;

import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * dlz-kit 最小子集：字段反射工具。
 * <ul>
 *   <li>{@link #getFields}：递归收集类与父类字段（去 static/transient）；</li>
 *   <li>{@link #getFn}：DlzFn Lambda 反序列化 → {@link Fn#v2} 字段名（getXxx/isXxx → xxx）；</li>
 *   <li>{@link #getValue}/{@link #setValue}：字段读写（setAccessible）。</li>
 * </ul>
 */
public final class FieldReflections {

    private FieldReflections() {
    }

    /** getFn 返回值：v1 保留（原版携带源对象引用），v2=字段名。 */
    public static final class Fn {
        public final Object v1;
        public final String v2;

        Fn(Object v1, String v2) {
            this.v1 = v1;
            this.v2 = v2;
        }
    }

    /** 递归收集字段（含父类；去 static/transient，去 Object 基类）。 */
    public static List<Field> getFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Class<?> cur = clazz;
        while (cur != null && cur != Object.class) {
            for (Field f : cur.getDeclaredFields()) {
                int mod = f.getModifiers();
                if (Modifier.isStatic(mod) || Modifier.isTransient(mod)) {
                    continue;
                }
                fields.add(f);
            }
            cur = cur.getSuperclass();
        }
        return fields;
    }

    /** Lambda 反序列化提取字段名。 */
    public static Fn getFn(DlzFn<?, ?> fn) {
        if (fn == null) return new Fn(null, null);
        try {
            Method writeReplace = fn.getClass().getDeclaredMethod("writeReplace");
            writeReplace.setAccessible(true);
            Object replaced = writeReplace.invoke(fn);
            if (replaced instanceof SerializedLambda) {
                String method = ((SerializedLambda) replaced).getImplMethodName();
                String field = methodToField(method);
                if (field != null) {
                    return new Fn(null, field);
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return new Fn(null, null);
    }

    private static String methodToField(String method) {
        if (method == null || method.isEmpty()) return null;
        String name = method;
        if (name.startsWith("get") && name.length() > 3) {
            name = name.substring(3);
        } else if (name.startsWith("is") && name.length() > 2) {
            name = name.substring(2);
        } else {
            return null;
        }
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    public static Object getValue(Object bean, Field field) {
        if (bean == null || field == null) return null;
        try {
            field.setAccessible(true);
            return field.get(bean);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    public static void setValue(Object bean, Field field, Object value) {
        if (bean == null || field == null) return;
        try {
            field.setAccessible(true);
            field.set(bean, value);
        } catch (IllegalAccessException ignored) {
        }
    }
}
