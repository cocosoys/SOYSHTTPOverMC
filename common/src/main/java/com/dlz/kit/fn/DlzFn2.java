package com.dlz.kit.fn;

import java.io.Serializable;

/**
 * dlz-kit 最小子集：双参函数式接口（DbLogUtil.logInfo 等使用）。
 */
@FunctionalInterface
public interface DlzFn2<T1, T2, R> extends Serializable {

    R apply(T1 t1, T2 t2);
}
