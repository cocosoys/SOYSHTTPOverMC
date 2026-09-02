package com.dlz.kit.exception;

/**
 * dlz-kit 最小子集：参数校验异常。
 */
public class ValidateException extends BaseException {

    private static final long serialVersionUID = 1L;

    public ValidateException(String message) {
        super(message);
    }

    public ValidateException(String message, Throwable cause) {
        super(message, cause);
    }
}
