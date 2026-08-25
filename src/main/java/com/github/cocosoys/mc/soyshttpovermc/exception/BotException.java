package com.github.cocosoys.mc.soyshttpovermc.exception;

/** 能力组 6（Bot 管理）专用异常。 */
public class BotException extends SoysHttpException {

    public BotException(String code, String message) {
        super(Module.BOT, code, message);
    }

    public BotException(String code, String message, Throwable cause) {
        super(Module.BOT, code, message, cause);
    }

    public BotException(String message) {
        super(Module.BOT, "BOT_ERR", message);
    }

    public BotException(String message, Throwable cause) {
        super(Module.BOT, "BOT_ERR", message, cause);
    }


    /** i18n 无兜底版（兜底参数可省略）：{@code new BotException("BOT_ERR", "异常.key", v)}，未命中语言表时以 key 自身作模板回退填 {0}。 */
    public BotException(String code, String i18nKey, Object... args) {
        super(Module.BOT, code, i18nKey, args);
    }

    /** 默认错误码、i18n 无兜底版：{@code new BotException("异常.key", v)}。 */
    public BotException(String i18nKey, Object... args) {
        super(Module.BOT, "BOT_ERR", i18nKey, args);
    }

    /** i18n 无兜底 + 根因版：{@code new BotException("BOT_ERR", "异常.key", e, v)}。 */
    public BotException(String code, String i18nKey, Throwable cause, Object... args) {
        super(Module.BOT, code, i18nKey, cause, args);
    }

    /** i18n 显式兜底版：{@code new BotException("BOT_ERR", "异常.key", "兜底 {0}", v)}。 */
    public BotException(String code, String i18nKey, String fallback, Object... args) {
        super(Module.BOT, code, i18nKey, fallback, args);
    }

    /** i18n 显式兜底 + 根因版：{@code new BotException("BOT_ERR", "异常.key", "兜底 {0}", e, v)}。 */
    public BotException(String code, String i18nKey, String fallback, Throwable cause, Object... args) {
        super(Module.BOT, code, i18nKey, fallback, cause, args);
    }
}
