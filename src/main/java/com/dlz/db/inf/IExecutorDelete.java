package com.dlz.db.inf;

import com.dlz.db.interceptor.DbPlugin;
import com.dlz.db.interceptor.SqlBuildInterceptor;
import com.dlz.db.support.DBHolder;
import com.dlz.db.support.SqlRunThreadHolder;

/**
 * 删除执行器：在"查询构造器"上叠加"执行删除"能力。
 *
 * <p><b>逻辑删除</b>已抽取为 {@link com.dlz.db.interceptor.LogicDeleteInterceptor} 插件，
 * 通过 {@link SqlBuildInterceptor#onExecuteDelete(IExecutorDelete)} 钩子实现 DELETE → UPDATE 改写。
 * 本接口不再硬编码逻辑删除逻辑，统一走插件链。
 *
 * <pre>
 * // 默认：如果注册了 LogicDeleteInterceptor 且表有 deleted 字段 → 逻辑删除
 * DB.Table.delete("user").eq("id", 1).execute();
 *
 * // 本次强制物理删除（跳过逻辑删除插件）
 * DB.Table.delete("user").ignoreLogicDelete(true).eq("id", 1).execute();
 * </pre>
 */
public interface IExecutorDelete<ME extends IExecutorDelete>
        extends IExecutorUDI, ISqlQuery<ME> {
    /** 目标表名。 */
    String getTableName();

    /**
     * 执行删除。
     * <p>先遍历已注册的 {@link DbPlugin} 逻辑删除插件 执行结果>-1 表示生效，拦截真是 DELETE </p>
     * -1 放行则执行物理 DELETE
     *
     * @return 受影响行数
     */
    default int execute() {
        try {
            // 调用插件：逻辑删除插件会在此将 DELETE 改写为 UPDATE deleted=1
            final int logicDeleteCnt = DbPlugin.doLogicDelete(this);
            if(logicDeleteCnt >-1){
                return logicDeleteCnt;
            }
            // 无插件拦截，走物理 DELETE
            return DBHolder.doDb(s -> s.execute(this));
        } finally {
            SqlRunThreadHolder.removeLogicDeleteSetting();
        }
    }

    /**
     * 设置本线程下一次 {@link #execute()} 是否启用逻辑删除。
     * <p>{@code true}（默认）：走逻辑删除（UPDATE 软删除）；<br>
     * {@code false}：强制走物理 DELETE。
     * <p>设置仅作用于<b>本线程的下一次执行</b>，execute 完毕会自动清理。
     */
    default ME ignoreLogicDelete(boolean ignoreLogicDelete) {
        SqlRunThreadHolder.setIgnoreLogicDelete(ignoreLogicDelete);
        return me();
    }
}
