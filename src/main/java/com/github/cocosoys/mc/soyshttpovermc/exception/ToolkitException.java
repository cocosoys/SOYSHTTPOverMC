package com.github.cocosoys.mc.soyshttpovermc.exception;

/**
 * 能力组 4（工具：JSON / Content-Type）专用异常。
 */
public class ToolkitException extends SoysHttpException {

    public ToolkitException(String code, String message) {
        super(Module.TOOLKIT, code, message);
    }

    public ToolkitException(String code, String message, Throwable cause) {
        super(Module.TOOLKIT, code, message, cause);
    }

    public ToolkitException(String message) {
        super(Module.TOOLKIT, "TOOLKIT_ERR", message);
    }

    public ToolkitException(String message, Throwable cause) {
        super(Module.TOOLKIT, "TOOLKIT_ERR", message, cause);
    }


    /**
     * i18n 无兜底版（兜底参数可省略）：{@code new ToolkitException("TOOLKIT_ERR", "异常.key", v)}，未命中语言表时以 key 自身作模板回退填 {0}。
     */
    public ToolkitException(String code, String i18nKey, Object... args) {
        super(Module.TOOLKIT, code, i18nKey, args);
    }

    /**
     * 默认错误码、i18n 无兜底版：{@code new ToolkitException("异常.key", v)}。
     */
    public ToolkitException(String i18nKey, Object... args) {
        super(Module.TOOLKIT, "TOOLKIT_ERR", i18nKey, args);
    }

    /**
     * i18n 无兜底 + 根因版：{@code new ToolkitException("TOOLKIT_ERR", "异常.key", e, v)}。
     */
    public ToolkitException(String code, String i18nKey, Throwable cause, Object... args) {
        super(Module.TOOLKIT, code, i18nKey, cause, args);
    }

    /**
     * i18n 显式兜底版：{@code new ToolkitException("TOOLKIT_ERR", "异常.key", "兜底 {0}", v)}。
     */
    public ToolkitException(String code, String i18nKey, String fallback, Object... args) {
        super(Module.TOOLKIT, code, i18nKey, fallback, args);
    }

    /**
     * i18n 显式兜底 + 根因版：{@code new ToolkitException("TOOLKIT_ERR", "异常.key", "兜底 {0}", e, v)}。
     */
    public ToolkitException(String code, String i18nKey, String fallback, Throwable cause, Object... args) {
        super(Module.TOOLKIT, code, i18nKey, fallback, cause, args);
    }
}
