package com.github.cocosoys.mc.soyshttpovermc.util;

import com.github.cocosoys.mc.soyshttpovermc.spring.entity.BaseEntity;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 零依赖的最小 JSON 序列化器（避免引入 Jackson）。
 * 支持：Map / Collection / 数组 / String / Number / Boolean / null / POJO（反射 getter）。
 * <p>实体类（继承 {@link BaseEntity}）可直接放入
 * {@link AjaxResult#success(Object)} 序列化输出。
 */
public final class JsonWriter {

    private JsonWriter() {
    }

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
        } else {
            // POJO：反射公开 getter 序列化
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
     * 自引用与重复字段）。无 getter 时返回空 Map。
     */
    public static Map<String, Object> beanToMap(Object bean) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (bean == null) return map;
        Method[] methods = bean.getClass().getMethods();
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
            if (map.containsKey(name)) continue; // getX 优先于 isX，避免重复
            try {
                Object val = m.invoke(bean);
                if (val == bean) continue; // 防自引用
                map.put(name, val);
            } catch (Exception ignored) {
            }
        }
        return map;
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
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
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
