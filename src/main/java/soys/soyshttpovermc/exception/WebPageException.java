package soys.soyshttpovermc.exception;

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


    public WebPageException(String code, String fmt, Object... args) {
        super(Module.WEB, code, fmt, args);
    }

    public WebPageException(String fmt, Object... args) {
        super(Module.WEB, "WEB_ERR", fmt, args);
    }

    public WebPageException(String code, String fmt, Throwable cause, Object... args) {
        super(Module.WEB, code, fmt, cause, args);
    }
}
