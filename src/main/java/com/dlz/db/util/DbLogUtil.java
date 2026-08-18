package com.dlz.db.util;

import com.dlz.db.core.DlzDbProperties;
import com.dlz.db.modal.DB;
import com.dlz.kit.fn.DlzFn2;
import com.dlz.kit.util.ExceptionUtils;
import com.dlz.kit.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class DbLogUtil {
    private final static String KEY_CALLER = "caller";
    private static boolean showCaller = false;
    private static long slowSqlThreshold = 0;
    private static boolean showRunSql = false;
    private static boolean showResult = false;

    public static void init(DlzDbProperties properties) {
        showCaller = properties.getLog().isShowCaller();
        showRunSql = properties.getLog().isShowRunSql();
        showResult = properties.getLog().isShowResult();
        slowSqlThreshold = properties.getLog().getSlowSqlThreshold();
    }

    private DbLogUtil() {
    }

    public static String setCaller(int level) {
        String caller = MDC.get(KEY_CALLER);
        if (caller == null) {
            caller = getTraceCaller(level + 1);
            MDC.put(KEY_CALLER, caller);
        }
        return caller;
    }

    public static void clearCaller() {
        MDC.remove(KEY_CALLER);
    }

    /**
     * 取得调用者
     */
    public static String getTraceCaller(final int level) {
        StackTraceElement[] trace = new Throwable().getStackTrace();
        int index = level;
        if (index < 1) {
            index = 1;
        }
        String traceInfo;
        List<String> frw_trace = new ArrayList<>();
        while (true) {
            if (index > trace.length - 1) {
                return null;
            }
            traceInfo = trace[index].toString();

            // 检查是否应该跳过
            boolean shouldSkip = traceInfo.contains("CGLIB$") ||
                    traceInfo.contains("lambda$") ||
                    traceInfo.startsWith("sun.") ||
                    traceInfo.startsWith("java.") ||
                    traceInfo.startsWith("org.") ||
                    traceInfo.startsWith("com.intellij.junit5");

            // 检查是否是框架代码
            boolean isFrameworkCode = traceInfo.startsWith("com.dlz.spring.") ||
                    traceInfo.startsWith("com.dlz.kit.") ||
                    traceInfo.startsWith("com.dlz.db.");

            if (shouldSkip) {
                index++;
            } else if (isFrameworkCode) {
                index++;
                if (log.isTraceEnabled()) {
                    frw_trace.add(traceInfo.replaceAll(".*\\((.*)\\)", "$1").replaceAll("\\.java", ""));
                }
            } else {
                // 找到目标，退出循环
                break;
            }
        }
        if (!frw_trace.isEmpty()) {
            log.trace("< {}", frw_trace.stream().collect(Collectors.joining(" < ")));
        }
        return traceInfo.replaceAll(".*\\((.*)\\)", " caller:($1)");
    }

    public static <T> String generateSqlMessage(Long l, T reulst, String methodName, String sql, Object[] args) {
        final String usedDataSourceName = DB.Dynamic.getUsedDataSourceName();
        if (usedDataSourceName != null) {
            methodName = "[" + usedDataSourceName + "] " + methodName;
        }
        String sqlMessage = showRunSql ?
                StringUtils.formatMsg("{} {}ms sql:{}", methodName, l, SqlUtil.getRunSqlByJdbc(sql, args)) :
                StringUtils.formatMsg("{} {}ms sql:{} {}", methodName, l, sql, args);
        if (showResult && reulst != null) {
            sqlMessage += StringUtils.formatMsg("\nresult:{}", reulst);
        }
        return sqlMessage;
    }

    public static String generateSqlMessage(Long l, String methodName, String sql, List<Object[]> batchArgs) {
        return StringUtils.formatMsg("{} {}ms sql:{} size:{}", methodName, l, sql, batchArgs.size());
    }

    public static <T> void logInfo(DlzFn2<Long, T, String> msg, Long t, T result, Exception error) {
        if (log.isInfoEnabled() || error != null) {
            if (showCaller) {
                DbLogUtil.setCaller(1);
            }
            final long l = System.currentTimeMillis() - t;
            try {
                if (error != null) {
                    log.error(ExceptionUtils.getStackTrace(error));
                    log.error(msg.apply(l, result));
                } else {
                    if (slowSqlThreshold > 0 && l > slowSqlThreshold) {
                        log.warn(msg.apply(l, result));
                    } else {
                        log.info(msg.apply(l, result));
                    }
                }
            } finally {
                if (showCaller) {
                    DbLogUtil.clearCaller();
                }
            }
        }
    }

    public static void warn(String msg, Exception error) {
        if (log.isWarnEnabled() || error != null) {
            if (showCaller) {
                DbLogUtil.setCaller(1);
            }
            try {
                if (error != null) {
                    log.warn(ExceptionUtils.getStackTrace(error));
                }
                log.warn(msg);
            } finally {
                if (showCaller) {
                    DbLogUtil.clearCaller();
                }
            }
        }
    }
    public static void debug(String msg, Exception error) {
        if (log.isDebugEnabled() || error != null) {
            if (showCaller) {
                DbLogUtil.setCaller(1);
            }
            try {
                if (error != null) {
                    log.debug(ExceptionUtils.getStackTrace(error));
                }
                log.debug(msg);
            } finally {
                if (showCaller) {
                    DbLogUtil.clearCaller();
                }
            }
        }
    }
}
