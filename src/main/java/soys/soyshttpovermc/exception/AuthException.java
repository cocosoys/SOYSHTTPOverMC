package soys.soyshttpovermc.exception;

/** 能力组 3（鉴权与凭证）专用异常。 */
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

    public AuthException(String code, String fmt, Object... args) {
        super(Module.AUTH, code, fmt, args);
    }

    public AuthException(String fmt, Object... args) {
        super(Module.AUTH, "AUTH_ERR", fmt, args);
    }

    public AuthException(String code, String fmt, Throwable cause, Object... args) {
        super(Module.AUTH, code, fmt, cause, args);
    }
}
