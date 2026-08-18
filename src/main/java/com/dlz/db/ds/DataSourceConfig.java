package com.dlz.db.ds;

import com.dlz.db.convertor.rowMapper.MySqlColumnMapRowMapper;
import com.dlz.db.convertor.rowMapper.OracleColumnMapRowMapper;
import com.dlz.db.convertor.rowMapper.ResultMapRowMapper;
import com.dlz.db.enums.DbTypeEnum;
import com.dlz.db.support.helper.*;
import com.dlz.kit.exception.SystemException;
import com.dlz.kit.util.ValUtil;
import lombok.AccessLevel;
import lombok.Setter;

import javax.sql.DataSource;

public class DataSourceConfig {
    protected final DataSourceProperty property;

    public DataSourceConfig(DataSourceProperty property) {
        this.property = property;
    }

    @Setter(AccessLevel.NONE)
    private ResultMapRowMapper rowMapper;

    public ResultMapRowMapper getRowMapper() {
        if (rowMapper != null) {
            return rowMapper;
        }
        DbTypeEnum dbType = getDbType();
        if (dbType == DbTypeEnum.MYSQL || dbType == DbTypeEnum.POSTGRESQL) {
            rowMapper = new MySqlColumnMapRowMapper();
        } else if (dbType == DbTypeEnum.ORACLE || dbType == DbTypeEnum.DM8) {
            rowMapper = new OracleColumnMapRowMapper();
        } else {
            rowMapper = new ResultMapRowMapper();
        }
        return rowMapper;
    }

    @Setter
    private DataSource dataSource;

    public DataSource getDataSource() {
        if (dataSource != null) {
            return dataSource;
        }
        try {
            IDataSourceCreator dataSourceCreator;
            if (ValUtil.isEmpty(property.getCreatorClassName())) {
                dataSourceCreator = new DataSourceCreatorHikari();
            } else {
                dataSourceCreator = (IDataSourceCreator) Class.forName(property.getCreatorClassName()).newInstance();
            }
            dataSource = dataSourceCreator.createDataSource(property);
            // 创建新的数据源
            return dataSource;
        } catch (Exception e) {
            throw new RuntimeException("取得数据源失败: " + e.getMessage(), e);
        }
    }

    private DbTypeEnum dbType;

    public DbTypeEnum getDbType() {
        if (dbType != null) {
            return dbType;
        }
        final String lowerCase = property.getUrl().toLowerCase();
        if (lowerCase.contains(":mysql")
                || lowerCase.contains(":mariadb")) {
            dbType = DbTypeEnum.MYSQL;
        } else if (lowerCase.contains(":postgresql")) {
            dbType = DbTypeEnum.POSTGRESQL;
        } else if (lowerCase.contains(":oracle")) {
            dbType = DbTypeEnum.ORACLE;
        } else if (lowerCase.contains(":dm")) {
            dbType = DbTypeEnum.DM8;
        } else if (lowerCase.contains(":sqlite")) {
            dbType = DbTypeEnum.SQLITE;
        } else if (lowerCase.contains(":sqlserver")) {
            dbType = DbTypeEnum.MSSQL;
        } else if (lowerCase.contains(":h2")) {
            dbType = DbTypeEnum.H2;
        } else {
            throw new SystemException("未识别的数据库类型:" + property.getUrl());
        }
        return dbType;
    }

    private SqlHelper helper;
    public SqlHelper getSqlHelper() {
        if (helper != null) {
            return helper;
        }
        final DbTypeEnum dbType = getDbType();

        if (dbType == DbTypeEnum.SQLITE) {
            helper = new DbOpSqlite();
        } else if (dbType == DbTypeEnum.POSTGRESQL) {
            helper = new DbOpPostgresql();
        } else if (dbType == DbTypeEnum.ORACLE || dbType == DbTypeEnum.DM8) {
            helper = new DbOpDm8();
        } else {
            helper = new DbOpMysql();
        }
        return helper;
    }

    public void close() throws Exception {
        if (dataSource != null) {
            if (dataSource instanceof AutoCloseable) {
                ((AutoCloseable) dataSource).close();
            }
            dataSource = null;
        }
        if (rowMapper != null) {
            rowMapper = null;
        }
    }

    public String getName() {
        return property.getName();
    }
}
