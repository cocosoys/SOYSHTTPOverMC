package com.dlz.db.core.jdbc;

import com.dlz.db.core.DlzConnectionHolder;
import com.dlz.db.core.ISqlExecutor;
import com.dlz.db.core.anno.ConnectionSupplier;
import com.dlz.db.modal.DB;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * SQLite SQL 执行器 - 基于真实 JDBC 连接
 */
@Slf4j
public class JdbcSqlExecutor implements ISqlExecutor {
    @Override
    public ConnectionSupplier getConnectionSupplier() {
        return () -> {
            DataSource ds = DB.Dynamic.getDataSource();
            // 1. 优先复用 dlz-db 自身事务连接（DB.Tx.run）
            Connection bound = DlzConnectionHolder.get(ds);
            if (bound != null) {
                return wrapNoClose(bound);
            }
            // 3. 获取新连接
            log.debug("获取新连接");
            return ds.getConnection();
        };
    }


    /**
     * 包装一个 close() 无操作的连接代理，避免 try-with-resources 关闭事务连接。
     */
    protected Connection wrapNoClose(Connection real) {
        return (Connection) java.lang.reflect.Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if ("close".equals(method.getName()) && (args == null || args.length == 0)) {
                        return null;
                    }
                    try {
                        return method.invoke(real, args);
                    } catch (java.lang.reflect.InvocationTargetException e) {
                        throw e.getTargetException();
                    }
                });
    }

}