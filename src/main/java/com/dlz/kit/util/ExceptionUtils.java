package com.dlz.kit.util;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * dlz-kit 最小子集：异常工具。
 */
public final class ExceptionUtils {

    private ExceptionUtils() {
    }

    /** 异常堆栈转字符串。 */
    public static String getStackTrace(Throwable t) {
        if (t == null) return "";
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    /** 前缀消息 + 异常堆栈（SqlHolder 日志使用）。 */
    public static String getStackTrace(String msg, Throwable t) {
        return msg + "\n" + getStackTrace(t);
    }
}
