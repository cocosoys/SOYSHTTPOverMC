package com.dlz.db.inf;

import com.dlz.db.support.DBHolder;

/**
 * 增删改（Update / Delete / Insert）执行器的统一入口。
 * <p>任何具备"写"能力的构造器（{@link com.dlz.db.modal.wrapper.PojoUpdate}、
 * {@link com.dlz.db.modal.wrapper.TableDelete} 等）都实现本接口。
 */
public interface IExecutorUDI extends ISqlPara {
    /**
     * 执行构造出的 SQL，返回受影响行数。
     * <pre>int rows = DB.Pojo.update(user).set(...).where(...).execute();</pre>
     */
    default int execute() {
        return DBHolder.doDb(s -> s.execute(this));
    }
}
