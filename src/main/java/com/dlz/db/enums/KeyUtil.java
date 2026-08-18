package com.dlz.db.enums;

import java.util.concurrent.atomic.AtomicInteger;

class KeyUtil {

    private static final ThreadLocal<AtomicInteger> paraNameIndex = ThreadLocal.withInitial(AtomicInteger::new);

    /**
     * 增加当前线程的计数器值（默认增加1）
     *
     * @return 增加后的值
     */
    static String getKeyName(String prefix) {
        return prefix+paraNameIndex.get().addAndGet(1);
    }
}
