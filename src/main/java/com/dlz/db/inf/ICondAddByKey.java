package com.dlz.db.inf;

import com.dlz.db.enums.DbOperateEnum;

import static com.dlz.db.enums.DbOperateEnum.*;

/**
 * 基于 <b>字符串列名</b> 的条件构造接口。
 *
 * <p><b>设计约定</b>：所有条件方法统一签名 <b>{@code (boolean is, String column, Object value)}</b>；
 * 省略 {@code is} 的重载等价于 {@code is=true}；{@code is=false} 时整条条件跳过，适合动态条件。
 *
 * <p><b>与 {@link ICondAddByLamda} 的差异</b>：
 * <ul>
 *   <li>列名为手写字符串（如 {@code "user_name"}），不做类型安全校验；</li>
 *   <li>适用于列名在运行时决定、或 Bean 中无对应属性、或拼接同表多列的场景；</li>
 *   <li>列名大小写与数据库实际列名一致（通常为 snake_case 小写）。</li>
 * </ul>
 *
 * <pre>
 * // 动态列名场景
 * String sortCol = userInput.getSortField();  // e.g. "create_time"
 * .eq("status", 1).gt(sortCol, someValue)
 *
 * // 同 {@link ICondAddByLamda} 等价
 * .eq("user_id", 42)    // 等价于 .eq(User::getUserId, 42)
 * </pre>
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
 */
public interface ICondAddByKey<ME extends ICondAddByKey> extends ICondBase<ME> {

    // ========== BETWEEN / NOT BETWEEN ==========

    /**
     * {@code column BETWEEN value1 AND value2}。
     * <pre>.between("age", 18, 60)   // age BETWEEN 18 AND 60</pre>
     */
    default ME between(String column, Object value1, Object value2) {
        addChildren(between.mk(column, new Object[]{value1, value2}, getTableName()));
        return me();
    }

    /** 动态条件版 {@link #between(String, Object, Object)}。 */
    default ME between(boolean is, String column, Object value1, Object value2) {
        if (is) {
            addChildren(between.mk(column, new Object[]{value1, value2}, getTableName()));
        }
        return me();
    }

    /**
     * 单值形式的 BETWEEN。{@code value} 支持多种形式：
     * <ul>
     *   <li>字符串："a1,a2"（逗号分隔）</li>
     *   <li>JSON：[a1,a2]</li>
     *   <li>数组：{@code new Object[]{a1, a2}}</li>
     *   <li>{@link java.util.List}：{@code Arrays.asList(a1, a2)}</li>
     * </ul>
     * <pre>
     * .between("create_time", "2024-01-01,2024-12-31")
     * .between("age", Arrays.asList(18, 60))
     * </pre>
     */
    default ME between(String column, Object value) {
        addChildren(between.mk(column, value, getTableName()));
        return me();
    }

    /** 动态条件版 {@link #between(String, Object)}。 */
    default ME between(boolean is, String column, Object value) {
        if (is) {
            addChildren(between.mk(column, value, getTableName()));
        }
        return me();
    }

    /** {@code column NOT BETWEEN value1 AND value2}。 */
    default ME notBetween(String column, Object value1, Object value2) {
        addChildren(notBetween.mk(column, new Object[]{value1, value2}, getTableName()));
        return me();
    }

    /** 动态条件版 {@link #notBetween(String, Object, Object)}。 */
    default ME notBetween(boolean is, String column, Object value1, Object value2) {
        if (is) {
            addChildren(notBetween.mk(column, new Object[]{value1, value2}, getTableName()));
        }
        return me();
    }

    /** 单值形式的 NOT BETWEEN，{@code value} 格式同 {@link #between(String, Object)}。 */
    default ME notBetween(String column, Object value) {
        addChildren(notBetween.mk(column, value, getTableName()));
        return me();
    }

    /** 动态条件版 {@link #notBetween(String, Object)}。 */
    default ME notBetween(boolean is, String column, Object value) {
        if (is) {
            addChildren(notBetween.mk(column, value, getTableName()));
        }
        return me();
    }

    // ========== IS NULL / IS NOT NULL ==========

    /**
     * {@code column IS NOT NULL}。
     * <pre>.isNotNull("email")   // email IS NOT NULL</pre>
     */
    default ME isNotNull(String column) {
        addChildren(isNotNull.mk(column, null, getTableName()));
        return me();
    }

    /** 动态条件版 {@link #isNotNull(String)}。 */
    default ME isNotNull(boolean is, String column) {
        if (is) {
            addChildren(isNotNull.mk(column, null, getTableName()));
        }
        return me();
    }

    /**
     * {@code column IS NULL}。
     * <pre>.isNull("delete_time")   // delete_time IS NULL</pre>
     */
    default ME isNull(String column) {
        addChildren(isNull.mk(column, null, getTableName()));
        return me();
    }

    /** 动态条件版 {@link #isNull(String)}。 */
    default ME isNull(boolean is, String column) {
        if (is) {
            addChildren(isNull.mk(column, null, getTableName()));
        }
        return me();
    }

    // ========== EQ / NE ==========

    /**
     * {@code column = value}。
     * <pre>.eq("status", 1)   // status = 1</pre>
     */
    default ME eq(String column, Object value) {
        addChildren(eq.mk(column, value, getTableName()));
        return me();
    }

    /**
     * 动态条件版 {@link #eq(String, Object)}。动态条件首选写法。
     * <pre>.eq(name != null, "name", name)</pre>
     */
    default ME eq(boolean is, String column, Object value) {
        if (is) {
            addChildren(eq.mk(column, value, getTableName()));
        }
        return me();
    }

    /** {@code column <> value}。 */
    default ME ne(String column, Object value) {
        addChildren(ne.mk(column, value, getTableName()));
        return me();
    }

    /** 动态条件版 {@link #ne(String, Object)}。 */
    default ME ne(boolean is, String column, Object value) {
        if (is) {
            addChildren(ne.mk(column, value, getTableName()));
        }
        return me();
    }

    // ========== 大小比较 ==========

    /** {@code column > value}。 */
    default ME gt(String column, Object value) {
        addChildren(gt.mk(column, value, getTableName()));
        return me();
    }

    /** 动态条件版 {@link #gt(String, Object)}。 */
    default ME gt(boolean is, String column, Object value) {
        if (is) {
            addChildren(gt.mk(column, value, getTableName()));
        }
        return me();
    }

    /** {@code column >= value}。 */
    default ME ge(String column, Object value) {
        addChildren(ge.mk(column, value, getTableName()));
        return me();
    }

    /** 动态条件版 {@link #ge(String, Object)}。 */
    default ME ge(boolean is, String column, Object value) {
        if (is) {
            addChildren(ge.mk(column, value, getTableName()));
        }
        return me();
    }

    /** {@code column < value}。 */
    default ME lt(String column, Object value) {
        addChildren(lt.mk(column, value, getTableName()));
        return me();
    }

    /** 动态条件版 {@link #lt(String, Object)}。 */
    default ME lt(boolean is, String column, Object value) {
        if (is) {
            addChildren(lt.mk(column, value, getTableName()));
        }
        return me();
    }

    /** {@code column <= value}。 */
    default ME le(String column, Object value) {
        addChildren(le.mk(column, value, getTableName()));
        return me();
    }

    /** 动态条件版 {@link #le(String, Object)}。 */
    default ME le(boolean is, String column, Object value) {
        if (is) {
            addChildren(le.mk(column, value, getTableName()));
        }
        return me();
    }

    // ========== LIKE 系列 ==========

    /**
     * {@code column LIKE '%value%'}（双侧模糊）。
     * <pre>.like("name", "张")   // name LIKE '%张%'</pre>
     */
    default ME like(String column, Object value) {
        addChildren(like.mk(column, value, getTableName()));
        return me();
    }

    /** 动态条件版 {@link #like(String, Object)}。 */
    default ME like(boolean is, String column, Object value) {
        if (is) {
            addChildren(like.mk(column, value, getTableName()));
        }
        return me();
    }

    /**
     * {@code column LIKE 'value%'}（右模糊，通常可命中前缀索引）。
     * <pre>.likeLeft("name", "张")   // name LIKE '张%'</pre>
     */
    default ME likeLeft(String column, Object value) {
        addChildren(likeLeft.mk(column, value, getTableName()));
        return me();
    }

    /** 动态条件版 {@link #likeLeft(String, Object)}。 */
    default ME likeLeft(boolean is, String column, Object value) {
        if (is) {
            addChildren(likeLeft.mk(column, value, getTableName()));
        }
        return me();
    }

    /**
     * {@code column LIKE '%value'}（左模糊，通常无法命中索引）。
     * <pre>.likeRight("email", "@qq.com")</pre>
     */
    default ME likeRight(String column, Object value) {
        addChildren(likeRight.mk(column, value, getTableName()));
        return me();
    }

    /** 动态条件版 {@link #likeRight(String, Object)}。 */
    default ME likeRight(boolean is, String column, Object value) {
        if (is) {
            addChildren(likeRight.mk(column, value, getTableName()));
        }
        return me();
    }

    /** {@code column NOT LIKE '%value%'}。 */
    default ME notLike(String column, Object value) {
        addChildren(notLike.mk(column, value, getTableName()));
        return me();
    }

    /** 动态条件版 {@link #notLike(String, Object)}。 */
    default ME notLike(boolean is, String column, Object value) {
        if (is) {
            addChildren(notLike.mk(column, value, getTableName()));
        }
        return me();
    }

    // ========== IN / NOT IN ==========

    /**
     * {@code column IN (...)}。{@code value} 可为：
     * <ul>
     *   <li>数组 / {@link java.util.Collection}</li>
     *   <li>逗号分隔字符串 {@code "1,2,3"}</li>
     *   <li>以 {@code "sql:"} 开头的子查询片段 {@code "sql:SELECT id FROM ..."}</li>
     * </ul>
     * <b>不可传单值</b>（传单值不会按 {@code col = value} 处理，行为未定义）。
     * <pre>
     * .in("id", "1,2,3")
     * .in("id", Arrays.asList(1, 2, 3))
     * .in("id", "sql:SELECT user_id FROM vip WHERE level > 5")
     * </pre>
     */
    default ME in(String column, Object value) {
        addChildren(in.mk(column, value, getTableName()));
        return me();
    }

    /** 动态条件版 {@link #in(String, Object)}。 */
    default ME in(boolean is, String column, Object value) {
        if (is) {
            addChildren(in.mk(column, value, getTableName()));
        }
        return me();
    }

    /** {@code column NOT IN (...)}，{@code value} 格式同 {@link #in(String, Object)}。 */
    default ME notIn(String column, Object value) {
        addChildren(notIn.mk(column, value, getTableName()));
        return me();
    }

    /** 动态条件版 {@link #notIn(String, Object)}。 */
    default ME notIn(boolean is, String column, Object value) {
        if (is) {
            addChildren(notIn.mk(column, value, getTableName()));
        }
        return me();
    }

    // ========== 自定义操作符 ==========

    /**
     * 以自定义操作符添加条件。适用于上述枚举未覆盖的场景。
     * <pre>.op("name", DbOprateEnum.eq, "admin")</pre>
     *
     * @param op 操作符枚举；实际调用 {@code op.mk(column, value)}，语义由枚举决定。
     */
    default ME op(String column, DbOperateEnum op, Object value) {
        addChildren(op.mk(column, value, getTableName()));
        return me();
    }
}
