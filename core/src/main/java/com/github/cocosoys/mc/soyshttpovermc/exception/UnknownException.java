package com.github.cocosoys.mc.soyshttpovermc.exception;

/**
 * 未知/未归类异常：用于把非本体系异常（普通 RuntimeException/Exception）包进总线时落点（模块 UNKNOWN）。
 */
public class UnknownException extends SoysHttpException {

    public UnknownException(String code, String message) {
        super(Module.UNKNOWN, code, message);
    }

    public UnknownException(String code, String message, Throwable cause) {
        super(Module.UNKNOWN, code, message, cause);
    }

    public UnknownException(String message) {
        super(Module.UNKNOWN, "E_UNKNOWN", message);
    }

    public UnknownException(String message, Throwable cause) {
        super(Module.UNKNOWN, "E_UNKNOWN", message, cause);
    }


    /**
     * i18n 无兜底版（兜底参数可省略）：{@code new UnknownException("E_UNKNOWN", "异常.key", v)}，未命中语言表时以 key 自身作模板回退填 {0}。
     */
    public UnknownException(String code, String i18nKey, Object... args) {
        super(Module.UNKNOWN, code, i18nKey, args);
    }

    /**
     * 默认错误码、i18n 无兜底版：{@code new UnknownException("异常.key", v)}。
     */
    public UnknownException(String i18nKey, Object... args) {
        super(Module.UNKNOWN, "E_UNKNOWN", i18nKey, args);
    }

    /**
     * i18n 无兜底 + 根因版：{@code new UnknownException("E_UNKNOWN", "异常.key", e, v)}。
     */
    public UnknownException(String code, String i18nKey, Throwable cause, Object... args) {
        super(Module.UNKNOWN, code, i18nKey, cause, args);
    }

    /**
     * i18n 显式兜底版：{@code new UnknownException("E_UNKNOWN", "异常.key", "兜底 {0}", v)}。
     */
    public UnknownException(String code, String i18nKey, String fallback, Object... args) {
        super(Module.UNKNOWN, code, i18nKey, fallback, args);
    }

    /**
     * i18n 显式兜底 + 根因版：{@code new UnknownException("E_UNKNOWN", "异常.key", "兜底 {0}", e, v)}。
     */
    public UnknownException(String code, String i18nKey, String fallback, Throwable cause, Object... args) {
        super(Module.UNKNOWN, code, i18nKey, fallback, cause, args);
    }
}
