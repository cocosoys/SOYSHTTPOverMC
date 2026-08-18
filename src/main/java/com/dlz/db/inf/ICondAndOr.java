package com.dlz.db.inf;

import com.dlz.db.enums.DbBuildEnum;
import com.dlz.db.modal.condition.Condition;
import com.dlz.kit.json.JSONMap;

import java.util.function.Consumer;

/**
 * 添加and or条件
 * @param <ME>
 */
public interface ICondAndOr<ME extends ICondAndOr> extends ICondBase<ME> {
    /**
     * 以自定义 SQL 片段添加一个条件。占位符采用 <b>命名式 {@code #{key}}</b>，参数由 {@link JSONMap} 提供。
     * <p>支持 <b>中括号语法</b>：{@code [AND name LIKE #{name}]} 中若 {@code name} 为 null/空，整段自动忽略。
     * <p>可传入预设 SQL key（以 {@code key.} 开头）自动从 XML/DB 加载 SQL 文本。
     *
     * <pre>
     * // 基础用法
     * .sql("age > #{min} AND age < #{max}", new JSONMap("min", 18).set("max", 60))
     *
     * // 中括号空值忽略
     * .sql("[AND status = #{status}]", new JSONMap("status", null))  // 此条件不加入
     *
     * // 引用预设 SQL
     * .sql("key.user.complexFilter", params)
     * </pre>
     *
     * @param is    为 false 则跳过此条件（动态条件的便捷开关）
     * @param sql   SQL 片段，或以 {@code key.} 开头的预设 SQL 标识
     * @param paras 命名参数，键对应 {@code #{key}} 中的 key
     * @return 当前条件对象，支持链式调用
     */
    default ME sql(boolean is, String sql, JSONMap paras) {
        if(is){
            // 实现思路：交给 DbBuildEnum.sql 解析模板（处理中括号、预设 key、占位符替换），
            // 产物是一个 Condition 节点；build 可能返回 null（如 sql 为空），需过滤。
            Condition sqlCond = DbBuildEnum.sql.build(getTableName(), sql, paras);
            if(sqlCond != null){
                addChildren(sqlCond);
            }
        }
        return me();
    }

    /**
     * {@link #sql(boolean, String, JSONMap)} 的便捷重载，{@code is} 默认为 true。
     */
    default ME sql(String sql, JSONMap paras) {
        return sql(true, sql, paras);
    }
//
//    /**
//     * 以自定义 SQL 片段添加一个条件。占位符采用 <b>位置式 {@code {0} {1} {2}}</b>，对应后续可变参数。
//     * <p>底层会把 {@code {n}} 转为命名占位符再复用 {@link #sql} 的模板引擎，因此中括号语法同样可用。
//     * <p><b>推荐新代码使用 {@link #sql} 命名参数形式</b>，可读性更好、不易错位。
//     *
//     * <pre>
//     * .apply("age > {0} AND age < {1}", 18, 60)
//     * .apply("EXISTS (SELECT 1 FROM vip WHERE user_id = t.id AND level >= {0})", 3)
//     * </pre>
//     *
//     * @param is    为 false 则跳过此条件
//     * @param sql   SQL 片段，占位符形如 {@code {0}, {1}}（下标对应 paras 数组位置）
//     * @param paras 位置参数数组
//     * @return 当前条件对象，支持链式调用
//     */
//    default T apply(boolean is,String sql, Object... paras) {
//        if(is){
//            // 实现思路：DbBuildEnum.apply.build 会扫描 {n} 正则，把每个 {n} 替换成 #{auto_key_n}，
//            // 再把 paras[n] 以 auto_key_n 塞入参数 map，之后走与 sql() 相同的模板处理链路。
//            Condition sqlCond = DbBuildEnum.apply.build(sql, paras);
//            if(sqlCond != null){
//                addChildren(sqlCond);
//            }
//        }
//        return me();
//    }
//
//    /**
//     * {@link #apply(boolean, String, Object...)} 的便捷重载，{@code is} 默认为 true。
//     */
//    default T apply(String sql, Object... paras) {
//        return apply(true, sql, paras);
//    }

    /**
     * 添加一组用 <b>AND</b> 连接的子条件，整组与外层条件用外层默认连接符（通常为 AND）拼接。
     * <p>语义：<b>方法名 = 括号内部的连接符</b>。{@code .ands(ands -> ...)} 表示 lambda 内的每个子条件都用 AND 拼接。
     * <p>子条件数量 > 1 时自动加括号。
     *
     * <pre>
     * // 单纯使用：等价于链式 AND（通常不需要，直接链式即可）
     * .ands(a -> a.eq(User::getStatus, 1).gt(User::getAge, 18))
     * // SQL: (status = 1 AND age > 18)
     *
     * // 在 or 内部使用，构造  (A AND B) OR (C AND D)
     * .ors(o -> o
     *     .ands(a -> a.eq(User::getType, 1).gt(User::getAge, 18))
     *     .ands(a -> a.eq(User::getType, 2).lt(User::getAge, 60)))
     * </pre>
     *
     * @param ands 一组将被 <b>AND</b> 连接的子条件（lambda 内调用 eq/gt/or/... 等）
     * @return 当前条件对象，支持链式调用
     */
    default ME ands(Consumer<Condition> ands) {
        // 实现思路：新建一个 muAnd 容器节点挂到当前条件上，
        // 再把该容器作为参数传给 lambda，用户在 lambda 内添加的所有子条件
        // 都会被 addChildren 到此容器，最终渲染时容器内的 children 全部用 AND 拼接。
        // 挂容器在前、执行 lambda 在后，保证 lambda 内的任何调用都作用到新容器而非外层。
        Condition and = DbBuildEnum.muAnd.build(getTableName());
        addChildren(and);
        ands.accept(and);
        return me();
    }

    /**
     * 添加一组用 <b>OR</b> 连接的子条件，整组与外层条件用外层默认连接符（通常为 AND）拼接。
     * <p>语义：<b>方法名 = 括号内部的连接符</b>。{@code .ors(ors -> ...)} 表示 lambda 内的每个子条件都用 OR 拼接。
     * <p>子条件数量 > 1 时自动加括号。
     *
     * <pre>
     * // 最常见用法：A AND (B OR C)
     * .eq(User::getStatus, 1)
     * .ors(o -> o.like(User::getName, kw).like(User::getMobile, kw))
     * // SQL: status = 1 AND (name LIKE ? OR mobile LIKE ?)
     *
     * // 并列多个 or 组：(A OR B) AND (C OR D)
     * .ors(o -> o.eq(User::getType, 1).eq(User::getType, 2))
     * .ors(o -> o.gt(User::getAge, 60).lt(User::getAge, 18))
     * // SQL: (type = 1 OR type = 2) AND (age > 60 OR age < 18)
     * </pre>
     *
     * <p><b>⚠️ 与 MyBatis-Plus 的差异</b>：MP 的 {@code .ors(lambda)} 表示 "下一组与前文用 OR 连接，lambda 内部默认 AND"；
     * DLZ-DB 的 {@code .ors(lambda)} 表示 "lambda 内部强制 OR 连接，整组与前文用 AND"。语义相反，从 MP 迁移时请逐处审查。
     *
     * @param ors 一组将被 <b>OR</b> 连接的子条件（lambda 内调用 eq/gt/and/... 等）
     * @return 当前条件对象，支持链式调用
     */
    default ME ors(Consumer<Condition> ors) {
        // 实现思路：与 ands(...) 对称，只是容器类型换成 muOr。
        // muOr 容器在渲染时把内部 children 全部用 OR 拼接；容器本身与外层（默认 AND）相接。
        // 这也是 "方法名 = 内部连接符" 这一核心设计的体现：调用处只需关心括号内的逻辑运算。
        Condition or = DbBuildEnum.muOr.build(getTableName());
        addChildren(or);
        ors.accept(or);
        return me();
    }
}
