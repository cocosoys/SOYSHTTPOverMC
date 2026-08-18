package com.dlz.db.modal.dto;

import com.dlz.db.support.PojoCache;
import com.dlz.kit.fn.DlzFn;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Arrays;

/**
 * 排序元素载体
 *
 * @author HCL
 * Create at 2019/5/27
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Order implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 需要进行排序的字段
     */
    private String column;
    /**
     * 是否正序排列，默认 true
     */
    private boolean asc = true;
    public static Order build(String column, boolean asc) {
        return new Order(column, asc);
    }
    public static Order build(String column, String sort) {
        return new Order(column, "asc".equalsIgnoreCase(sort));
    }
    public static Order buildWithSql(String sql) {
        String[] split = sql.split(" ");
        return build(split[0], split[1]);
    }
    public static Order asc(String column) {
        return build(column, true);
    }
    public static <T> Order asc(DlzFn<T, ?> column) {
        return build(PojoCache.fnName(column), true);
    }
    public static Order desc(String column) {
        return build(column, false);
    }
    public static <T> Order desc(DlzFn<T, ?> column) {
        return build(PojoCache.fnName(column), false);
    }
    public static Order[] ascs(String... columns) {
        return Arrays.stream(columns).map(Order::asc).toArray(Order[]::new);
    }
    @SafeVarargs
    @SuppressWarnings("unchecked")
    public static <T> Order[] ascs(DlzFn<T, ?>... columns) {
        return Arrays.stream(columns).map(Order::asc).toArray(Order[]::new);
    }
    public static Order[] descs(String... columns) {
        return Arrays.stream(columns).map(Order::desc).toArray(Order[]::new);
    }
    @SafeVarargs
    @SuppressWarnings("unchecked")
    public static <T1> Order[] descs(DlzFn<T1, ?>... columns) {
        return Arrays.stream(columns).map(Order::desc).toArray(Order[]::new);
    }
    public static Order[] buildWithSqls(String columns) {
        return Arrays.stream(columns.split(",")).map(Order::buildWithSql).toArray(Order[]::new);
    }
}
