package com.dlz.db.inf;

import com.dlz.db.modal.items.JdbcItem;
import com.dlz.db.modal.items.SqlItem;

/**
 * SQL 生成入口：把构造器内部状态（条件、分页、参数等）渲染成可执行的 JDBC SQL。
 * <p>所有可执行的构造器（查询/更新/删除）都实现该接口，框架执行器据此产出最终 SQL + 参数。
 */
public interface ISqlPara {
    /** 生成主体 SQL（SELECT/UPDATE/DELETE 等）及其绑定参数。 */
    JdbcItem jdbcSql();

    /** 生成配套的 COUNT SQL，用于分页查询总数。 */
    JdbcItem jdbcCnt();

    /** 返回原始的 SQL 中间结构（含未绑定参数的模板片段，主要供框架内部使用）。 */
    SqlItem getSqlItem();
}
