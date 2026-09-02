package com.dlz.db.inf;

import com.dlz.db.support.DBHolder;

/**
 * 插入执行器：在 {@link IExecutorUDI#execute()} 之外，额外提供"插入并回填自增主键"的方法。
 */
public interface IExecutorInsert extends IExecutorUDI {
    /**
     * 执行插入，返回数据库生成的自增主键。
     * <pre>Long id = DB.Table.insert("user")).insertWithAutoKey();</pre>
     */
    default Long insertWithAutoKey() {
        return DBHolder.doDb(s -> s.insertWithAutoKey(this));
    }
}
