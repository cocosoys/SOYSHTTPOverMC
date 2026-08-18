package com.dlz.db.core;

import com.dlz.db.ds.DataSourceConfig;
import com.dlz.db.modal.DB;
import com.dlz.db.support.SqlHolder;
import com.dlz.db.support.helper.HelperScan;
import com.dlz.db.util.DbLogUtil;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 测试用 SQLite 数据库提供者 - 使用真实 SQLite 数据库
 * 用于单元测试，提供真实的数据库连接
 */
@Slf4j
public class DlzDbAdapter{
    private ISqlExecutor sqlExecutor;
    private final Supplier<DataSource> dataSourceMaker;
    private final Supplier<ISqlExecutor> sqlExecutorMaker;
    private final DlzDbProperties sqlConfig;
    private final Function<DataSource, ITxExecutor> txExecutorMaker;

    public DlzDbAdapter(DlzDbProperties sqlConfig,
                        Supplier<DataSource> dataSourceMaker,
                        Supplier<ISqlExecutor> sqlExecutorMaker,
                        Function<DataSource, ITxExecutor> txExecutorMaker) {
        // 初始化配置
        this.sqlConfig = sqlConfig;
        this.dataSourceMaker = dataSourceMaker;
        this.sqlExecutorMaker = sqlExecutorMaker;
        this.txExecutorMaker = txExecutorMaker;
    }


    public ITxExecutor createTxExecutor(DataSourceConfig dataSourceConfig) {
        return txExecutorMaker.apply(dataSourceConfig.getDataSource());
    }

    public ISqlExecutor getSqlExecutor() {
        if (sqlExecutor == null) {
            synchronized (DlzDbAdapter.class) {
                if (sqlExecutor == null) {
                    DB.Dynamic.setDefaultDataSource(dataSourceMaker.get());

                    final ISqlExecutor sqlExecutor1 = sqlExecutorMaker.get();
                    DbLogUtil.init(sqlConfig);

                    SqlHolder.init();

                    SqlHolder.loadDbSql();
                    this.sqlExecutor = sqlExecutor1;
                    // 自动更新数据库结构
                    if (sqlConfig.getHelper().isAutoUpdate()) {
                        log.info("dlzHelper autoUpdate ...");
                        HelperScan.scan(sqlConfig.getHelper().getPackageName());
                    }
                    log.info("dlzHelper init finish.");
                }
            }
        }
        return sqlExecutor;
    }


    public DlzDbProperties getSqlConfig() {
        return sqlConfig;
    }
}