package com.dlz.kit.util;

import java.util.Collection;
import java.util.List;

/**
 * dlz-kit 最小子集：字符串工具。
 */
public final class StringUtils {

    private StringUtils() {
    }

    public static boolean isEmpty(Object s) {
        if (s == null) return true;
        if (s instanceof String) return ((String) s).isEmpty();
        if (s instanceof java.util.Collection) return ((java.util.Collection<?>) s).isEmpty();
        if (s instanceof java.util.Map) return ((java.util.Map<?, ?>) s).isEmpty();
        if (s.getClass().isArray()) return java.lang.reflect.Array.getLength(s) == 0;
        return false;
    }

    public static boolean isNotEmpty(Object s) {
        return !isEmpty(s);
    }

    /**
     * SLF4J 风格格式化：把 {@code {}} 依次替换为参数。
     */
    public static String formatMsg(String msg, Object... args) {
        if (msg == null) return "";
        if (args == null || args.length == 0) return msg;
        StringBuilder sb = new StringBuilder(msg.length() + 32);
        int argIndex = 0;
        int i = 0;
        while (i < msg.length()) {
            if (msg.charAt(i) == '{' && i + 1 < msg.length() && msg.charAt(i + 1) == '}') {
                if (argIndex < args.length) {
                    sb.append(args[argIndex++] == null ? "null" : args[argIndex]);
                } else {
                    sb.append("{}");
                }
                i += 2;
            } else {
                sb.append(msg.charAt(i));
                i++;
            }
        }
        return sb.toString();
    }

    /**
     * 用分隔符连接集合。
     */
    public static String join(Object[] array, String separator) {
        if (array == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < array.length; i++) {
            if (i > 0) sb.append(separator);
            sb.append(array[i]);
        }
        return sb.toString();
    }

    public static String join(List<?> list, String separator) {
        return list == null ? "" : join(list.toArray(), separator);
    }

    /**
     * dlz 参数顺序重载：join(separator, list)。
     */
    public static String join(String separator, List<?> list) {
        return join(list, separator);
    }

    public static String join(String separator, Object[] array) {
        return join(array, separator);
    }

    /**
     * List&lt;String&gt; → String[]。
     */
    public static String[] listToArray(List<String> list) {
        return list == null ? new String[0] : list.toArray(new String[0]);
    }

    /**
     * 任意集合 → Object[]（SqlUtil 的 in 参数转换使用）。
     */
    public static Object[] listToArray(Collection<?> collection) {
        return collection == null ? new Object[0] : collection.toArray();
    }
}
