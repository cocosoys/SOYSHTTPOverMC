package com.dlz.db.interceptor;

import com.dlz.db.modal.condition.Condition;
import com.dlz.db.modal.para.AQuery;
import com.dlz.db.modal.wrapper.TableInsert;

import java.util.Map;

/**
 * SQL 构建拦截器。
 *
 * <p>在 {@link com.dlz.db.modal.wrapper.WrapperBuildUtil} 的关键节点被调用，
 * 用于自动注入 WHERE 条件、插入字段值、或改写删除操作。
 *
 * <p>内置实现：
 * <ul>
 *   <li>{@link com.dlz.db.interceptor.LogicDeleteInterceptor} — 逻辑删除</li>
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
public interface SqlBuildInterceptor {

    /**
     * 插件是否启用。
     *
     * <p>每次调用点都会检查，返回 false 则跳过该插件的所有逻辑。
     * 可用于运行时动态开关（如按配置、按租户模式决定是否启用）。
     *
     * @return true 启用，false 跳过
     */
    boolean isEnabled();

    /**
     * 查询/更新/删除的 WHERE 子句构建时调用。
     *
     * <p>用于追加自动条件，例如：
     * <ul>
     *   <li>逻辑删除：{@code deleted = 0}</li>
     *   <li>租户隔离：{@code tenant_id = ?}</li>
     *   <li>数据权限：{@code owner_id = ?}</li>
     * </ul>
     *
     * <p>实现方应先检查表是否存在目标字段、WHERE 是否已包含该条件，避免重复注入。
     *
     * @param maker 当前查询/更新/删除构造器
     */
    default void onBuildWhere(String tableName, Condition where) {
        // 默认空实现，子类按需覆盖
    }

    /**
     * 插入的字段构建时调用。
     *
     * <p>用于自动追加字段值，例如：
     * <ul>
     *   <li>逻辑删除：{@code deleted = 0}</li>
     *   <li>租户隔离：{@code tenant_id = ?}（覆盖调用方传入的值）</li>
     *   <li>通用信息添加：{@code owner_id = ?，create_time = ? ...}（覆盖调用方传入的值）</li>
     * </ul>
     *
     * <p>实现方应先检查表是否存在目标字段，避免无效注入。
     *
     * @param tableName 表名
     * @param insertValues 插入字段值
     */
    default void onBuildInsert(String tableName, Map<String, Object> insertValues) {
        // 默认空实现，子类按需覆盖
    }
}
