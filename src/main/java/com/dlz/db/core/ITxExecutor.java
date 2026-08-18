package com.dlz.db.core;

import com.dlz.db.exception.DbException;
import com.dlz.db.util.DbLogUtil;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Supplier;

/**
 * 事务执行器接口。
 * <p>抽象事务执行逻辑，支持不同框架的事务实现（Spring、Solon 等）。</p>
 *
 * <p>事务传播级别：仅支持 REQUIRED（默认）</p>
 * <p>异常处理：任何异常都会触发回滚</p>
 */
//@FunctionalInterface
public interface ITxExecutor {

    /**
     * 在事务中执行任务。
     * <p>如果执行过程中抛出异常，事务将回滚；否则提交。</p>
     *
     * @param task 要执行的任务
     * @param <T>  返回值类型
     * @return 任务执行结果
     * @throws Exception 执行失败或事务异常
     */
    default <T> T execute(Supplier<T> task) throws Exception {
        DataSource dataSource = getDataSource();
        // 已在事务中：直接复用外层连接，不重复 begin/commit
        if (hasBinding(dataSource)) {
            return runInExisting(task);
        }
        return runNewTransaction(dataSource, task);
    }

    DataSource getDataSource();
    boolean hasBinding(DataSource dataSource);
    void bind(DataSource dataSource,Connection connection);
    void unBind(DataSource dataSource);



    default <T> T runInExisting(Supplier<T> task) {
        try {
            return task.get();
        } catch (DbException e) {
            throw e;
        } catch (Exception e) {
            throw new DbException("事务执行失败：" + e.getMessage(), 1003, e);
        }
    }

    default <T> T runNewTransaction(DataSource dataSource, Supplier<T> task) throws Exception {
        Connection connection = null;
        boolean bound = false;
        boolean prevAutoCommit = true;
        try {
            connection = dataSource.getConnection();
            prevAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            bind(dataSource, connection);
            bound = true;

            T result = task.get();
            connection.commit();
            return result;
        } catch (Exception e) {
            if (connection != null) {
                try {
                    if (!connection.isClosed()) {
                        connection.rollback();
                    }
                } catch (SQLException ex) {
                    DbLogUtil.warn("连接回滚失败", ex);
                }
            }
            if (e instanceof DbException) {
                throw new DbException("事务执行失败：" + e.getMessage(), 1006, e);
            }
            throw new DbException("事务执行失败：" + e.getMessage(), 1003, e);
        } finally {
            if (bound) {
                unBind(dataSource);
            }
            if (connection != null) {
                try {
                    if (!connection.isClosed()) {
                        connection.setAutoCommit(prevAutoCommit);
                    }
                } catch (Exception e) {
                    DbLogUtil.warn("还原 autoCommit 失败", e);
                }
                try {
                    if (!connection.isClosed()) {
                        connection.close();
                    }
                } catch (Exception e) {
                    DbLogUtil.warn("关闭连接失败", e);
                }
            }
        }
    }

    /**
     * 在事务中执行无返回值的任务。
     *
     * @param task 要执行的任务
     * @throws Exception 执行失败或事务异常
     */
    default void execute(Runnable task) throws Exception {
        execute(() -> {
            task.run();
            return null;
        });
    }
}
