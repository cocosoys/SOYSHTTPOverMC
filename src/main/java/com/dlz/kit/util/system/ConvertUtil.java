package com.dlz.kit.util.system;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * dlz-kit 最小子集：通用类型转换（Map/ResultMap → Bean；Map 列表 → Bean 列表）。
 * 供 dlz-db-core 的 service 层（{@code IDbJdbcService/IDbListService}）使用：
 * 把 JDBC 行（Map）转换为目标 Pojo。
 */
public final class ConvertUtil {

    private ConvertUtil() {
    }

    /** Map → Bean（字段名匹配；值为 Map/List 时递归）。 */
    public static <T> T convert(Map<String, Object> map, Class<T> clazz) {
        if (map == null || clazz == null) return null;
        if (clazz == Map.class || clazz.isInstance(map)) {
            return (T) map;
        }
        try {
            T bean = clazz.getDeclaredConstructor().newInstance();
            for (Field f : FieldReflections.getFields(clazz)) {
                Object value = map.get(f.getName());
                if (value == null) {
                    value = map.get(f.getName().toLowerCase());
                }
                if (value == null) {
                    continue;
                }
                try {
                    f.setAccessible(true);
                    f.set(bean, coerce(value, f.getType()));
                } catch (IllegalAccessException ignored) {
                }
            }
            return bean;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /** Map 列表 → Bean 列表。 */
    public static <T> List<T> convertList(List<? extends Map<String, Object>> maps, Class<T> clazz) {
        List<T> out = new ArrayList<>();
        if (maps == null) return out;
        for (Map<String, Object> m : maps) {
            out.add(convert(m, clazz));
        }
        return out;
    }

    private static Object coerce(Object value, Class<?> type) {
        if (type == String.class) return String.valueOf(value);
        if (type == Integer.class || type == int.class) {
            return value instanceof Number ? ((Number) value).intValue() : Integer.parseInt(String.valueOf(value));
        }
        if (type == Long.class || type == long.class) {
            return value instanceof Number ? ((Number) value).longValue() : Long.parseLong(String.valueOf(value));
        }
        if (type == Double.class || type == double.class) {
            return value instanceof Number ? ((Number) value).doubleValue() : Double.parseDouble(String.valueOf(value));
        }
        if (type == Float.class || type == float.class) {
            return value instanceof Number ? ((Number) value).floatValue() : Float.parseFloat(String.valueOf(value));
        }
        if (type == Boolean.class || type == boolean.class) {
            return value instanceof Boolean ? value : Boolean.parseBoolean(String.valueOf(value));
        }
        return value;
    }
}
