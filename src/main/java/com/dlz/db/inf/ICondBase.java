package com.dlz.db.inf;

import com.dlz.db.modal.condition.Condition;

/**
 * 条件构造器的最底层接口：所有条件子类必须能把一个 {@link Condition} 节点挂到自己管理的条件树上。
 * <p>通常由框架实现，业务代码不直接调用 {@link #addChildren}。
 *
 * @param <ME> 链式返回类型
 */
public interface ICondBase<ME extends ICondBase> extends IChained<ME> {
    /** 把一个条件节点挂到当前条件容器上（由上层 eq/gt/and/or 等方法内部调用）。 */
    void addChildren(Condition child);
    String getTableName();
}
