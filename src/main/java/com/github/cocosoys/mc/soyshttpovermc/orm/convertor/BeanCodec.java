package com.github.cocosoys.mc.soyshttpovermc.orm.convertor;

import com.github.cocosoys.mc.soyshttpovermc.orm.meta.FieldMeta;
import com.github.cocosoys.mc.soyshttpovermc.orm.meta.PojoMeta;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemorySection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bean ↔ YamlConfiguration 节点双向编解码（YAML 后端的数据映射层）：
 * <ul>
 *   <li>标量字段：String/数值/布尔/Date(毫秒)/枚举(name) 直接存节点值；</li>
 *   <li>嵌套字段：List&lt;标量&gt; → 列表；List&lt;对象&gt; → 列表（对象转 Section）；Map → 节点；对象 → Section。</li>
 * </ul>
 */
public final class BeanCodec {

    private BeanCodec() {
    }

    // ===== 序列化：Bean → ConfigurationSection（写入目标节点） =====

    /** 把实体写入指定 section（字段 → 节点键）。 */
    public static void serialize(Object bean, ConfigurationSection target) {
        if (bean == null || target == null) return;
        PojoMeta meta = PojoMeta.of(bean.getClass());
        for (FieldMeta fm : meta.getFields()) {
            if (fm.isIgnored()) continue;
            Object value = readField(bean, fm.field);
            if (value == null) {
                target.set(fm.columnName, null);
                continue;
            }
            target.set(fm.columnName, encodeValue(value));
        }
    }

    private static Object encodeValue(Object value) {
        if (value instanceof Date) {
            return ((Date) value).getTime();
        }
        if (value instanceof Enum) {
            return ((Enum<?>) value).name();
        }
        return value; // 标量直接存；List/Map/对象由 YamlConfiguration 原生支持（对象按 Map 存，需 map 化）
    }

    // ===== 反序列化：ConfigurationSection → Bean =====

    /** 从 section 反序列化实体（字段缺失取默认值）。 */
    public static <T> T deserialize(Class<T> beanClass, ConfigurationSection source) {
        if (source == null) return null;
        PojoMeta meta = PojoMeta.of(beanClass);
        T bean = newInstance(beanClass);
        if (bean == null) return null;
        for (FieldMeta fm : meta.getFields()) {
            if (fm.isIgnored()) continue;
            Object raw = source.get(fm.columnName);
            Object value = decodeValue(raw, fm.field.getGenericType(), fm.type);
            writeField(bean, fm.field, value);
        }
        return bean;
    }

    private static Object decodeValue(Object raw, Type genericType, Class<?> rawType) {
        if (raw == null) return null;
        if (raw instanceof MemorySection || raw instanceof Map) {
            // 嵌套对象 / Map / 对象列表元素
            if (rawType == List.class || rawType == java.util.Collection.class || rawType.isArray()) {
                // List<X>：Section 按索引或 Map
                return decodeList(raw, genericType);
            }
            if (rawType == Map.class || rawType == java.util.HashMap.class || rawType == java.util.LinkedHashMap.class) {
                return decodeMap(raw);
            }
            // 嵌套对象（Section → 目标类型实例）
            return decodeNested(raw, rawType);
        }
        if (raw instanceof List) {
            return decodeList(raw, genericType);
        }
        return convertScalar(raw, rawType);
    }

    private static Object decodeList(Object raw, Type genericType) {
        List<Object> out = new ArrayList<>();
        Class<?> elemType = Object.class;
        if (genericType instanceof ParameterizedType) {
            Type arg = ((ParameterizedType) genericType).getActualTypeArguments()[0];
            if (arg instanceof Class) {
                elemType = (Class<?>) arg;
            }
        }
        if (raw instanceof ConfigurationSection) {
            ConfigurationSection sec = (ConfigurationSection) raw;
            int i = 0;
            while (sec.isSet(String.valueOf(i))) {
                out.add(decodeValue(sec.get(String.valueOf(i)), elemType, elemType));
                i++;
            }
        } else if (raw instanceof List) {
            for (Object item : (List<?>) raw) {
                out.add(decodeValue(item, elemType, elemType));
            }
        } else if (raw instanceof Map) {
            for (Object item : ((Map<?, ?>) raw).values()) {
                out.add(decodeValue(item, elemType, elemType));
            }
        }
        return out;
    }

    private static Map<String, Object> decodeMap(Object raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (raw instanceof ConfigurationSection) {
            for (String key : ((ConfigurationSection) raw).getKeys(false)) {
                out.put(key, ((ConfigurationSection) raw).get(key));
            }
        } else if (raw instanceof Map) {
            for (Map.Entry<?, ?> e : ((Map<?, ?>) raw).entrySet()) {
                out.put(String.valueOf(e.getKey()), e.getValue());
            }
        }
        return out;
    }

    private static Object decodeNested(Object raw, Class<?> type) {
        ConfigurationSection sec = raw instanceof ConfigurationSection
                ? (ConfigurationSection) raw : yamlSectionOf(raw);
        return deserialize(type, sec);
    }

    private static ConfigurationSection yamlSectionOf(Object raw) {
        YamlConfiguration tmp = new YamlConfiguration();
        ConfigurationSection root = tmp.createSection("root");
        if (raw instanceof Map) {
            for (Map.Entry<?, ?> e : ((Map<?, ?>) raw).entrySet()) {
                root.set(String.valueOf(e.getKey()), e.getValue());
            }
        }
        return root;
    }

    private static Object convertScalar(Object raw, Class<?> type) {
        if (type == Object.class || type.isInstance(raw)) return raw;
        if (type == String.class) return String.valueOf(raw);
        if (type == Integer.class || type == int.class) {
            return raw instanceof Number ? ((Number) raw).intValue() : Integer.parseInt(String.valueOf(raw));
        }
        if (type == Long.class || type == long.class) {
            return raw instanceof Number ? ((Number) raw).longValue() : Long.parseLong(String.valueOf(raw));
        }
        if (type == Double.class || type == double.class) {
            return raw instanceof Number ? ((Number) raw).doubleValue() : Double.parseDouble(String.valueOf(raw));
        }
        if (type == Float.class || type == float.class) {
            return raw instanceof Number ? ((Number) raw).floatValue() : Float.parseFloat(String.valueOf(raw));
        }
        if (type == Boolean.class || type == boolean.class) {
            return raw instanceof Boolean ? raw : Boolean.parseBoolean(String.valueOf(raw));
        }
        if (type == Date.class) {
            long ms = raw instanceof Number ? ((Number) raw).longValue() : Long.parseLong(String.valueOf(raw));
            return new Date(ms);
        }
        if (type.isEnum()) {
            return Enum.valueOf((Class<Enum>) type, String.valueOf(raw));
        }
        return raw; // 无法转换保持原值
    }

    // ===== 反射读写 =====

    private static Object readField(Object bean, Field field) {
        try {
            field.setAccessible(true);
            return field.get(bean);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    private static void writeField(Object bean, Field field, Object value) {
        if (value == null) return;
        try {
            field.setAccessible(true);
            field.set(bean, value);
        } catch (IllegalAccessException e) {
            // 忽略：无法写入的字段保持默认值
        }
    }

    private static <T> T newInstance(Class<T> type) {
        try {
            Constructor<T> ctor = type.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (Exception e) {
            return null;
        }
    }
}
