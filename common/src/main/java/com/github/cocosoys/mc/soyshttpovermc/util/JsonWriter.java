package com.github.cocosoys.mc.soyshttpovermc.util;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.github.cocosoys.mc.soyshttpovermc.orm.convertor.BeanCodec;
import com.github.cocosoys.mc.soyshttpovermc.spring.entity.BaseEntity;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 零依赖的最小 JSON 序列化器（只消费 jackson-annotations 注解，不引入 Jackson 运行时）。
 * 支持：Map / Collection / 数组 / String / Number / Boolean / null / 枚举 / POJO（反射 getter）。
 * <p>实体类（继承 {@link BaseEntity}）可直接放入
 * {@link AjaxResult#success(Object)} 序列化输出。
 *
 * <p><b>注解支持（序列化侧）</b>：
 * <ul>
 *   <li>{@link JsonFormat}：Date 字段/方法按注解 pattern 输出（缺省回退 {@link BeanCodec#DATE_TIME_PATTERN}），
 *       现有实体上的 {@code @JsonFormat} 立即生效；</li>
 *   <li>{@link JsonProperty}：输出键重命名（方法注解优先，字段注解回退），适配前端/契约命名；</li>
 *   <li>{@link JsonIgnore}：跳过对应 getter/字段，防止 token、内部状态、计算字段泄漏；</li>
 *   <li>{@link JsonInclude}：{@code Include.NON_NULL} 时裁剪 null 输出（方法 &gt; 字段 &gt; 类级）；</li>
 *   <li>{@link JsonValue}：POJO / 枚举整体输出为单值（方法优先，字段回退），枚举无注解时输出 {@code name()}。</li>
 * </ul>
 */
public final class JsonWriter {

    private JsonWriter() {
    }

    /** 类 → 字段名 → 字段 的反射缓存（避免每次 getter 序列化重复 getDeclaredField）。 */
    private static final Map<Class<?>, Map<String, Field>> FIELD_CACHE = new ConcurrentHashMap<>();

    private static final Object NO_VALUE = new Object();

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        append(sb, value);
        return sb.toString();
    }

    private static void append(StringBuilder sb, Object v) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String) {
            appendString(sb, (String) v);
        } else if (v instanceof Number || v instanceof Boolean) {
            sb.append(v.toString());
        } else if (v instanceof Map) {
            appendMap(sb, (Map<?, ?>) v);
        } else if (v instanceof Collection) {
            appendCollection(sb, (Collection<?>) v);
        } else if (v.getClass().isArray()) {
            appendArray(sb, v);
        } else if (v instanceof CharSequence) {
            appendString(sb, v.toString());
        } else if (v instanceof Date) {
            appendString(sb, BeanCodec.formatDate((Date) v));
        } else if (v instanceof Enum) {
            // 枚举：@JsonValue 单值输出；否则 name()（避免 POJO 反射捞到 name()/ordinal()/values() 出脏对象）
            Object jv = jsonValueOf(v);
            append(sb, jv == NO_VALUE ? ((Enum<?>) v).name() : jv);
        } else {
            // @JsonValue 值对象：整体输出为单一值
            Object jv = jsonValueOf(v);
            if (jv != NO_VALUE) {
                append(sb, jv);
                return;
            }
            Map<String, Object> bean = beanToMap(v);
            if (bean.isEmpty()) {
                appendString(sb, v.toString());
            } else {
                appendMap(sb, bean);
            }
        }
    }

    /**
     * 把任意 POJO 反射为 Map：提取公开 getX()/isX() 返回值（跳过 getClass、无参返回 void、
     * 自引用与重复字段），并消费 {@link JsonProperty} / {@link JsonIgnore} / {@link JsonFormat} /
     * {@link JsonInclude} 注解。无 getter 时返回空 Map。
     */
    public static Map<String, Object> beanToMap(Object bean) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (bean == null) return map;
        Class<?> type = bean.getClass();
        Method[] methods = type.getMethods();
        for (Method m : methods) {
            if (m.getParameterCount() != 0 || m.getReturnType() == void.class) continue;
            String name = null;
            String mn = m.getName();
            if (mn.startsWith("get") && mn.length() > 3) {
                name = decap(mn.substring(3));
            } else if (mn.startsWith("is") && mn.length() > 2
                    && (m.getReturnType() == boolean.class || m.getReturnType() == Boolean.class)) {
                name = decap(mn.substring(2));
            }
            if (name == null || name.isEmpty() || "class".equals(name)) continue;

            // 方法注解
            if (m.isAnnotationPresent(JsonIgnore.class)) continue;
            JsonProperty jp = m.getAnnotation(JsonProperty.class);
            String key = (jp != null && !jp.value().isEmpty()) ? jp.value() : name;

            // 字段注解（方法优先，字段回退）
            Field field = findField(type, name);
            if (field != null) {
                if (field.isAnnotationPresent(JsonIgnore.class)) continue;
                JsonProperty fjp = field.getAnnotation(JsonProperty.class);
                if (fjp != null && !fjp.value().isEmpty()) key = fjp.value();
            }

            if (map.containsKey(key)) continue; // getX 优先于 isX，避免重复
            try {
                Object val = m.invoke(bean);
                if (val == bean) continue; // 防自引用
                if (val instanceof Date) {
                    JsonFormat jf = jsonFormatOf(m, field);
                    if (jf != null && !jf.pattern().isEmpty()) {
                        val = new SimpleDateFormat(jf.pattern()).format((Date) val);
                    }
                }
                if (val == null && isNonNullExcluded(type, m, field)) continue;
                map.put(key, val);
            } catch (Exception ignored) {
            }
        }
        return map;
    }

    /** @JsonInclude(NON_NULL) 判定：方法 > 字段 > 类级。 */
    private static boolean isNonNullExcluded(Class<?> type, Method m, Field field) {
        JsonInclude.Include inc = null;
        JsonInclude mi = m.getAnnotation(JsonInclude.class);
        if (mi != null) inc = mi.value();
        if (inc == null && field != null) {
            JsonInclude fi = field.getAnnotation(JsonInclude.class);
            if (fi != null) inc = fi.value();
        }
        if (inc == null) {
            JsonInclude ci = type.getAnnotation(JsonInclude.class);
            if (ci != null) inc = ci.value();
        }
        return inc == JsonInclude.Include.NON_NULL;
    }

    /** 读取 Date 格式化注解：方法优先，字段回退。 */
    private static JsonFormat jsonFormatOf(Method m, Field field) {
        JsonFormat jf = m.getAnnotation(JsonFormat.class);
        if (jf == null && field != null) {
            jf = field.getAnnotation(JsonFormat.class);
        }
        return jf;
    }

    /** @JsonValue 单值：返回注解方法返回值或注解字段值；无注解返回 {@link #NO_VALUE} 哨兵。 */
    private static Object jsonValueOf(Object bean) {
        Class<?> type = bean.getClass();
        for (Method m : type.getMethods()) {
            if (m.isAnnotationPresent(JsonValue.class) && m.getParameterCount() == 0) {
                try {
                    return m.invoke(bean);
                } catch (Exception e) {
                    return NO_VALUE;
                }
            }
        }
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.isAnnotationPresent(JsonValue.class)) {
                    try {
                        f.setAccessible(true);
                        return f.get(bean);
                    } catch (Exception e) {
                        return NO_VALUE;
                    }
                }
            }
        }
        return NO_VALUE;
    }

    /** 按名字在类层次查找字段（含父类），带缓存。 */
    private static Field findField(Class<?> type, String name) {
        Map<String, Field> cache = FIELD_CACHE.computeIfAbsent(type, t -> new ConcurrentHashMap<>());
        Field f = cache.get(name);
        if (f != null || cache.containsKey(name)) return f;
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field found = c.getDeclaredField(name);
                found.setAccessible(true);
                cache.put(name, found);
                return found;
            } catch (NoSuchFieldException ignored) {
            }
        }
        cache.put(name, null);
        return null;
    }

    private static String decap(String s) {
        if (s.isEmpty()) return s;
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private static void appendMap(StringBuilder sb, Map<?, ?> map) {
        sb.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            appendString(sb, String.valueOf(e.getKey()));
            sb.append(':');
            append(sb, e.getValue());
        }
        sb.append('}');
    }

    private static void appendCollection(StringBuilder sb, Collection<?> c) {
        sb.append('[');
        boolean first = true;
        for (Object o : c) {
            if (!first) sb.append(',');
            first = false;
            append(sb, o);
        }
        sb.append(']');
    }

    private static void appendArray(StringBuilder sb, Object arr) {
        sb.append('[');
        int len = Array.getLength(arr);
        for (int i = 0; i < len; i++) {
            if (i > 0) sb.append(',');
            append(sb, Array.get(arr, i));
        }
        sb.append(']');
    }

    private static void appendString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }
}
