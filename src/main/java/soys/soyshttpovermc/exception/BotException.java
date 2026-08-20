package soys.soyshttpovermc.exception;

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


    public BotException(String code, String fmt, Object... args) {
        super(Module.BOT, code, fmt, args);
    }

    public BotException(String fmt, Object... args) {
        super(Module.BOT, "BOT_ERR", fmt, args);
    }

    public BotException(String code, String fmt, Throwable cause, Object... args) {
        super(Module.BOT, code, fmt, cause, args);
    }
}
