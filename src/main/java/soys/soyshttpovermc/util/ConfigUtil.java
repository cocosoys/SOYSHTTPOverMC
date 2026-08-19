package soys.soyshttpovermc.util;

import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置工具类：提供 YAML → JSON 的自动转换方法。
 * <p>支持两种输入：</p>
 * <ul>
 *   <li>{@link YamlConfiguration}（整个文件 → JSON）</li>
 *   <li>{@link ConfigurationSection}（子树 → JSON）</li>
 * </ul>
 * <p>底层委托 {@link JsonWriter#write(Object)} 完成序列化，零外部依赖。</p>
 */
public final class ConfigUtil {

    private ConfigUtil() {
    }

    /**
     * 将 {@link ConfigurationSection}（或其子类 {@link YamlConfiguration}）转换为 JSON 字符串。
     * <p>嵌套的 {@link ConfigurationSection} 自动递归展开为 JSON 对象；</p>
     * <p>基本类型 / 字符串 / 列表 / null 按 JSON 标准序列化。</p>
     *
     * @param section YAML 配置节点（null 返回 "null"）
     * @return 格式化后的 JSON 字符串
     */
    public static String toJson(ConfigurationSection section) {
        if (section == null) {
            return "null";
        }
        Map<String, Object> map = toMap(section);
        return JsonWriter.write(map);
    }

    /**
     * 将 {@link ConfigurationSection} 递归转换为 {@link Map}。
     * <p>嵌套的 {@link ConfigurationSection} 递归展开为 {@code Map<String, Object>}；</p>
     * <p>列表中的 {@link ConfigurationSection} 元素也递归展开。</p>
     *
     * @param section YAML 配置节点（null 返回空 map）
     * @return 平铺/嵌套的 Java Map
     */
    public static Map<String, Object> toMap(ConfigurationSection section) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (section == null) {
            return map;
        }
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            map.put(key, convertValue(value));
        }
        return map;
    }

    /**
     * 递归转换单个值：{@link ConfigurationSection} → Map，列表中的嵌套 Section 也展开。
     */
    private static Object convertValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof ConfigurationSection) {
            return toMap((ConfigurationSection) value);
        }
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            // 检查列表中是否包含嵌套的 ConfigurationSection
            boolean hasNested = false;
            for (Object item : list) {
                if (item instanceof ConfigurationSection) {
                    hasNested = true;
                    break;
                }
            }
            if (hasNested) {
                java.util.List<Object> converted = new java.util.ArrayList<>();
                for (Object item : list) {
                    converted.add(convertValue(item));
                }
                return converted;
            }
        }
        // 基本类型、String、List<基本类型> 直接返回
        return value;
    }
}