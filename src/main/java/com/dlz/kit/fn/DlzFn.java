package com.dlz.kit.fn;

import java.io.Serializable;
import java.util.function.Function;

/**
 * dlz-kit 最小子集（自建，供 dlz-db-core 移入编译）：可序列化函数式接口。
 * 用于 Lambda 条件（{@code User::getName}），经 FieldReflections.getFn 反序列化提取字段名。
 */
@FunctionalInterface
public interface DlzFn<T, R> extends Function<T, R>, Serializable {
}
