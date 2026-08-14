package soys.soyshttpovermc.api.impl;

import soys.soyshttpovermc.api.ApiToolkitApi;
import soys.soyshttpovermc.exception.ExceptionBus;
import soys.soyshttpovermc.exception.ToolkitException;
import soys.soyshttpovermc.util.JsonWriter;
import soys.soyshttpovermc.web.MimeTypes;

/**
 * 能力组 4：工具（对象 → JSON、扩展名 → Content-Type）。
 * 由 {@link SoysHttpOverMcApiImpl} 组合并对外暴露；实现 {@link ApiToolkitApi}。
 */
public class ApiToolkitImpl implements ApiToolkitApi {

    @Override
    public String toJson(Object obj) {
        if (obj == null) return "null";
        try {
            return JsonWriter.write(obj);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new ToolkitException("E_TO_JSON", "对象序列化 JSON 失败: " + ex.getMessage(), ex));
        }
    }

    @Override
    public String guessContentType(String path) {
        try {
            return MimeTypes.forPath(path);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new ToolkitException("E_MIME", "推断 Content-Type 失败(path=" + path + "): " + ex.getMessage(), ex));
        }
    }
}
