package com.dlz.kit.json;

import java.util.LinkedHashMap;

/**
 * dlz-kit 最小子集：命名参数 Map（自定义 SQL 条件占位符 {@code #{key}} 的参数载体）。
 * 支持链式 {@code set}：{@code new JSONMap("min",18).set("max",60)}。
 * 原版支持 JSON 字符串解析（依赖 JSON 库），本项目取「命名参数 Map」最小语义。
 */
public class JSONMap extends LinkedHashMap<String, Object> {

    private static final long serialVersionUID = 1L;

    public JSONMap() {
    }

    public JSONMap(String key, Object value) {
        put(key, value);
    }

    /** 链式设置参数。 */
    public JSONMap set(String key, Object value) {
        put(key, value);
        return this;
    }

    public JSONMap setIfNotNull(String key, Object value) {
        if (value != null) {
            put(key, value);
        }
        return this;
    }

    /** Date 值格式化为字符串（ResultMap.coverDate2Str 使用）。 */
    public String getDateStr(String key, String dateFormat) {
        Object v = get(key);
        if (!(v instanceof java.util.Date)) return null;
        return new java.text.SimpleDateFormat(dateFormat).format((java.util.Date) v);
    }

    // ===== 便捷读取 =====

    public String getStr(String key) {
        Object v = get(key);
        return v == null ? null : String.valueOf(v);
    }

    public String getStr(String key, String def) {
        Object v = get(key);
        return v == null ? def : String.valueOf(v);
    }

    public Long getLong(String key) {
        Object v = get(key);
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).longValue();
        return Long.parseLong(String.valueOf(v).trim());
    }

    public long getLong(String key, long def) {
        Object v = get(key);
        if (v == null) return def;
        try {
            return getLong(key);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public Integer getInt(String key, int def) {
        Object v = get(key);
        if (v == null) return def;
        try {
            return v instanceof Number ? ((Number) v).intValue() : Integer.parseInt(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public Boolean getBoolean(String key, boolean def) {
        Object v = get(key);
        if (v == null) return def;
        if (v instanceof Boolean) return (Boolean) v;
        return Boolean.parseBoolean(String.valueOf(v).trim());
    }
}
