package com.dlz.db.inf;

import com.dlz.db.enums.DbOperateEnum;
import com.dlz.kit.fn.DlzFn;

import static com.dlz.db.enums.DbOperateEnum.*;

/**
 * 基于 Lambda 字段引用（{@link DlzFn}）的条件构造接口。
 *
 * <p><b>设计约定</b>：所有条件方法统一签名 <b>{@code (boolean is, DlzFn<T,?> column, Object value)}</b>，
 * 其中 {@code is} 为 false 时整条条件跳过——这是动态条件的首选表达方式；
 * 省略 {@code is} 的重载等价于 {@code is=true}。
 *
 * <pre>
 * // 显式开关（动态条件首选）
 * .eq(user.getId() != null, User::getId, user.getId())
 *
 * // 默认开启
 * .gt(User::getAge, 18)
 * </pre>
 *
 * <p><b>列名来源</b>：{@code column} 是类型安全的方法引用（如 {@code User::getName}），
 * 框架在运行时通过 SerializedLambda 提取属性名并按约定转为 DB 列名（驼峰→下划线）。
 * 若需直接传字符串列名，请使用 {@link ICondAddByKey}。
 *
 * <p><b>命名速查</b>：
 * <pre>
 *   eq / ne                     =  / <>
 *   gt / ge / lt / le           > / >= / < / <=
 *   like / likeLeft / likeRight / notLike           LIKE '%v%' / 'v%' / '%v' / NOT LIKE
 *   in / notIn                     IN / NOT IN
 *   between / notBetween                     BETWEEN / NOT BETWEEN
 *   isNull / isNotNull                  IS NULL / IS NOT NULL
 *   op                          自定义操作符（{@link DbOperateEnum}）
 * </pre>
 *
 * @param <ME> 链式返回类型（子类自身，便于 fluent 风格）
 * @param <T>  条件所针对的 Bean 类型
 */
public interface ICondAddByLamda<ME extends ICondAddByLamda, T> extends ICondBase<ME> {

    // ========== BETWEEN / NOT BETWEEN ==========

    /**
     * {@code BETWEEN value1 AND value2}。
     * <pre>.between(User::getAge, 18, 60)   // age BETWEEN 18 AND 60</pre>
     */
    default ME between(DlzFn<T, ?> column, Object value1, Object value2) {
        addChildren(between.mk(column, new Object[]{value1, value2}, getTableName()));
        return me();
    }

    /**
     * 动态条件版 {@link #between(DlzFn, Object, Object)}，{@code is=false} 时整条跳过。
     * <pre>.between(min != null &amp;&amp; max != null, User::getAge, min, max)</pre>
     */
    default ME between(boolean is, DlzFn<T, ?> column, Object value1, Object value2) {
        if (is) {
            addChildren(between.mk(column, new Object[]{value1, value2}, getTableName()));
        }
        return me();
    }

    /**
     * 单值形式的 BETWEEN，{@code value} 可为：{@code "v1,v2"} 字符串 / {@code [v1,v2]} JSON / 数组 / List。
     * <pre>.between(User::getAge, "18,60")</pre>
     */
    default ME between(DlzFn<T, ?> column, Object value) {
        addChildren(between.mk(column, value, getTableName()));
        return me();
    }

    /**
     * 动态条件版 {@link #between(DlzFn, Object)}，{@code is=false} 时整条跳过。
     */
    default ME between(boolean is, DlzFn<T, ?> column, Object value) {
        if (is) {
            addChildren(between.mk(column, value, getTableName()));
        }
        return me();
    }

    /**
     * {@code NOT BETWEEN value1 AND value2}。
     * <pre>.notBetween(User::getAge, 18, 60)</pre>
     */
    default ME notBetween(DlzFn<T, ?> column, Object value1, Object value2) {
        addChildren(notBetween.mk(column, new Object[]{value1, value2}, getTableName()));
        return me();
    }

    /**
     * 动态条件版 {@link #notBetween(DlzFn, Object, Object)}。
     */
    default ME notBetween(boolean is, DlzFn<T, ?> column, Object value1, Object value2) {
        if (is) {
            addChildren(notBetween.mk(column, new Object[]{value1, value2}, getTableName()));
        }
        return me();
    }

    /**
     * 单值形式的 NOT BETWEEN，{@code value} 格式同 {@link #between(DlzFn, Object)}。
     */
    default ME notBetween(DlzFn<T, ?> column, Object value) {
        addChildren(notBetween.mk(column, value, getTableName()));
        return me();
    }

    /**
     * 动态条件版 {@link #notBetween(DlzFn, Object)}。
     */
    default ME notBetween(boolean is, DlzFn<T, ?> column, Object value) {
        if (is) {
            addChildren(notBetween.mk(column, value, getTableName()));
        }
        return me();
    }

    // ========== IS NULL / IS NOT NULL ==========

    /**
     * {@code IS NOT NULL}。
     * <pre>.isNotNull(User::getEmail)   // email IS NOT NULL</pre>
     */
    default ME isNotNull(DlzFn<T, ?> column) {
        addChildren(isNotNull.mk(column, null, getTableName()));
        return me();
    }

    /**
     * 动态条件版 {@link #isNotNull(DlzFn)}。
     */
    default ME isNotNull(boolean is, DlzFn<T, ?> column) {
        if (is) {
            addChildren(isNotNull.mk(column, null, getTableName()));
        }
        return me();
    }

    /**
     * {@code IS NULL}。
     * <pre>.isNull(User::getDeleteTime)   // delete_time IS NULL</pre>
     */
    default ME isNull(DlzFn<T, ?> column) {
        addChildren(isNull.mk(column, null, getTableName()));
        return me();
    }

    /**
     * 动态条件版 {@link #isNull(DlzFn)}。
     */
    default ME isNull(boolean is, DlzFn<T, ?> column) {
        if (is) {
            addChildren(isNull.mk(column, null, getTableName()));
        }
        return me();
    }

    // ========== EQ / NE ==========

    /**
     * {@code column = value}。
     * <pre>.eq(User::getStatus, 1)   // status = 1</pre>
     */
    default ME eq(DlzFn<T, ?> column, Object value) {
        addChildren(eq.mk(column, value, getTableName()));
        return me();
    }

    /**
     * 动态条件版 {@link #eq(DlzFn, Object)}。
     * <pre>.eq(name != null, User::getName, name)</pre>
     */
    default ME eq(boolean is, DlzFn<T, ?> column, Object value) {
        if (is) {
            addChildren(eq.mk(column, value, getTableName()));
        }
        return me();
    }

    /**
     * {@code column <> value}。
     */
    default ME ne(DlzFn<T, ?> column, Object value) {
        addChildren(ne.mk(column, value, getTableName()));
        return me();
    }

    /**
     * 动态条件版 {@link #ne(DlzFn, Object)}。
     */
    default ME ne(boolean is, DlzFn<T, ?> column, Object value) {
        if (is) {
            addChildren(ne.mk(column, value, getTableName()));
        }
        return me();
    }

    // ========== 大小比较 ==========

    /**
     * {@code column > value}。
     * <pre>.gt(User::getAge, 18)</pre>
     */
    default ME gt(DlzFn<T, ?> column, Object value) {
        addChildren(gt.mk(column, value, getTableName()));
        return me();
    }

    /**
     * 动态条件版 {@link #gt(DlzFn, Object)}。
     */
    default ME gt(boolean is, DlzFn<T, ?> column, Object value) {
        if (is) {
            addChildren(gt.mk(column, value, getTableName()));
        }
        return me();
    }

    /**
     * {@code column >= value}。
     */
    default ME ge(DlzFn<T, ?> column, Object value) {
        addChildren(ge.mk(column, value, getTableName()));
        return me();
    }

    /**
     * 动态条件版 {@link #ge(DlzFn, Object)}。
     */
    default ME ge(boolean is, DlzFn<T, ?> column, Object value) {
        if (is) {
            addChildren(ge.mk(column, value, getTableName()));
        }
        return me();
    }

    /**
     * {@code column < value}。
     */
    default ME lt(DlzFn<T, ?> column, Object value) {
        addChildren(lt.mk(column, value, getTableName()));
        return me();
    }

    /**
     * 动态条件版 {@link #lt(DlzFn, Object)}。
     */
    default ME lt(boolean is, DlzFn<T, ?> column, Object value) {
        if (is) {
            addChildren(lt.mk(column, value, getTableName()));
        }
        return me();
    }

    /**
     * {@code column <= value}。
     */
    default ME le(DlzFn<T, ?> column, Object value) {
        addChildren(le.mk(column, value, getTableName()));
        return me();
    }

    /**
     * 动态条件版 {@link #le(DlzFn, Object)}。
     */
    default ME le(boolean is, DlzFn<T, ?> column, Object value) {
        if (is) {
            addChildren(le.mk(column, value, getTableName()));
        }
        return me();
    }

    // ========== LIKE 系列 ==========

    /**
     * {@code column LIKE '%value%'}（双侧模糊）。
     * <pre>.like(User::getName, "张")   // name LIKE '%张%'</pre>
     */
    default ME like(DlzFn<T, ?> column, Object value) {
        addChildren(like.mk(column, value, getTableName()));
        return me();
    }

    /**
     * 动态条件版 {@link #like(DlzFn, Object)}。
     */
    default ME like(boolean is, DlzFn<T, ?> column, Object value) {
        if (is) {
            addChildren(like.mk(column, value, getTableName()));
        }
        return me();
    }

    /**
     * {@code column LIKE 'value%'}（右模糊，通常可命中前缀索引）。
     * <pre>.likeLeft(User::getName, "张")   // name LIKE '张%'</pre>
     */
    default ME likeLeft(DlzFn<T, ?> column, Object value) {
        addChildren(likeLeft.mk(column, value, getTableName()));
        return me();
    }

    /**
     * 动态条件版 {@link #likeLeft(DlzFn, Object)}。
     */
    default ME likeLeft(boolean is, DlzFn<T, ?> column, Object value) {
        if (is) {
            addChildren(likeLeft.mk(column, value, getTableName()));
        }
        return me();
    }

    /**
     * {@code column LIKE '%value'}（左模糊，通常无法命中索引）。
     * <pre>.likeRight(User::getEmail, "@qq.com")</pre>
     */
    default ME likeRight(DlzFn<T, ?> column, Object value) {
        addChildren(likeRight.mk(column, value, getTableName()));
        return me();
    }

    /**
     * 动态条件版 {@link #likeRight(DlzFn, Object)}。
     */
    default ME likeRight(boolean is, DlzFn<T, ?> column, Object value) {
        if (is) {
            addChildren(likeRight.mk(column, value, getTableName()));
        }
        return me();
    }

    /**
     * {@code column NOT LIKE '%value%'}。
     */
    default ME notLike(DlzFn<T, ?> column, Object value) {
        addChildren(notLike.mk(column, value, getTableName()));
        return me();
    }

    /**
     * 动态条件版 {@link #notLike(DlzFn, Object)}。
     */
    default ME notLike(boolean is, DlzFn<T, ?> column, Object value) {
        if (is) {
            addChildren(notLike.mk(column, value, getTableName()));
        }
        return me();
    }

    // ========== IN / NOT IN ==========

    /**
     * {@code column IN (...)}。{@code value} 可为：数组 / {@link java.util.Collection} / 逗号分隔字符串 {@code "1,2,3"} / 以 {@code "sql:"} 开头的子查询。
     * <pre>
     * .in(User::getId, "1,2,3")
     * .in(User::getId, Arrays.asList(1, 2, 3))
     * .in(User::getId, "sql:SELECT id FROM vip WHERE level > 5")
     * </pre>
     */
    default ME in(DlzFn<T, ?> column, Object value) {
        addChildren(in.mk(column, value, getTableName()));
        return me();
    }

    /**
     * 动态条件版 {@link #in(DlzFn, Object)}。
     */
    default ME in(boolean is, DlzFn<T, ?> column, Object value) {
        if (is) {
            addChildren(in.mk(column, value, getTableName()));
        }
        return me();
    }

    /**
     * {@code column NOT IN (...)}，{@code value} 格式同 {@link #in(DlzFn, Object)}。
     */
    default ME notIn(DlzFn<T, ?> column, Object value) {
        addChildren(notIn.mk(column, value, getTableName()));
        return me();
    }

    /**
     * 动态条件版 {@link #notIn(DlzFn, Object)}。
     */
    default ME notIn(boolean is, DlzFn<T, ?> column, Object value) {
        if (is) {
            addChildren(notIn.mk(column, value, getTableName()));
        }
        return me();
    }

    // ========== 自定义操作符 ==========

    /**
     * 以自定义操作符添加条件。适用于上述枚举未覆盖的场景。
     * <pre>.op(User::getName, DbOprateEnum.eq, "admin")</pre>
     *
     * @param op 操作符枚举；<b>注意</b>：实际使用的是此枚举自身的 {@code mk(column, value)}，
     *           参数 {@code column}/{@code value} 的含义与该枚举语义一致。
     */
    default ME op(DlzFn<T, ?> column, DbOperateEnum op, Object value) {
        addChildren(op.mk(column, value, getTableName()));
        return me();
    }
}
