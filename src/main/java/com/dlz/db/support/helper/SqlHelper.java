package com.dlz.db.support.helper;

import com.dlz.db.core.anno.SqlAction;
import com.dlz.db.modal.dto.ResultMap;
import com.dlz.db.support.DBHolder;
import com.dlz.db.support.bean.TableInfo;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public abstract class SqlHelper {
    /**
     * 创建表
     * @param tableName
     * @param clazz
     */
    public abstract void createTable(String tableName, Class<?> clazz);

    /**
     * 获取表所有字段
     * @param tableName
          */
    public abstract Set<String> getTableColumnNames(String tableName);

    /**
     * 获取表详细信息（含字段、主键、注释）
     * @param tableName
          */
    public abstract TableInfo getTableInfo(String tableName);

    /**
     * 根据bean属性创建字段
     * @param tableName
     * @param name
     * @param field
     */
    public abstract void createColumn(String tableName, String name, Field field);

    /**
     * 更新默认值
     * @param tableName
     * @param columnName
     * @param value
     */
    public void updateDefaultValue(String tableName, String columnName, String value){
        String sql = "SELECT COUNT(*) FROM " + tableName + " WHERE `" + columnName + "` IS NULL";
        Long count = DBHolder.getSqlExecutor().getFirstColumn(sql, Long.class);
        if (count > 0) {
            sql = "UPDATE " + tableName + " SET `" + columnName + "` = ? WHERE `" + columnName + "` IS NULL";
            DBHolder.getSqlExecutor().update(sql, value);
        }
    }

    /**
     * 根据属性取得数据库字段属性
     * @param field
          */
    public abstract String getDbColumnType(Field field);

    /**
     * 列出当前库的所有表（跨数据库通用，走 JDBC DatabaseMetaData.getTables()）。
     * <p>返回的 TableInfo 只填 tableName + tableComment，columnInfos 置空（按需调 getTableInfo 取列信息）。</p>
     *
     * @param tableNamePattern 表名通配符（SQL LIKE，如 "admin_%"；null 表示全部）
     * @param types            表类型过滤，JDBC 标准：["TABLE"] / ["VIEW"] / ["TABLE","VIEW"] / null（全部）
     * @return 表信息列表
     */
    public List<TableInfo> listTables(String tableNamePattern, String[] types) {
        return DBHolder.getSqlExecutor().doDb((SqlAction<List<TableInfo>>) conn -> {
            List<TableInfo> result = new ArrayList<>();
            DatabaseMetaData metaData = conn.getMetaData();
            // catalog/schema 传 null 表示按当前连接的默认库查
            try (ResultSet rs = metaData.getTables(conn.getCatalog(), conn.getSchema(), tableNamePattern, types)) {
                while (rs.next()) {
                    TableInfo info = new TableInfo();
                    info.setTableName(rs.getString("TABLE_NAME"));
                    info.setTableComment(rs.getString("REMARKS"));
                    info.setColumnInfos(null); // 列表查询不填列信息，按需调 getTableInfo
                    info.setPrimaryKeys(null);
                    result.add(info);
                }
            }
            return result;
        }, null);
    }
}

