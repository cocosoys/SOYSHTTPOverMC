package soys.soyshttpovermc.annotations;

/**
 * HTTP 请求方法枚举（仿 Spring 的 {@code org.springframework.web.bind.annotation.RequestMethod}）。
 */
public enum RequestMethod {
    GET, HEAD, POST, PUT, PATCH, DELETE, OPTIONS, TRACE;

    /** 返回全部方法名数组（注意：Stream.toArray() 返回 Object[]，必须用 name() 转换，勿直接强转） */
    public static String[] toList() {
        RequestMethod[] v = values();
        String[] out = new String[v.length];
        for (int i = 0; i < v.length; i++) {
            out[i] = v[i].name();
        }
        return out;
    }
}
