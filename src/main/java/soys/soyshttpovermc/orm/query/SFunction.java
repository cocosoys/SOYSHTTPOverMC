package soys.soyshttpovermc.orm.query;

import java.io.Serializable;
import java.util.function.Function;

/**
 * 可序列化函数式接口（借鉴 dlz-db-core 的 DlzFn / MyBatis-Plus 的 SFunction）：
 * 用于 Lambda 条件（{@code eq(User::getName, "a")}），经 {@link LambdaUtils} 反序列化提取字段名，
 * 支持 IDE 自动补全与重构安全。
 */
@FunctionalInterface
public interface SFunction<T, R> extends Function<T, R>, Serializable {
}
