package soys.soyshttpovermc.exception;

/** 未知/未归类异常：用于把非本体系异常（普通 RuntimeException/Exception）包进总线时落点（模块 UNKNOWN）。 */
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
}
