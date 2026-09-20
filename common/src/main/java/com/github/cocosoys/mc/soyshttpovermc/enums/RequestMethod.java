package com.github.cocosoys.mc.soyshttpovermc.enums;

/**
 * HTTP 请求方法枚举（仿 Spring 的 {@code org.springframework.web.bind.annotation.RequestMethod}）。
 *
 * <p>{@link #ANY} 为通配值（路由键 {@code "*"}，不限定方法），非真实 HTTP 方法：
 * 注解 {@code @RequestMapping} 不指定 method 时使用；{@link #toList()} 不含它。</p>
 */
public enum RequestMethod {

    GET("GET"),
    HEAD("HEAD"),
    POST("POST"),
    PUT("PUT"),
    PATCH("PATCH"),
    DELETE("DELETE"),
    OPTIONS("OPTIONS"),
    TRACE("TRACE"),

    /** 通配（{@code "*"}）：不限方法（@RequestMapping 无 method 时的路由键）。 */
    ANY("*");

    private final String code;

    RequestMethod(String code) {
        this.code = code;
    }

    /**
     * 路由 / 落库代码（真实方法 = 大写方法名；ANY = {@code "*"}）。
     */
    public String code() {
        return code;
    }

    /**
     * 返回全部真实方法名数组（排除通配 {@link #ANY}；Stream.toArray() 返回 Object[]，
     * 必须用 code() 转换，勿直接强转）。
     */
    public static String[] toList() {
        RequestMethod[] v = values();
        int n = 0;
        for (RequestMethod m : v) {
            if (m != ANY) {
                n++;
            }
        }
        String[] out = new String[n];
        int k = 0;
        for (RequestMethod m : v) {
            if (m != ANY) {
                out[k++] = m.code();
            }
        }
        return out;
    }

    /**
     * 按代码解析（忽略大小写；{@code "*"} → {@link #ANY}）；未匹配返回 null。
     */
    public static RequestMethod fromCode(String input) {
        if (input == null) {
            return null;
        }
        String normalized = input.trim();
        if ("*".equals(normalized)) {
            return ANY;
        }
        normalized = normalized.toUpperCase();
        for (RequestMethod m : values()) {
            if (m.code.equals(normalized)) {
                return m;
            }
        }
        return null;
    }
}
