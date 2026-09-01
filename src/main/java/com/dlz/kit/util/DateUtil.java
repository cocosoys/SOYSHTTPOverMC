package com.dlz.kit.util;

import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * dlz-kit 最小子集：日期工具（SqlUtil 生成可运行 SQL 时的日期格式化）。
 * DATETIME 为线程安全的 DateFormat（内部 ThreadLocal SimpleDateFormat）。
 */
public final class DateUtil {

    private DateUtil() {
    }

    /**
     * 线程安全的 yyyy-MM-dd HH:mm:ss 格式化器。
     */
    public static final DateFormat DATETIME = new DateFormat() {
        private static final long serialVersionUID = 1L;
        private final ThreadLocal<SimpleDateFormat> holder = ThreadLocal.withInitial(
                () -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));

        @Override
        public StringBuffer format(Date date, StringBuffer toAppendTo, FieldPosition fieldPosition) {
            return holder.get().format(date, toAppendTo, fieldPosition);
        }

        @Override
        public Date parse(String source, ParsePosition pos) {
            return holder.get().parse(source, pos);
        }
    };
}
