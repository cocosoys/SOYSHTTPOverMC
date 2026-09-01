package com.github.cocosoys.mc.soyshttpovermc.util;

import com.github.cocosoys.mc.soyshttpovermc.i18n.I18n;

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

    /**
     * 格式化成功消息（String.format 风格），如 {@code successf("已更新 %s", id)}。
     */
    public static AjaxResult successf(String fmt, Object... args) {
        return new AjaxResult(SUCCESS, String.format(fmt, args));
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

    /**
     * 格式化失败消息，如 {@code errorf("缺少参数 %s", name)}。
     */
    public static AjaxResult errorf(String fmt, Object... args) {
        return new AjaxResult(ERROR, String.format(fmt, args));
    }

    /**
     * 格式化指定码失败消息，如 {@code errorf(400, "参数 %s 不合法: %s", name, value)}。
     */
    public static AjaxResult errorf(int code, String fmt, Object... args) {
        return new AjaxResult(code, String.format(fmt, args));
    }

    // ===== 语义化 =====
    public static AjaxResult unauthorized(String msg) {
        return new AjaxResult(UNAUTHORIZED, msg == null ? "认证失败" : msg);
    }

    /**
     * 格式化认证失败消息，如 {@code unauthorizedf("凭据无效: %s", token)}。
     */
    public static AjaxResult unauthorizedf(String fmt, Object... args) {
        return new AjaxResult(UNAUTHORIZED, String.format(fmt, args));
    }

    public static AjaxResult forbidden(String msg) {
        return new AjaxResult(FORBIDDEN, msg == null ? "没有访问权限" : msg);
    }

    /**
     * 格式化无权限消息，如 {@code forbiddenf("API %s 需要 %s", api, perm)}。
     */
    public static AjaxResult forbiddenf(String fmt, Object... args) {
        return new AjaxResult(FORBIDDEN, String.format(fmt, args));
    }

    public static AjaxResult notFound(String msg) {
        return new AjaxResult(NOT_FOUND, msg == null ? "资源不存在" : msg);
    }

    /**
     * 格式化资源不存在消息，如 {@code notFoundf("资源 %s 不存在", path)}。
     */
    public static AjaxResult notFoundf(String fmt, Object... args) {
        return new AjaxResult(NOT_FOUND, String.format(fmt, args));
    }

    // ===== i18n 版（*T：key 首参，命中语言表翻译，未命中回退 fallback）=====

    /**
     * i18n 成功消息：{@code successT("ajax.x.key", "兜底 {0}", v)}。
     */
    public static AjaxResult successT(String i18nKey, String fallback, Object... args) {
        return new AjaxResult(SUCCESS, I18n.resolve(i18nKey, fallback, args));
    }

    /**
     * i18n 成功消息 + data 字段：{@code successDataT(data, "ajax.x.key", "兜底 {0}", v)}。
     */
    public static AjaxResult successDataT(Object data, String i18nKey, String fallback, Object... args) {
        return new AjaxResult(SUCCESS, I18n.resolve(i18nKey, fallback, args), data);
    }

    /**
     * i18n 失败消息：{@code errorT("ajax.x.key", "兜底 {0}", v)}。
     */
    public static AjaxResult errorT(String i18nKey, String fallback, Object... args) {
        return new AjaxResult(ERROR, I18n.resolve(i18nKey, fallback, args));
    }

    /**
     * i18n 指定码失败消息：{@code errorT(400, "ajax.x.key", "兜底 {0}", v)}。
     */
    public static AjaxResult errorT(int code, String i18nKey, String fallback, Object... args) {
        return new AjaxResult(code, I18n.resolve(i18nKey, fallback, args));
    }

    /**
     * i18n 认证失败消息：{@code unauthorizedT("ajax.auth.key", "兜底 {0}", v)}。
     */
    public static AjaxResult unauthorizedT(String i18nKey, String fallback, Object... args) {
        return new AjaxResult(UNAUTHORIZED, I18n.resolve(i18nKey, fallback, args));
    }

    /**
     * i18n 无权限消息：{@code forbiddenT("ajax.perm.key", "兜底 {0}", v)}。
     */
    public static AjaxResult forbiddenT(String i18nKey, String fallback, Object... args) {
        return new AjaxResult(FORBIDDEN, I18n.resolve(i18nKey, fallback, args));
    }

    /**
     * i18n 资源不存在消息：{@code notFoundT("ajax.x.key", "兜底 {0}", v)}。
     */
    public static AjaxResult notFoundT(String i18nKey, String fallback, Object... args) {
        return new AjaxResult(NOT_FOUND, I18n.resolve(i18nKey, fallback, args));
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

    /**
     * 序列化为 JSON 字符串（HTTP 200 + body code 的 RuoYi 风格）。
     */
    public String toJson() {
        return JsonWriter.write(this);
    }

    @Override
    public String toString() {
        return toJson();
    }
}
