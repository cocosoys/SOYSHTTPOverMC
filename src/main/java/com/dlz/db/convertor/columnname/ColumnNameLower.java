package com.dlz.db.convertor.columnname;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 小写下划线列名转换器（本项目扩展，用于跨端列名统一）：
 * <ul>
 *   <li>{@code toDbColumnName}：userName → user_name（小写下划线，与 YAML 端 PojoMeta.camelToUnderline 一致）；</li>
 *   <li>{@code toFieldName}：user_name → userName（驼峰）。</li>
 * </ul>
 * 替换 dlz 默认的 ColumnNameCamel（大写风格 USER_NAME），保证 SQL 列名与 YAML 键名双端同名同构。
 */
public class ColumnNameLower implements IColumnNameConvertor {

    private static final Pattern TO_CAMEL = Pattern.compile("_([a-z])");

    @Override
    public String toFieldName(String dbKey) {
        if (dbKey == null) return "";
        String s = dbKey.toLowerCase(Locale.ROOT);
        Matcher mat = TO_CAMEL.matcher(s);
        while (mat.find()) {
            s = s.replace("_" + mat.group(1), mat.group(1).toUpperCase(Locale.ROOT));
        }
        return s.replaceAll("_", "");
    }

    @Override
    public String toDbColumnName(String beanKey) {
        if (beanKey == null) return null;
        if (beanKey.contains("_")) {
            return beanKey.toLowerCase(Locale.ROOT); // 已含下划线：仅小写
        }
        StringBuilder sb = new StringBuilder(beanKey.length() + 4);
        for (int i = 0; i < beanKey.length(); i++) {
            char ch = beanKey.charAt(i);
            if (Character.isUpperCase(ch) && i > 0) {
                sb.append('_').append(Character.toLowerCase(ch));
            } else {
                sb.append(Character.toLowerCase(ch));
            }
        }
        return sb.toString();
    }
}
