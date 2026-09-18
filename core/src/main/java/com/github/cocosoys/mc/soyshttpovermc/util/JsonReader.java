package com.github.cocosoys.mc.soyshttpovermc.util;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.cocosoys.mc.soyshttpovermc.orm.convertor.BeanCodec;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 零依赖的最小 JSON 反序列化器（与 {@link JsonWriter} 对称，只消费 jackson-annotations 注解）。
 * <p>支持：对象 → {@link Map}、数组 → {@link List}、字符串/数字/布尔/null（含转义与
 * Unicode），以及 POJO 反射绑定（按 JSON 键名找 {@code setXxx} setter，类型转换覆盖
 * String/int/long/boolean/double/float/Date/List/Map/嵌套 POJO）。</p>
 * <p><b>注解支持（反序列化侧）</b>：
 * <ul>
 *   <li>{@link JsonProperty}：JSON 键 → 实体字段名反向映射（RuoYi 风格 {@code create_time} → {@code createTime}）；</li>
 *   <li>{@link JsonFormat}：Date 字段按注解 pattern 解析（缺省回退 {@link BeanCodec#parseDate}）。</li>
 * </ul></p>
 * <p>用途：{@code @RequestBody} 绑定实体参数——{@code JsonReader.fromJson(body, SysMenu.class)}。
 * 解析或绑定失败抛出 {@link IllegalArgumentException}，由调用方转 400 响应。</p>
 */
public final class JsonReader {

    private JsonReader() {
    }

    /** JSON 文本 → 对象（Map/List/标量）；空文本返回 null。 */
    public static Object parse(String text) {
        if (text == null) {
            return null;
        }
        Parser p = new Parser(text.trim());
        Object v = p.parseValue();
        p.skipWs();
        if (p.pos < p.src.length()) {
            throw new IllegalArgumentException("JSON 解析失败：第 " + p.pos + " 字符处存在多余内容");
        }
        return v;
    }

    /** JSON 文本 → Map（非对象返回空 Map）。 */
    public static Map<String, Object> parseObject(String text) {
        Object v = parse(text);
        return v instanceof Map ? (Map<String, Object>) v : new LinkedHashMap<>();
    }

    /** JSON 文本 → List（非数组返回空 List）。 */
    public static List<Object> parseArray(String text) {
        Object v = parse(text);
        return v instanceof List ? (List<Object>) v : new ArrayList<>();
    }

    /**
     * JSON 文本 → 目标类型：String/基础类型直接转换；POJO 反射 setter 绑定。
     * 空文本返回目标类型空实例（字段默认值，由业务层校验必填）。
     */
    @SuppressWarnings("unchecked")
    public static <T> T fromJson(String text, Class<T> type) {
        if (text == null || text.trim().isEmpty()) {
            return newInstance(type);
        }
        Object v = parse(text);
        return (T) convert(v, type);
    }

    private static <T> T newInstance(Class<T> type) {
        try {
            Constructor<T> c = type.getDeclaredConstructor();
            c.setAccessible(true);
            return c.newInstance();
        } catch (Exception e) {
            throw new IllegalArgumentException("无法实例化请求体类型: " + type.getName(), e);
        }
    }

    private static Object convert(Object v, Class<?> type) {
        if (v == null) {
            return null;
        }
        if (type == String.class) {
            return String.valueOf(v);
        }
        if (type == int.class || type == Integer.class) {
            return v instanceof Number ? ((Number) v).intValue() : Integer.parseInt(String.valueOf(v).trim());
        }
        if (type == long.class || type == Long.class) {
            return v instanceof Number ? ((Number) v).longValue() : Long.parseLong(String.valueOf(v).trim());
        }
        if (type == double.class || type == Double.class) {
            return v instanceof Number ? ((Number) v).doubleValue() : Double.parseDouble(String.valueOf(v).trim());
        }
        if (type == float.class || type == Float.class) {
            return v instanceof Number ? ((Number) v).floatValue() : Float.parseFloat(String.valueOf(v).trim());
        }
        if (type == boolean.class || type == Boolean.class) {
            return v instanceof Boolean ? v : Boolean.parseBoolean(String.valueOf(v).trim());
        }
        if (type == Date.class) {
            if (v instanceof Date) return v;
            if (v instanceof Number) return new Date(((Number) v).longValue());
            try {
                return BeanCodec.parseDate(String.valueOf(v));
            } catch (ParseException e) {
                throw new IllegalArgumentException("无法将 " + v.getClass().getSimpleName() + " 转换为 java.util.Date: " + v, e);
            }
        }
        if (type.isAssignableFrom(v.getClass())) {
            return v; // Map/List/String/Number 等原样
        }
        if (v instanceof Map) {
            return bindBean((Map<?, ?>) v, type);
        }
        if (v instanceof List) {
            return v; // 字段声明 List<X> 时元素为 JSON 原始类型（String/Number），由调用处按需转换
        }
        throw new IllegalArgumentException("无法将 " + v.getClass().getSimpleName() + " 转换为 " + type.getName());
    }

    /** POJO 绑定：JSON 键 → setXxx setter（支持 @JsonProperty 反向映射）；未知字段忽略（宽容）；绑定失败抛异常。 */
    private static Object bindBean(Map<?, ?> map, Class<?> type) {
        Object bean = newInstance((Class<Object>) type);
        for (Map.Entry<?, ?> e : map.entrySet()) {
            String key = String.valueOf(e.getKey());
            if (key.isEmpty()) {
                continue;
            }
            Method setter = findSetter(type, key);
            if (setter == null) {
                continue;
            }
            Class<?> paramType = setter.getParameterTypes()[0];
            Object value = e.getValue();
            try {
                if (paramType == Date.class && value != null) {
                    JsonFormat jf = jsonFormatOnField(type, decap(setter.getName().substring(3)));
                    if (jf != null && !jf.pattern().isEmpty()) {
                        setter.invoke(bean, parseDateWith(jf.pattern(), String.valueOf(value)));
                        continue;
                    }
                }
                setter.invoke(bean, convert(value, paramType));
            } catch (Exception ex) {
                throw new IllegalArgumentException("字段 " + key + " 绑定失败: " + ex.getMessage(), ex);
            }
        }
        return bean;
    }

    /** @JsonProperty 反向映射：JSON 键 → 实体字段名（字段注解优先，getter/setter 方法注解回退）；未命中原样返回。 */
    private static String fieldNameByJsonProperty(Class<?> type, String jsonKey) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                JsonProperty jp = f.getAnnotation(JsonProperty.class);
                if (jp != null && jsonKey.equals(jp.value())) {
                    return f.getName();
                }
            }
        }
        for (Method m : type.getMethods()) {
            JsonProperty jp = m.getAnnotation(JsonProperty.class);
            if (jp == null || !jsonKey.equals(jp.value())) {
                continue;
            }
            if (m.getParameterCount() == 1 && m.getName().startsWith("set")) {
                return decap(m.getName().substring(3));
            }
            if (m.getParameterCount() == 0) {
                if (m.getName().startsWith("get")) {
                    return decap(m.getName().substring(3));
                }
                if (m.getName().startsWith("is")) {
                    return decap(m.getName().substring(2));
                }
            }
        }
        return jsonKey;
    }

    private static Method findSetter(Class<?> type, String field) {
        String mapped = fieldNameByJsonProperty(type, field);
        String setterName = "set" + Character.toUpperCase(mapped.charAt(0)) + mapped.substring(1);
        for (Method m : type.getMethods()) {
            if (m.getName().equals(setterName) && m.getParameterCount() == 1) {
                return m;
            }
        }
        return null;
    }

    /** 按字段名在类层次查找 @JsonFormat 注解（反序列化侧读取用）。 */
    private static JsonFormat jsonFormatOnField(Class<?> type, String fieldName) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(fieldName);
                return f.getAnnotation(JsonFormat.class);
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    private static Date parseDateWith(String pattern, String s) {
        try {
            return new SimpleDateFormat(pattern).parse(s);
        } catch (ParseException e) {
            throw new IllegalArgumentException("日期格式错误: " + s + "（期望 " + pattern + "）", e);
        }
    }

    private static String decap(String s) {
        if (s.isEmpty()) return s;
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    // ===== 词法解析 =====

    private static final class Parser {
        final String src;
        int pos = 0;

        Parser(String src) {
            this.src = src;
        }

        void skipWs() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
                pos++;
            }
        }

        Object parseValue() {
            skipWs();
            if (pos >= src.length()) {
                throw new IllegalArgumentException("JSON 意外结束");
            }
            char c = src.charAt(pos);
            if (c == '{') {
                return parseObject();
            }
            if (c == '[') {
                return parseArray();
            }
            if (c == '"') {
                return parseString();
            }
            if (c == 't') {
                expect("true");
                return Boolean.TRUE;
            }
            if (c == 'f') {
                expect("false");
                return Boolean.FALSE;
            }
            if (c == 'n') {
                expect("null");
                return null;
            }
            return parseNumber();
        }

        Map<String, Object> parseObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++; // {
            skipWs();
            if (pos < src.length() && src.charAt(pos) == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWs();
                String key = parseString();
                skipWs();
                if (pos >= src.length() || src.charAt(pos) != ':') {
                    throw new IllegalArgumentException("对象键后缺少冒号");
                }
                pos++;
                Object val = parseValue();
                map.put(key, val);
                skipWs();
                if (pos >= src.length()) {
                    throw new IllegalArgumentException("对象未闭合");
                }
                char c = src.charAt(pos);
                if (c == ',') {
                    pos++;
                    continue;
                }
                if (c == '}') {
                    pos++;
                    return map;
                }
                throw new IllegalArgumentException("对象分隔符非法: " + c);
            }
        }

        List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            pos++; // [
            skipWs();
            if (pos < src.length() && src.charAt(pos) == ']') {
                pos++;
                return list;
            }
            while (true) {
                list.add(parseValue());
                skipWs();
                if (pos >= src.length()) {
                    throw new IllegalArgumentException("数组未闭合");
                }
                char c = src.charAt(pos);
                if (c == ',') {
                    pos++;
                    continue;
                }
                if (c == ']') {
                    pos++;
                    return list;
                }
                throw new IllegalArgumentException("数组分隔符非法: " + c);
            }
        }

        String parseString() {
            if (pos >= src.length() || src.charAt(pos) != '"') {
                throw new IllegalArgumentException("期望字符串");
            }
            pos++;
            StringBuilder sb = new StringBuilder();
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c == '"') {
                    pos++;
                    return sb.toString();
                }
                if (c == '\\') {
                    pos++;
                    if (pos >= src.length()) {
                        throw new IllegalArgumentException("转义未完成");
                    }
                    char e = src.charAt(pos);
                    switch (e) {
                        case '"':
                            sb.append('"');
                            break;
                        case '\\':
                            sb.append('\\');
                            break;
                        case '/':
                            sb.append('/');
                            break;
                        case 'n':
                            sb.append('\n');
                            break;
                        case 'r':
                            sb.append('\r');
                            break;
                        case 't':
                            sb.append('\t');
                            break;
                        case 'b':
                            sb.append('\b');
                            break;
                        case 'f':
                            sb.append('\f');
                            break;
                        case 'u':
                            if (pos + 4 >= src.length()) {
                                throw new IllegalArgumentException("Unicode 转义不完整");
                            }
                            try {
                                sb.append((char) Integer.parseInt(src.substring(pos + 1, pos + 5), 16));
                                pos += 4;
                            } catch (NumberFormatException ex) {
                                throw new IllegalArgumentException("Unicode 转义非法");
                            }
                            break;
                        default:
                            throw new IllegalArgumentException("非法转义: \\" + e);
                    }
                    pos++;
                } else {
                    sb.append(c);
                    pos++;
                }
            }
            throw new IllegalArgumentException("字符串未闭合");
        }

        Number parseNumber() {
            int start = pos;
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') {
                    pos++;
                } else {
                    break;
                }
            }
            String num = src.substring(start, pos);
            if (num.isEmpty()) {
                throw new IllegalArgumentException("非法数字");
            }
            if (num.indexOf('.') >= 0 || num.indexOf('e') >= 0 || num.indexOf('E') >= 0) {
                return Double.parseDouble(num);
            }
            long l = Long.parseLong(num);
            if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
                return (int) l;
            }
            return l;
        }

        void expect(String token) {
            if (!src.startsWith(token, pos)) {
                throw new IllegalArgumentException("期望 " + token);
            }
            pos += token.length();
        }
    }
}
