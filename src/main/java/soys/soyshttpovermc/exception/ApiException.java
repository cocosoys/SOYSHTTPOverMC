package soys.soyshttpovermc.exception;

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


    public ApiException(String code, String fmt, Object... args) {
        super(Module.API, code, fmt, args);
    }

    public ApiException(String fmt, Object... args) {
        super(Module.API, "API_ERR", fmt, args);
    }

    public ApiException(String code, String fmt, Throwable cause, Object... args) {
        super(Module.API, code, fmt, cause, args);
    }
}
