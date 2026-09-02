package com.dlz.kit.util;

import java.math.BigDecimal;
import java.util.Date;

/**
 * dlz-kit 最小子集：值转换工具（TableColumnMapper 等使用）。
 */
public final class ValUtil {

    private ValUtil() {
    }

    public static boolean isEmpty(Object obj) {
        if (obj == null) return true;
        if (obj instanceof String) return ((String) obj).isEmpty();
        if (obj instanceof java.util.Collection) return ((java.util.Collection<?>) obj).isEmpty();
        if (obj instanceof java.util.Map) return ((java.util.Map<?, ?>) obj).isEmpty();
        if (obj.getClass().isArray()) return java.lang.reflect.Array.getLength(obj) == 0;
        return false;
    }

    public static String toStr(Object obj) {
        return obj == null ? null : String.valueOf(obj);
    }

    public static String toStr(Object obj, String def) {
        return obj == null ? def : String.valueOf(obj);
    }

    public static int toInt(Object obj) {
        if (obj == null) return 0;
        if (obj instanceof Number) return ((Number) obj).intValue();
        return Integer.parseInt(String.valueOf(obj).trim());
    }

    public static int toInt(Object obj, int def) {
        if (obj == null) return def;
        try {
            return toInt(obj);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static long toLong(Object obj) {
        if (obj == null) return 0L;
        if (obj instanceof Number) return ((Number) obj).longValue();
        return Long.parseLong(String.valueOf(obj).trim());
    }

    public static long toLong(Object obj, long def) {
        if (obj == null) return def;
        try {
            return toLong(obj);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static double toDouble(Object obj) {
        if (obj == null) return 0d;
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        return Double.parseDouble(String.valueOf(obj).trim());
    }

    public static double toDouble(Object obj, double def) {
        if (obj == null) return def;
        try {
            return toDouble(obj);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static BigDecimal toBigDecimal(Object obj) {
        if (obj == null) return BigDecimal.ZERO;
        if (obj instanceof BigDecimal) return (BigDecimal) obj;
        if (obj instanceof Number) return BigDecimal.valueOf(((Number) obj).doubleValue());
        return new BigDecimal(String.valueOf(obj).trim());
    }

    public static boolean toBoolean(Object obj) {
        if (obj == null) return false;
        if (obj instanceof Boolean) return (Boolean) obj;
        return Boolean.parseBoolean(String.valueOf(obj).trim());
    }

    public static boolean toBoolean(Object obj, boolean def) {
        if (obj == null) return def;
        if (obj instanceof Boolean) return (Boolean) obj;
        String s = String.valueOf(obj).trim();
        if (s.isEmpty()) return def;
        return Boolean.parseBoolean(s);
    }

    public static Date toDate(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Date) return (Date) obj;
        if (obj instanceof Number) return new Date(((Number) obj).longValue());
        return new Date(Long.parseLong(String.valueOf(obj).trim()));
    }

    /**
     * 通用对象转换（目标类型推断）。
     */
    public static <T> T toObj(Object obj, Class<T> clazz) {
        if (obj == null) return null;
        if (clazz == null || clazz.isInstance(obj)) return (T) obj;
        if (clazz == String.class) return (T) toStr(obj);
        if (clazz == Integer.class || clazz == int.class) return (T) Integer.valueOf(toInt(obj));
        if (clazz == Long.class || clazz == long.class) return (T) Long.valueOf(toLong(obj));
        if (clazz == Double.class || clazz == double.class) return (T) Double.valueOf(toDouble(obj));
        if (clazz == Boolean.class || clazz == boolean.class) return (T) Boolean.valueOf(toBoolean(obj));
        if (clazz == BigDecimal.class) return (T) toBigDecimal(obj);
        if (clazz == Date.class) return (T) toDate(obj);
        return (T) obj;
    }

    /**
     * 按泛型 Type 转换（支持 ParameterizedType 的原始类型）。
     */
    public static Object toObj(Object obj, java.lang.reflect.Type type) {
        if (obj == null || type == null) return obj;
        Class<?> raw = type instanceof Class ? (Class<?>) type
                : type instanceof java.lang.reflect.ParameterizedType
                  ? (Class<?>) ((java.lang.reflect.ParameterizedType) type).getRawType() : null;
        if (raw == null || raw == Object.class || raw.isInstance(obj)) return obj;
        return toObj(obj, raw);
    }

    /**
     * 转数组（逗号分隔字符串 / 集合 → Object[]）。
     */
    public static Object[] toArray(Object obj) {
        if (obj == null) return new Object[0];
        if (obj.getClass().isArray()) {
            int len = java.lang.reflect.Array.getLength(obj);
            Object[] out = new Object[len];
            for (int i = 0; i < len; i++) out[i] = java.lang.reflect.Array.get(obj, i);
            return out;
        }
        if (obj instanceof java.util.Collection) {
            return ((java.util.Collection<?>) obj).toArray();
        }
        if (obj instanceof String) {
            String s = ((String) obj).trim();
            return s.isEmpty() ? new Object[0] : s.split("\\s*,\\s*");
        }
        return new Object[]{obj};
    }
}
