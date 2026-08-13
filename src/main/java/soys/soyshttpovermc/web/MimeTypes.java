package soys.soyshttpovermc.web;

import java.util.HashMap;
import java.util.Map;

/**
 * 扩展名 → HTTP Content-Type 映射。前端资源若 Content-Type 不对，浏览器不渲染 JS/CSS/图片。
 */
public final class MimeTypes {

    private static final Map<String, String> MAP = new HashMap<>();

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
