package com.dlz.kit.util;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * dlz-kit 最小子集：JSON 工具（最小语义：Map 路径取值，不依赖 JSON 库）。
 * {@code at(map, "key.sub")}：从嵌套 Map/JSONMap 按点分路径取值（SqlUtil 的 #{key.sub} 占位解析）。
 */
public final class JacksonUtil {

    private JacksonUtil() {
    }

    /**
     * 按路径从嵌套 Map 取值；路径为 {@code a.b.c}（点分）。
     * 单层时直接取 key；支持 key 在任意深度的 Map/JSONMap。
     */
    @SuppressWarnings("unchecked")
    public static Object at(Object src, String path) {
        if (src == null || path == null) return null;
        String p = path.trim();
        if (p.isEmpty()) return src;
        Object cur = src;
        for (String part : p.split("\\.")) {
            if (cur instanceof Map) {
                cur = ((Map<String, Object>) cur).get(part);
            } else {
                return null;
            }
        }
        return cur;
    }

    /** 便捷：字符串化。 */
    public static String toStr(Object obj) {
        return obj == null ? null : String.valueOf(obj);
    }
}
