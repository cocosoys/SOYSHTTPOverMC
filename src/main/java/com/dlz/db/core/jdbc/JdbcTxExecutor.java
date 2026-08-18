package com.dlz.db.core.jdbc;

import com.dlz.db.core.DlzConnectionHolder;
import com.dlz.db.core.ITxExecutor;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * 简单的事务执行器 - 基于 SQLite 的真实事务支持
 */
@Slf4j
public class JdbcTxExecutor implements ITxExecutor {

    private final DataSource dataSource;

    public JdbcTxExecutor(DataSource dataSource) {
        this.dataSource = dataSource;
    }


    @Override
    public boolean hasBinding(DataSource dataSource) {
        return DlzConnectionHolder.hasBinding(dataSource);
    }


    @Override
    public DataSource getDataSource() {
        return dataSource;
    }

    @Override
    public void bind(DataSource dataSource, Connection connection) {
        DlzConnectionHolder.bind(dataSource, connection);
    }

    @Override
    public void unBind(DataSource dataSource) {
        DlzConnectionHolder.unbind(dataSource);
    }
}