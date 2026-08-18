package com.dlz.db.inf;

import com.dlz.db.modal.condition.Condition;

/**
 * 通用查询/写入构造器的条件入口总和。
 *
 * <p>继承自：
 * <ul>
 *   <li>{@link ICondAndOr}：原生 SQL 片段（sql/apply）及嵌套 and/or 组；</li>
 *   <li>{@link ICondAddByKey}：以<b>字符串列名</b>添加 eq/gt/like/in/... 等条件；</li>
 *   <li>{@link ICondAuto}：按 Map 自动批量生成条件。</li>
 * </ul>
 * <p>Lambda 版本（{@link ICondAddByLamda}）通常由具体 Pojo 构造器按需额外混入，不在本接口默认包含。
 *
 * @param <T> 链式返回类型
 */
public interface ISqlQuery<T extends ISqlQuery> extends
        ICondAndOr<T>,
        ICondAddByKey<T>,
        ICondAuto<T> {
    /** 当前构造器持有的 WHERE 根条件节点。 */
    Condition where();

    /** 向 {@link #where()} 根节点追加子条件（由 {@link ICondBase} 约定，业务代码不直接调用）。 */
    default void addChildren(Condition child) {
        where().addChildren(child);
    }

    /** 用给定条件整体替换 WHERE。 */
    T where(Condition cond);

    /**
     * 允许/禁止"无条件的全表查询或更新"。
     * <p>默认 false：当条件为空时执行会抛异常，防止误操作全表。设为 true 明确承担风险。
     */
    T setAllowFullQuery(boolean allowFullQuery);

    /** 目标表名。 */
    String getTableName();

    /** 是否允许在无 WHERE 条件时执行。 */
    boolean isAllowFullQuery();
}
