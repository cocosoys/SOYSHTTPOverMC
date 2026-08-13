package soys.soyshttpovermc.api.util;

import java.util.HashMap;

/**
 * 仿 RuoYi/Spring 的统一返回体：{@code {code, msg, data}}。
 * 注解式 API 处理器可直接返回本类；网关将其序列化为 JSON 响应。
 *
 * <pre>
 *   return AjaxResult.success();                    // {"code":200,"msg":"操作成功"}
 *   return AjaxResult.success(data);                // {"code":200,"msg":"操作成功","data":...}
 *   return AjaxResult.success("已更新", id);          // 自定义 msg
 *   return AjaxResult.error("服务器异常");             // {"code":500,"msg":"服务器异常"}
 *   return AjaxResult.unauthorized("未登录");          // {"code":401,...}
 *   return AjaxResult.forbidden("无权限");            // {"code":403,...}
 * </pre>
 */
public class AjaxResult extends HashMap<String, Object> {

    private static final long serialVersionUID = 1L;

    public static final int SUCCESS = 200;
    public static final int UNAUTHORIZED = 401;
    public static final int FORBIDDEN = 403;
    public static final int NOT_FOUND = 404;
    public static final int ERROR = 500;

    public AjaxResult() {
    }

    public AjaxResult(int code, String msg) {
        put("code", code);
        put("msg", msg);
    }

    public AjaxResult(int code, String msg, Object data) {
        put("code", code);
        put("msg", msg);
        put("data", data);
    }

    // ===== 成功 =====
    public static AjaxResult success() {
        return new AjaxResult(SUCCESS, "操作成功");
    }

    public static AjaxResult success(Object data) {
        return new AjaxResult(SUCCESS, "操作成功", data);
    }

    public static AjaxResult success(String msg, Object data) {
        return new AjaxResult(SUCCESS, msg, data);
    }

    // ===== 失败 =====
    public static AjaxResult error() {
        return new AjaxResult(ERROR, "操作失败");
    }

    public static AjaxResult error(String msg) {
        return new AjaxResult(ERROR, msg);
    }

    public static AjaxResult error(int code, String msg) {
        return new AjaxResult(code, msg);
    }

    // ===== 语义化 =====
    public static AjaxResult unauthorized(String msg) {
        return new AjaxResult(UNAUTHORIZED, msg == null ? "认证失败" : msg);
    }

    public static AjaxResult forbidden(String msg) {
        return new AjaxResult(FORBIDDEN, msg == null ? "没有访问权限" : msg);
    }

    public static AjaxResult notFound(String msg) {
        return new AjaxResult(NOT_FOUND, msg == null ? "资源不存在" : msg);
    }

    // ===== 访问器 =====
    public int getCode() {
        Object v = get("code");
        return v instanceof Number ? ((Number) v).intValue() : -1;
    }

    public String getMsg() {
        Object v = get("msg");
        return v == null ? "" : String.valueOf(v);
    }

    public Object getData() {
        return get("data");
    }

    /** 序列化为 JSON 字符串（HTTP 200 + body code 的 RuoYi 风格）。 */
    public String toJson() {
        return JsonWriter.write(this);
    }

    @Override
    public String toString() {
        return toJson();
    }
}
