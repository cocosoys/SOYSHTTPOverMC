package com.github.cocosoys.mc.soyshttpovermc.exception;

/** 能力组 2（网页登记）专用异常。 */
public class WebPageException extends SoysHttpException {

    public WebPageException(String code, String message) {
        super(Module.WEB, code, message);
    }

    public WebPageException(String code, String message, Throwable cause) {
        super(Module.WEB, code, message, cause);
    }

    public WebPageException(String message) {
        super(Module.WEB, "WEB_ERR", message);
    }

    public WebPageException(String message, Throwable cause) {
        super(Module.WEB, "WEB_ERR", message, cause);
    }


    /** i18n 无兜底版（兜底参数可省略）：{@code new WebPageException("WEB_ERR", "异常.key", v)}，未命中语言表时以 key 自身作模板回退填 {0}。 */
    public WebPageException(String code, String i18nKey, Object... args) {
        super(Module.WEB, code, i18nKey, args);
    }

    /** 默认错误码、i18n 无兜底版：{@code new WebPageException("异常.key", v)}。 */
    public WebPageException(String i18nKey, Object... args) {
        super(Module.WEB, "WEB_ERR", i18nKey, args);
    }

    /** i18n 无兜底 + 根因版：{@code new WebPageException("WEB_ERR", "异常.key", e, v)}。 */
    public WebPageException(String code, String i18nKey, Throwable cause, Object... args) {
        super(Module.WEB, code, i18nKey, cause, args);
    }

    /** i18n 显式兜底版：{@code new WebPageException("WEB_ERR", "异常.key", "兜底 {0}", v)}。 */
    public WebPageException(String code, String i18nKey, String fallback, Object... args) {
        super(Module.WEB, code, i18nKey, fallback, args);
    }

    /** i18n 显式兜底 + 根因版：{@code new WebPageException("WEB_ERR", "异常.key", "兜底 {0}", e, v)}。 */
    public WebPageException(String code, String i18nKey, String fallback, Throwable cause, Object... args) {
        super(Module.WEB, code, i18nKey, fallback, cause, args);
    }
}
