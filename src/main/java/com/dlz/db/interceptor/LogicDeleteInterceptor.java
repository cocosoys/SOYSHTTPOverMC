package com.dlz.db.interceptor;

import com.dlz.db.inf.IExecutorDelete;
import com.dlz.db.modal.condition.Condition;
import com.dlz.db.modal.para.ParaMap;
import com.dlz.db.modal.wrapper.PojoUpdate;
import com.dlz.db.modal.wrapper.TableDelete;
import com.dlz.db.modal.wrapper.TableUpdate;
import com.dlz.db.modal.wrapper.WrapperBuildUtil;
import com.dlz.db.support.DBHolder;
import com.dlz.db.support.PojoCache;
import com.dlz.db.support.SqlRunThreadHolder;
import com.dlz.db.util.DbConvertUtil;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * 逻辑删除拦截器。
 *
 * <p>将原来散落在 {@code WrapperBuildUtil.buildWhere}、{@code IExecutorDelete.execute}、
 * {@code IDbExecuteService} 里的逻辑删除代码统一收拢为单个插件。
 *
 * <h3>行为</h3>
 * <table border="1">
 * <tr><th>操作</th><th>自动行为</th><th>注入位置</th></tr>
 * <tr><td>查询/更新</td><td>WHERE 追加 {@code deleted = 0}</td><td>{@link #onBuildWhere}</td></tr>
 * <tr><td>插入</td><td>VALUES 追加 {@code deleted = 0}</td><td>{@link #onBuildInsert}</td></tr>
 * <tr><td>删除</td><td>DELETE → UPDATE {@code deleted = 1}</td><td>{@link #doLogicDelete}</td></tr>
 * </table>
 *
 * <h3>开关控制</h3>
 * <ul>
 *   <li>全局开关：{@code DlzDbProperties.logicDeleteField} 为空则禁用</li>
 *   <li>线程级开关：{@link SqlRunThreadHolder#isIgnoreLogicDelete()} 为 true 则跳过（单次强制物理删除）</li>
 * </ul>
 *
 * <pre>
 * // 默认：逻辑删除
 * DB.Table.delete("user").eq("id", 1).execute();
 *
 * // 本次强制物理删除
 * DB.Table.delete("user").ignoreLogicDelete(true).eq("id", 1).execute();
 * </pre>
 *
 * @author dingkui
 * @since 7.1.0
 */
@Slf4j
public class LogicDeleteInterceptor implements SqlBuildInterceptor {

    private final String dbColumnName;
    private final String fieldName;

    public LogicDeleteInterceptor(String fieldName) {
        this.fieldName = DbConvertUtil.toFieldName(fieldName);
        this.dbColumnName = DbConvertUtil.toDbColumnName(fieldName);
    }

    @Override
    public boolean isEnabled() {
        // 全局开关：字段名未配置则禁用
        // 线程级开关：SqlRunThreadHolder.isIgnoreLogicDelete() 为 true 时本次跳过
        return dbColumnName != null && !dbColumnName.isEmpty();
    }

    /**
     * 拦截逻辑查询
     * @param tableName 表名
     * @param where 查询条件
     */
    @Override
    public void onBuildWhere(String tableName, Condition where) {
        if (SqlRunThreadHolder.isIgnoreLogicDelete()) {
            return;
        }
        if (!PojoCache.isColumnExists(tableName, dbColumnName)) {
            return;
        }
        if (where.isContainCondition(dbColumnName)) {
            return;
        }
        where.eq(dbColumnName, 0);
    }

    /**
     * 拦截逻辑插入
     * @param tableName 表名
     * @param insertValues 插入字段
     */
    @Override
    public void onBuildInsert(String tableName, Map<String, Object> insertValues) {
        if (SqlRunThreadHolder.isIgnoreLogicDelete()) {
            return;
        }
        if (insertValues.containsKey(dbColumnName)) {
            return;
        }
        if (!PojoCache.isColumnExists(tableName, dbColumnName)) {
            return;
        }
        insertValues.put(dbColumnName, 0);
    }

    /**
     * 逻辑删除执行
     * @param executor
     * @return
     */
    public int doLogicDelete(IExecutorDelete executor) {
        if (SqlRunThreadHolder.isIgnoreLogicDelete()) {
            return -1; // 放行物理 DELETE
        }
        if (!PojoCache.isColumnExists(executor.getTableName(), dbColumnName)) {
            return -1; // 表无逻辑删除字段，放行物理 DELETE
        }

        // DELETE → UPDATE deleted=1
        final TableUpdate update = new TableUpdate(executor.getTableName())
                .set(dbColumnName, 1)
                .where(executor.where());
        if (executor instanceof TableDelete) {
            update.getPara().putAll(((TableDelete) executor).getPara());
        } else if (executor instanceof PojoUpdate) {
            final ParaMap pm = ((PojoUpdate) executor).getPm();
            update.getPara().putAll(pm.getPara());
        }
        return DBHolder.doDb(s -> s.execute(update));
    }
    /**
     * 逻辑删除执行
     * @return
     */
    public Field getLogicDeleteField(String tableName, Class<?> beanClass) {
        if (SqlRunThreadHolder.isIgnoreLogicDelete()) {
            return null; // 忽略不做逻辑删除处理
        }
        if (!PojoCache.isColumnExists(tableName, dbColumnName)) {
            return null; // 表无逻辑删除字段
        }
        return PojoCache.getLogicDeleteInfo(beanClass, fieldName);
    }

    /**
     * 逻辑删除执行
     * @return
     */
    public String getLogicDeleteField(String tableName) {
        if (SqlRunThreadHolder.isIgnoreLogicDelete()) {
            return null; // 忽略不做逻辑删除处理
        }
        if (!PojoCache.isColumnExists(tableName, dbColumnName)) {
            return null; // 表无逻辑删除字段
        }
        return fieldName;
    }
}
