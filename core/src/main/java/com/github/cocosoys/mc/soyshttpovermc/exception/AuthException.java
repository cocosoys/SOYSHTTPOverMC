package com.github.cocosoys.mc.soyshttpovermc.exception;

/**
 * 能力组 3（鉴权与凭证）专用异常。
 */
public class AuthException extends SoysHttpException {

    public AuthException(String code, String message) {
        super(Module.AUTH, code, message);
    }

    public AuthException(String code, String message, Throwable cause) {
        super(Module.AUTH, code, message, cause);
    }

    public AuthException(String message) {
        super(Module.AUTH, "AUTH_ERR", message);
    }

    public AuthException(String message, Throwable cause) {
        super(Module.AUTH, "AUTH_ERR", message, cause);
    }

    /**
     * i18n 无兜底版（兜底参数可省略）：{@code new AuthException("AUTH_ERR", "异常.key", v)}，未命中语言表时以 key 自身作模板回退填 {0}。
     */
    public AuthException(String code, String i18nKey, Object... args) {
        super(Module.AUTH, code, i18nKey, args);
    }

    /**
     * 默认错误码、i18n 无兜底版：{@code new AuthException("异常.key", v)}。
     */
    public AuthException(String i18nKey, Object... args) {
        super(Module.AUTH, "AUTH_ERR", i18nKey, args);
    }

    /**
     * i18n 无兜底 + 根因版：{@code new AuthException("AUTH_ERR", "异常.key", e, v)}。
     */
    public AuthException(String code, String i18nKey, Throwable cause, Object... args) {
        super(Module.AUTH, code, i18nKey, cause, args);
    }

    /**
     * i18n 显式兜底版：{@code new AuthException("AUTH_ERR", "异常.key", "兜底 {0}", v)}。
     */
    public AuthException(String code, String i18nKey, String fallback, Object... args) {
        super(Module.AUTH, code, i18nKey, fallback, args);
    }

    /**
     * i18n 显式兜底 + 根因版：{@code new AuthException("AUTH_ERR", "异常.key", "兜底 {0}", e, v)}。
     */
    public AuthException(String code, String i18nKey, String fallback, Throwable cause, Object... args) {
        super(Module.AUTH, code, i18nKey, fallback, cause, args);
    }
}
