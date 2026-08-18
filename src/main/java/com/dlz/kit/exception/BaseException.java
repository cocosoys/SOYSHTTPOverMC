package com.dlz.kit.exception;

/**
 * dlz-kit 最小子集：业务异常基类。
 */
public class BaseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private int code = -1;

    public BaseException(String message) {
        super(message);
    }

    public BaseException(String message, Throwable cause) {
        super(message, cause);
    }

    public BaseException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BaseException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
