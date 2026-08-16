package soys.soyshttpovermc.web;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 扩展名 → HTTP Content-Type 映射。前端资源若 Content-Type 不对，浏览器不渲染 JS/CSS/图片。
 *
 * <p>内置一组常见类型；第三方插件可经 {@link #register(String, String)} 追加/覆盖
 * （如 .vue / .ts / .json5 等），登记在插件 onEnable 中调用一次即可全局生效。</p>
 */
public final class MimeTypes {

    private static final Map<String, String> MAP = new ConcurrentHashMap<>();

    static {
        MAP.put("html", "text/html; charset=utf-8");
        MAP.put("htm", "text/html; charset=utf-8");
        MAP.put("js", "application/javascript; charset=utf-8");
        MAP.put("mjs", "application/javascript; charset=utf-8");
        MAP.put("css", "text/css; charset=utf-8");
        MAP.put("json", "application/json; charset=utf-8");
        MAP.put("map", "application/json; charset=utf-8");
        MAP.put("txt", "text/plain; charset=utf-8");
        MAP.put("xml", "application/xml; charset=utf-8");
        MAP.put("svg", "image/svg+xml");
        MAP.put("png", "image/png");
        MAP.put("jpg", "image/jpeg");
        MAP.put("jpeg", "image/jpeg");
        MAP.put("gif", "image/gif");
        MAP.put("webp", "image/webp");
        MAP.put("ico", "image/x-icon");
        MAP.put("bmp", "image/bmp");
        MAP.put("woff", "font/woff");
        MAP.put("woff2", "font/woff2");
        MAP.put("ttf", "font/ttf");
        MAP.put("eot", "application/vnd.ms-fontobject");
        MAP.put("pdf", "application/pdf");
        MAP.put("wasm", "application/wasm");
    }

    private MimeTypes() {
    }

    /**
     * 注册/覆盖一个扩展名映射（线程安全）。第三方插件在 onEnable 中调用即可让自定义扩展名
     * （如 vue / ts / json5 / wgsl）以正确 Content-Type 输出，浏览器才会按预期渲染/执行。
     *
     * @param ext  扩展名（不含点，如 {@code "vue"}）；自动转小写
     * @param type 完整 Content-Type（如 {@code "text/html; charset=utf-8"}）
     */
    public static void register(String ext, String type) {
        if (ext == null || ext.isEmpty() || type == null || type.isEmpty()) return;
        MAP.put(ext.toLowerCase(), type);
    }

    /** 返回扩展名对应的 Content-Type；未知返回默认二进制流 */
    public static String forPath(String path) {
        int dot = path.lastIndexOf('.');
        if (dot >= 0 && dot < path.length() - 1) {
            String ext = path.substring(dot + 1).toLowerCase();
            String t = MAP.get(ext);
            if (t != null) return t;
        }
        return "application/octet-stream";
    }
}
