package soys.soyshttpovermc.exception;

/** 能力组 7（HTTP 请求 / 本地回环调用）专用异常。 */
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


    public HttpClientException(String code, String fmt, Object... args) {
        super(Module.HTTP, code, fmt, args);
    }

    public HttpClientException(String fmt, Object... args) {
        super(Module.HTTP, "HTTP_ERR", fmt, args);
    }

    public HttpClientException(String code, String fmt, Throwable cause, Object... args) {
        super(Module.HTTP, code, fmt, cause, args);
    }
}
