package com.dlz.kit.util;

/**
 * dlz-kit 最小子集：二元组（SqlUtil 翻页 SQL 结果 {@code VAL<sql, params>} 使用）。
 */
public class VAL<K, V> {

    public final K v1;
    public final V v2;

    public VAL(K v1, V v2) {
        this.v1 = v1;
        this.v2 = v2;
    }

    public static <K, V> VAL<K, V> of(K k, V v) {
        return new VAL<>(k, v);
    }
}
