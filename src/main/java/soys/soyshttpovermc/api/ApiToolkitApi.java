package soys.soyshttpovermc.api;

/**
 * 能力组 4：工具（对象 → JSON、扩展名 → Content-Type）。
 * 由 {@link SoysHttpOverMcApi#getToolkit()} 跳转获取。
 */
public interface ApiToolkitApi {

    /** 任意对象 → JSON 字符串（复用 JsonWriter） */
    String toJson(Object obj);

    /** 扩展名 → Content-Type（复用 MimeTypes） */
    String guessContentType(String path);
}
