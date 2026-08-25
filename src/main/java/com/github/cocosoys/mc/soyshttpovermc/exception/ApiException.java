package com.github.cocosoys.mc.soyshttpovermc.exception;

/** 能力组 1（注解式 API 注册）专用异常。 */
public class ApiException extends SoysHttpException {

    public ApiException(String code, String message) {
        super(Module.API, code, message);
    }

    public ApiException(String code, String message, Throwable cause) {
        super(Module.API, code, message, cause);
    }

    public ApiException(String message) {
        super(Module.API, "API_ERR", message);
    }

    public ApiException(String message, Throwable cause) {
        super(Module.API, "API_ERR", message, cause);
    }


    /** i18n 无兜底版（兜底参数可省略）：{@code new ApiException("API_ERR", "异常.key", v)}，未命中语言表时以 key 自身作模板回退填 {0}。 */
    public ApiException(String code, String i18nKey, Object... args) {
        super(Module.API, code, i18nKey, args);
    }

    /** 默认错误码、i18n 无兜底版：{@code new ApiException("异常.key", v)}。 */
    public ApiException(String i18nKey, Object... args) {
        super(Module.API, "API_ERR", i18nKey, args);
    }

    /** i18n 无兜底 + 根因版：{@code new ApiException("API_ERR", "异常.key", e, v)}。 */
    public ApiException(String code, String i18nKey, Throwable cause, Object... args) {
        super(Module.API, code, i18nKey, cause, args);
    }

    /** i18n 显式兜底版：{@code new ApiException("API_ERR", "异常.key", "兜底 {0}", v)}。 */
    public ApiException(String code, String i18nKey, String fallback, Object... args) {
        super(Module.API, code, i18nKey, fallback, args);
    }

    /** i18n 显式兜底 + 根因版：{@code new ApiException("API_ERR", "异常.key", "兜底 {0}", e, v)}。 */
    public ApiException(String code, String i18nKey, String fallback, Throwable cause, Object... args) {
        super(Module.API, code, i18nKey, fallback, cause, args);
    }
}
