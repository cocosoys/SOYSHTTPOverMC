package com.github.cocosoys.mc.soyshttpovermc.exception;

/**
 * 能力组 7（HTTP 请求 / 本地回环调用）专用异常。
 */
public class HttpClientException extends SoysHttpException {

    public HttpClientException(String code, String message) {
        super(Module.HTTP, code, message);
    }

    public HttpClientException(String code, String message, Throwable cause) {
        super(Module.HTTP, code, message, cause);
    }

    public HttpClientException(String message) {
        super(Module.HTTP, "HTTP_ERR", message);
    }

    public HttpClientException(String message, Throwable cause) {
        super(Module.HTTP, "HTTP_ERR", message, cause);
    }


    /**
     * i18n 无兜底版（兜底参数可省略）：{@code new HttpClientException("HTTP_ERR", "异常.key", v)}，未命中语言表时以 key 自身作模板回退填 {0}。
     */
    public HttpClientException(String code, String i18nKey, Object... args) {
        super(Module.HTTP, code, i18nKey, args);
    }

    /**
     * 默认错误码、i18n 无兜底版：{@code new HttpClientException("异常.key", v)}。
     */
    public HttpClientException(String i18nKey, Object... args) {
        super(Module.HTTP, "HTTP_ERR", i18nKey, args);
    }

    /**
     * i18n 无兜底 + 根因版：{@code new HttpClientException("HTTP_ERR", "异常.key", e, v)}。
     */
    public HttpClientException(String code, String i18nKey, Throwable cause, Object... args) {
        super(Module.HTTP, code, i18nKey, cause, args);
    }

    /**
     * i18n 显式兜底版：{@code new HttpClientException("HTTP_ERR", "异常.key", "兜底 {0}", v)}。
     */
    public HttpClientException(String code, String i18nKey, String fallback, Object... args) {
        super(Module.HTTP, code, i18nKey, fallback, args);
    }

    /**
     * i18n 显式兜底 + 根因版：{@code new HttpClientException("HTTP_ERR", "异常.key", "兜底 {0}", e, v)}。
     */
    public HttpClientException(String code, String i18nKey, String fallback, Throwable cause, Object... args) {
        super(Module.HTTP, code, i18nKey, fallback, cause, args);
    }
}
