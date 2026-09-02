package com.dlz.kit.exception;

/**
 * dlz-kit 最小子集：错误码注册表（DbException 静态注册业务错误码）。
 */
public final class ExceptionErrors {

    private static final java.util.Map<Integer, String> ERRORS = new java.util.concurrent.ConcurrentHashMap<>();

    private ExceptionErrors() {
    }

    public static void addErrors(int code, String message) {
        ERRORS.put(code, message);
    }

    public static String getMessage(int code) {
        return ERRORS.getOrDefault(code, "未知错误");
    }
}
