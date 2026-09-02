package com.dlz.kit.exception;

/**
 * dlz-kit 最小子集：系统异常。
 */
public class SystemException extends BaseException {

    private static final long serialVersionUID = 1L;

    public SystemException(String message) {
        super(message);
    }

    public SystemException(String message, Throwable cause) {
        super(message, cause);
    }
}
