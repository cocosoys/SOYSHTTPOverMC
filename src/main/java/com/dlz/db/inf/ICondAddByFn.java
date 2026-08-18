package com.dlz.db.inf;

import com.dlz.db.enums.DbOperateEnum;
import com.dlz.kit.fn.DlzFn;

import static com.dlz.db.enums.DbOperateEnum.*;

/**
 * 基于 Lambda 字段引用（{@link DlzFn}）的条件构造接口 —— <b>与具体 Bean 解绑</b> 版本。
 *
 * <p><b>与 {@link ICondAddByLamda} 的差异</b>：{@link ICondAddByLamda} 在接口层绑定了泛型 {@code T}
 * （代表整个构造器操作的 Bean 类型），所有 Lambda 必须来自同一个 Bean；本接口把 Lambda 的 Bean 类型
 * 提升到<b>方法级泛型 {@code <T1>}</b>，因此同一构造器里可以混用来自不同 Bean 的 Lambda 引用。
 *
 * <p>典型用途：{@link com.dlz.db.modal.para.AParaTable} 这类"面向参数集合、不绑定单一 Bean"的构造器。
 * 业务代码如果知道自己操作的是单一 Bean，优先使用 {@link ICondAddByLamda}。
 *
 * <p><b>设计约定</b>：方法签名、行为、命名与 {@link ICondAddByLamda} 完全一致，可直接参考：
 * <ul>
 *   <li>统一 {@code (boolean is, DlzFn<T1,?> column, Object value)} 三参形式，{@code is=false} 跳过；</li>
 *   <li>{@code column} 通过 SerializedLambda 提取属性名，按驼峰→下划线约定转为 DB 列名；</li>
 *   <li>BETWEEN 支持 (value1, value2) 与单值 "a1,a2"/JSON/数组/List 两种形式；</li>
 *   <li>IN 支持数组/Collection/逗号分隔字符串/{@code "sql:"} 子查询。</li>
 * </ul>
 *
 * <pre>
 * .eq(User::getStatus, 1)
 *  .gt(Order::getAmount, 100)            // 混用不同 Bean 的 Lambda
 *  .in(User::getId, "1,2,3")
 *  .between(Order::getCreateTime, start, end)
 * </pre>
 */
public interface ICondAddByFn<ME extends ICondAddByFn> extends ICondBase<ME> {

    // ========== BETWEEN / NOT BETWEEN ==========

    /**
     * {@code column BETWEEN value1 AND value2}。
     * <pre>.between(User::getAge, 18, 60)</pre>
     */
    default <T1> ME between(DlzFn<T1, ?> column, Object value1, Object value2) {
        addChildren(between.mk(column, new Object[]{value1, value2}, getTableName()));
        return me();
    }

    /** 动态条件版 {@link #between(DlzFn, Object, Object)}。 */
    default <T1> ME between(boolean is, DlzFn<T1, ?> column, Object value1, Object value2) {
        if (is) {
            addChildren(between.mk(column, new Object[]{value1, value2}, getTableName()));
        }
        return me();
    }

    /**
     * 单值形式的 BETWEEN。{@code value} 支持 "a1,a2" / JSON [a1,a2] / 数组 / {@link java.util.List}。
     * <pre>.between(User::getAge, "18,60")</pre>
     */
    default <T1> ME between(DlzFn<T1, ?> column, Object value) {
        addChildren(between.mk(column, value, getTableName()));
        return me();
    }

    /** 动态条件版 {@link #between(DlzFn, Object)}。 */
    default <T1> ME between(boolean is, DlzFn<T1, ?> column, Object value) {
        if (is) {
            addChildren(between.mk(column, value, getTableName()));
        }
        return me();
    }

    /** {@code column NOT BETWEEN value1 AND value2}。 */
    default <T1> ME notBetween(DlzFn<T1, ?> column, Object value1, Object value2) {
        addChildren(notBetween.mk(column, new Object[]{value1, value2}, getTableName()));
        return me();
    }

    /** 动态条件版 {@link #notBetween(DlzFn, Object, Object)}。 */
    default <T1> ME notBetween(boolean is, DlzFn<T1, ?> column, Object value1, Object value2) {
        if (is) {
            addChildren(notBetween.mk(column, new Object[]{value1, value2}, getTableName()));
        }
        return me();
    }

    /** 单值形式的 NOT BETWEEN，{@code value} 格式同 {@link #between(DlzFn, Object)}。 */
    default <T1> ME notBetween(DlzFn<T1, ?> column, Object value) {
        addChildren(notBetween.mk(column, value, getTableName()));
        return me();
    }

    /** 动态条件版 {@link #notBetween(DlzFn, Object)}。 */
    default <T1> ME notBetween(boolean is, DlzFn<T1, ?> column, Object value) {
        if (is) {
            addChildren(notBetween.mk(column, value, getTableName()));
        }
        return me();
    }

    // ========== IS NULL / IS NOT NULL ==========

    /** {@code column IS NOT NULL}。 */
    default <T1> ME isNotNull(DlzFn<T1, ?> column) {
        addChildren(isNotNull.mk(column, null, getTableName()));
        return me();
    }

    /** 动态条件版 {@link #isNotNull(DlzFn)}。 */
    default <T1> ME isNotNull(boolean is, DlzFn<T1, ?> column) {
        if (is) {
            addChildren(isNotNull.mk(column, null, getTableName()));
        }
        return me();
    }

    /** {@code column IS NULL}。 */
    default <T1> ME isNull(DlzFn<T1, ?> column) {
        addChildren(isNull.mk(column, null, getTableName()));
        return me();
    }

    /** 动态条件版 {@link #isNull(DlzFn)}。 */
    default <T1> ME isNull(boolean is, DlzFn<T1, ?> column) {
        if (is) {
            addChildren(isNull.mk(column, null, getTableName()));
        }
        return me();
    }

    // ========== EQ / NE ==========

    /**
     * {@code column = value}。
     * <pre>.eq(User::getStatus, 1)</pre>
     */
    default <T1> ME eq(DlzFn<T1, ?> column, Object value) {
        addChildren(eq.mk(column, value, getTableName()));
        return me();
    }

    /** 动态条件版 {@link #eq(DlzFn, Object)}。 */
    default <T1> ME eq(boolean is, DlzFn<T1, ?> column, Object value) {
        if (is) {
            addChildren(eq.mk(column, value, getTableName()));
        }
        return me();
    }

    /** {@code column <> value}。 */
    default <T1> ME ne(DlzFn<T1, ?> column, Object value) {
        addChildren(ne.mk(column, value, getTableName()));
        return me();
    }

    /** 动态条件版 {@link #ne(DlzFn, Object)}。 */
    default <T1> ME ne(boolean is, DlzFn<T1, ?> column, Object value) {
        if (is) {
            addChildren(ne.mk(column, value, getTableName()));
        }
        return me();
    }

    // ========== 大小比较 ==========

    /** {@code column > value}。 */
    default <T1> ME gt(DlzFn<T1, ?> column, Object value) {
        addChildren(gt.mk(column, value, getTableName()));
        return me();
    }

    /** 动态条件版 {@link #gt(DlzFn, Object)}。 */
    default <T1> ME gt(boolean is, DlzFn<T1, ?> column, Object value) {
        if (is) {
            addChildren(gt.mk(column, value, getTableName()));
        }
        return me();
    }

    /** {@code column >= value}。 */
    default <T1> ME ge(DlzFn<T1, ?> column, Object value) {
        addChildren(ge.mk(column, value, getTableName()));
        return me();
    }

    /** 动态条件版 {@link #ge(DlzFn, Object)}。 */
    default <T1> ME ge(boolean is, DlzFn<T1, ?> column, Object value) {
        if (is) {
            addChildren(ge.mk(column, value, getTableName()));
        }
        return me();
    }

    /** {@code column < value}。 */
    default <T1> ME lt(DlzFn<T1, ?> column, Object value) {
        addChildren(lt.mk(column, value, getTableName()));
        return me();
    }

    /** 动态条件版 {@link #lt(DlzFn, Object)}。 */
    default <T1> ME lt(boolean is, DlzFn<T1, ?> column, Object value) {
        if (is) {
            addChildren(lt.mk(column, value, getTableName()));
        }
        return me();
    }

    /** {@code column <= value}。 */
    default <T1> ME le(DlzFn<T1, ?> column, Object value) {
        addChildren(le.mk(column, value, getTableName()));
        return me();
    }

    /** 动态条件版 {@link #le(DlzFn, Object)}。 */
    default <T1> ME le(boolean is, DlzFn<T1, ?> column, Object value) {
        if (is) {
            addChildren(le.mk(column, value, getTableName()));
        }
        return me();
    }

    // ========== LIKE 系列 ==========

    /** {@code column LIKE '%value%'}（双侧模糊）。 */
    default <T1> ME like(DlzFn<T1, ?> column, Object value) {
        addChildren(like.mk(column, value, getTableName()));
        return me();
    }

    /** 动态条件版 {@link #like(DlzFn, Object)}。 */
    default <T1> ME like(boolean is, DlzFn<T1, ?> column, Object value) {
        if (is) {
            addChildren(like.mk(column, value, getTableName()));
        }
        return me();
    }

    /** {@code column LIKE 'value%'}（右模糊，通常可命中前缀索引）。 */
    default <T1> ME likeLeft(DlzFn<T1, ?> column, Object value) {
        addChildren(likeLeft.mk(column, value, getTableName()));
        return me();
    }

    /** 动态条件版 {@link #likeLeft(DlzFn, Object)}。 */
    default <T1> ME likeLeft(boolean is, DlzFn<T1, ?> column, Object value) {
        if (is) {
            addChildren(likeLeft.mk(column, value, getTableName()));
        }
        return me();
    }

    /** {@code column LIKE '%value'}（左模糊，通常无法命中索引）。 */
    default <T1> ME likeRight(DlzFn<T1, ?> column, Object value) {
        addChildren(likeRight.mk(column, value, getTableName()));
        return me();
    }

    /** 动态条件版 {@link #likeRight(DlzFn, Object)}。 */
    default <T1> ME likeRight(boolean is, DlzFn<T1, ?> column, Object value) {
        if (is) {
            addChildren(likeRight.mk(column, value, getTableName()));
        }
        return me();
    }

    /** {@code column NOT LIKE '%value%'}。 */
    default <T1> ME notLike(DlzFn<T1, ?> column, Object value) {
        addChildren(notLike.mk(column, value, getTableName()));
        return me();
    }

    /** 动态条件版 {@link #notLike(DlzFn, Object)}。 */
    default <T1> ME notLike(boolean is, DlzFn<T1, ?> column, Object value) {
        if (is) {
            addChildren(notLike.mk(column, value, getTableName()));
        }
        return me();
    }

    // ========== IN / NOT IN ==========

    /**
     * {@code column IN (...)}。{@code value} 可为：数组 / {@link java.util.Collection} / 逗号分隔字符串 / 以 {@code "sql:"} 开头的子查询。
     * <b>不可传单值</b>。
     * <pre>.in(User::getId, "1,2,3")</pre>
     */
    default <T1> ME in(DlzFn<T1, ?> column, Object value) {
        addChildren(in.mk(column, value, getTableName()));
        return me();
    }

    /** 动态条件版 {@link #in(DlzFn, Object)}。 */
    default <T1> ME in(boolean is, DlzFn<T1, ?> column, Object value) {
        if (is) {
            addChildren(in.mk(column, value, getTableName()));
        }
        return me();
    }

    /** {@code column NOT IN (...)}，{@code value} 格式同 {@link #in(DlzFn, Object)}。 */
    default <T1> ME notIn(DlzFn<T1, ?> column, Object value) {
        addChildren(notIn.mk(column, value, getTableName()));
        return me();
    }

    /** 动态条件版 {@link #notIn(DlzFn, Object)}。 */
    default <T1> ME notIn(boolean is, DlzFn<T1, ?> column, Object value) {
        if (is) {
            addChildren(notIn.mk(column, value, getTableName()));
        }
        return me();
    }

    // ========== 自定义操作符 ==========

    /**
     * 以自定义操作符添加条件。适用于上述枚举未覆盖的场景。
     * <pre>.op(User::getName, DbOprateEnum.eq, "admin")</pre>
     */
    default <T1> ME op(DlzFn<T1, ?> column, DbOperateEnum op, Object value) {
        addChildren(op.mk(column, value, getTableName()));
        return me();
    }
}
