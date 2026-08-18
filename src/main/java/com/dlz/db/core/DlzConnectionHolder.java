package com.dlz.db.core;

import com.dlz.db.core.jdbc.JdbcSqlExecutor;
import com.dlz.db.core.jdbc.JdbcTxExecutor;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

/**
 * Dlz 事务连接持有者。
 * <p>用 ThreadLocal 按 {@link DataSource} 维度绑定事务连接，使
 * {@link JdbcTxExecutor} 与 {@link JdbcSqlExecutor} 在同一事务内复用同一连接。</p>
 *
 * <p>使用 {@code Map<DataSource,Connection>} 而非单 Connection，是为了支持
 * 嵌套切换数据源时（如 {@code DB.Dynamic.use("test", ...)}）各自独立的事务连接。</p>
 *
 * @since 7.0.1
 */
public final class DlzConnectionHolder {

    private static final ThreadLocal<Map<DataSource, Connection>> BINDINGS =
            ThreadLocal.withInitial(HashMap::new);

    private DlzConnectionHolder() {
    }

    /** 绑定一个连接到当前线程的指定数据源。 */
    public static void bind(DataSource ds, Connection conn) {
        BINDINGS.get().put(ds, conn);
    }

    /** 解绑当前线程的指定数据源连接（不关闭连接）。 */
    public static Connection unbind(DataSource ds) {
        Map<DataSource, Connection> map = BINDINGS.get();
        Connection conn = map.remove(ds);
        if (map.isEmpty()) {
            BINDINGS.remove();
        }
        return conn;
    }

    /** 获取当前线程绑定到指定数据源的连接，未绑定返回 null。 */
    public static Connection get(DataSource ds) {
        return BINDINGS.get().get(ds);
    }

    /** 是否当前线程对该数据源处于事务中。 */
    public static boolean hasBinding(DataSource ds) {
        return BINDINGS.get().containsKey(ds);
    }
}
