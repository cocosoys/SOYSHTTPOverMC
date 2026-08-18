package com.dlz.db.interceptor;

import com.dlz.db.inf.IExecutorDelete;
import com.dlz.db.modal.condition.Condition;
import com.dlz.db.modal.wrapper.WrapperBuildUtil;
import com.dlz.db.support.PojoCache;
import com.dlz.db.support.SqlRunThreadHolder;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SQL 构建拦截器。
 *
 * <p>在 {@link com.dlz.db.modal.wrapper.WrapperBuildUtil} 的关键节点被调用，
 * 用于自动注入 WHERE 条件、插入字段值、或改写删除操作。
 *
 * <p>内置实现：
 * <ul>
 *   <li>{@link LogicDeleteInterceptor} — 逻辑删除</li>
 * </ul>
 * 后续可扩展：租户隔离、数据权限等。
 *
 * <h3>使用示例</h3>
 * <pre>
 * // 注册插件（启动时一次）
 * WrapperBuildUtil.registerInterceptor(new LogicDeleteInterceptor());
 *
 * // 后续所有 DB.Table 操作自动经过插件链
 * DB.Table.select("user").eq("id", 1).queryOne();
 * // ↑ LogicDeleteInterceptor.onBuildWhere 自动追加 deleted=0
 * </pre>
 *
 * @author dingkui
 * @since 7.1.0
 */
@Slf4j
public class DbPlugin {

    // ==================== 插件机制 ====================

    /**
     * 已注册的 SQL 构建拦截器列表。
     * 使用 CopyOnWriteArrayList 保证遍历时不加锁（注册频率低，遍历频率高）。
     */
    private static final List<SqlBuildInterceptor> interceptors = new CopyOnWriteArrayList<>();
    private static LogicDeleteInterceptor logicDeleteInterceptor;

    /**
     * 注册一个 SQL 构建拦截器。
     * <p>通常在应用启动时调用（如 Spring @PostConstruct）。
     *
     * @param interceptor 拦截器实例
     */
    public static void registerInterceptor(SqlBuildInterceptor interceptor) {
        if (interceptor != null) {
            if(interceptor.isEnabled()){
                if(interceptor instanceof LogicDeleteInterceptor){
                    logicDeleteInterceptor = (LogicDeleteInterceptor) interceptor;
                }
                interceptors.add(interceptor);
                log.info("Registered SqlBuildInterceptor: {}", interceptor.getClass().getName());
            }
        }
    }

    /**
     * 移除所有已注册的拦截器（主要用于测试场景重置状态）。
     */
    public static void clearInterceptors() {
        interceptors.clear();
    }

    /**
     * 获取已注册的拦截器列表（只读视图）。
     * <p>供 {@link com.dlz.db.inf.IExecutorDelete#execute()} 等处遍历调用。
     */
    public static List<SqlBuildInterceptor> getInterceptors() {
        return interceptors;
    }

    /**
     * 获取已注册的拦截器数量。
     */
    public static int getInterceptorCount() {
        return interceptors.size();
    }

    // ==================== SQL 构建 ====================
    /**
     * 生成查询条件sql
     * <p>先调用所有启用的插件的 {@link SqlBuildInterceptor#onBuildWhere}，
     * 再生成最终 WHERE 子句。
     */
    public static void onBuildWhere(String tableName, Condition where) {
        // 调用插件链：逻辑删除/租户/权限 等自动注入 WHERE 条件
        for (SqlBuildInterceptor interceptor : interceptors) {
            interceptor.onBuildWhere(tableName, where);
        }
    }
    /**
     * 构建插入语句
     * <p>先调用所有启用的插件的 {@link SqlBuildInterceptor#onBuildInsert}，
     * 再生成最终 INSERT 子句。
     */
    public static void onBuildInsert(String tableName, Map<String, Object> insertValues) {
        // 调用插件链：逻辑删除/租户 等自动注入插入字段
        for (SqlBuildInterceptor interceptor : interceptors) {
             interceptor.onBuildInsert(tableName, insertValues);
        }
    }
    /**
     * 逻辑删除处理，逻辑删除插件生效则进行逻辑删除拦截
     * @param maker
     * @return 逻辑删除结果 -1 放行物理 DELETE, 其他 逻辑删除条数
     */
    public static int doLogicDelete(IExecutorDelete maker) {
        // 调用插件：逻辑删除/租户 等自动注入插入字段
        // 调用插件链：逻辑删除插件会在此将 DELETE 改写为 UPDATE deleted=1
        if(logicDeleteInterceptor == null){
            return -1;
        }
        return logicDeleteInterceptor.doLogicDelete(maker);
    }
    /**
     */
    public static Field getLogicDeleteField(String tableName, Class<?> beanClass) {
        // 调用插件：逻辑删除/租户 等自动注入插入字段
        // 调用插件链：逻辑删除插件会在此将 DELETE 改写为 UPDATE deleted=1
        if(logicDeleteInterceptor == null){
            return null;
        }
        return logicDeleteInterceptor.getLogicDeleteField(tableName, beanClass);
    }
    /**
     */
    public static String getLogicDeleteField(String tableName) {
        // 调用插件：逻辑删除/租户 等自动注入插入字段
        // 调用插件链：逻辑删除插件会在此将 DELETE 改写为 UPDATE deleted=1
        if(logicDeleteInterceptor == null){
            return null;
        }
        return logicDeleteInterceptor.getLogicDeleteField(tableName);
    }
}
