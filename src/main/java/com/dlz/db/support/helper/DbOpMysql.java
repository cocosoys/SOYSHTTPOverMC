package com.dlz.db.support.helper;

import com.dlz.db.annotation.IdType;
import com.dlz.db.annotation.TableId;
import com.dlz.db.modal.dto.ResultMap;
import com.dlz.db.support.DBHolder;
import com.dlz.db.support.PojoCache;
import com.dlz.db.support.bean.ColumnInfo;
import com.dlz.db.support.bean.TableInfo;
import com.dlz.kit.util.StringUtils;
import com.dlz.kit.util.ValUtil;
import com.dlz.kit.util.system.FieldReflections;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class DbOpMysql extends SqlHelper {
    /*
    CREATE TABLE `ds_info`  (
        `id` varchar(32) NOT NULL PRIMARY KEY,
        `PLUGIN_ID` varchar(255) COMMENT '插件id',
        `NAME` varchar(255) COMMENT '数据源名称',
        `URL` varchar(255) COMMENT '数据源连接地址',
        `USERNAME` varchar(255) COMMENT '数据源用户名',
        `PASSWORD` varchar(255) COMMENT '数据源密码',
        `JSON` varchar(255) COMMENT '其他定义',
        `DESCRIPTION` varchar(255) COMMENT '数据源描述',
        `TYPE` varchar(255) COMMENT '数据源类型',
        `STATUS` varchar(255) COMMENT '数据源状态'
    )COMMENT = 'xxxx';
*/
    @Override
    public void createTable(String tableName, Class<?> clazz) {
        String createSql = "CREATE TABLE IF NOT EXISTS `{}` ({}){}";
        final String columns = FieldReflections.getFields(clazz).stream().map(field -> {
            String columnName = PojoCache.getColumnName(field);
            String column = null;
            if (columnName.equals("")) {
                return column;
            }
            column = " `" + columnName + "` " + getDbColumnType(field);
            String columnComment = PojoCache.getColumnComment(field);
            if (StringUtils.isNotEmpty(columnComment)) {
                column += " COMMENT '" + columnComment + "'";
            }
            if (PojoCache.isColumnPk(field)) {
                column += " PRIMARY KEY";
                TableId tableId = field.getAnnotation(TableId.class);
                if (tableId != null && tableId.type() == IdType.AUTO) {
                    column += " AUTO_INCREMENT";
                }
            }
            return column;
        }).filter(Objects::nonNull)
                .collect(Collectors.joining(","));

        String tableComment = PojoCache.getTableComment(clazz);
        if (StringUtils.isNotEmpty(tableComment)) {
            tableComment = " COMMENT = '" + tableComment  + "'";
        }else{
            tableComment = "";
        }

        String sql = StringUtils.formatMsg(createSql, tableName,columns,tableComment);

        DBHolder.getSqlExecutor().update(sql);
    }

    @Override
    public Set<String> getTableColumnNames(String tableName) {
        // 构建查询字段信息的SQL语句
        String sql = "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?";
        // 执行查询并获取结果
        return DBHolder.getSqlExecutor().getList(sql, tableName).stream().map(item -> item.getStr("columnName")).collect(Collectors.toSet());
    }

    @Override
    public TableInfo getTableInfo(String tableName) {
        // 构建查询表注释的SQL语句
        String sql = "SELECT TABLE_COMMENT FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?";
        // 执行查询并获取结果
        TableInfo tableInfo = new TableInfo();
        tableInfo.setTableName(tableName);
        tableInfo.setTableComment(DBHolder.getSqlExecutor().getFirstColumn(sql, String.class, tableName));

        // 获取主键信息
        // 构建查询主键的SQL语句
        sql = "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND CONSTRAINT_NAME = 'PRIMARY'";
        // 执行查询并获取结果
        List<String> primaryKeys = DBHolder.getSqlExecutor().getList(sql, tableName).stream().map(map -> map.getStr("columnName", "")).collect(Collectors.toList());
        tableInfo.setPrimaryKeys(primaryKeys);

        // 构建查询字段信息的SQL语句（补全 nullable/defaultValue/autoIncrement/columnSize/decimalDigits）
        sql = "SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_COMMENT, IS_NULLABLE, COLUMN_DEFAULT, EXTRA, CHARACTER_MAXIMUM_LENGTH, NUMERIC_PRECISION, NUMERIC_SCALE " +
                "FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?";
        // 执行查询并获取结果
        List<ColumnInfo> columnInfos = DBHolder.getSqlExecutor().getList(sql, tableName).stream().map(map -> {
            ColumnInfo columnInfo = new ColumnInfo();
            columnInfo.setColumnName(map.getStr("columnName", ""));
            columnInfo.setColumnType(map.getStr("columnType", ""));
            columnInfo.setColumnComment(map.getStr("columnComment", ""));
            // 转换字段类型为Java类型
            columnInfo.setJavaType(getJavaType(columnInfo.getColumnType()));
            // 6 个新字段
            columnInfo.setNullable("YES".equalsIgnoreCase(map.getStr("isNullable", "")));
            columnInfo.setDefaultValue(map.getStr("columnDefault"));
            String extra = map.getStr("extra", "");
            columnInfo.setAutoIncrement(extra != null && extra.toLowerCase().contains("auto_increment"));
            columnInfo.setColumnSize(ValUtil.toInt(map.get("characterMaximumLength"), 0));
            // numericPrecision 对应 DECIMAL(10,2) 的 10，作为 columnSize 兜底
            if (columnInfo.getColumnSize() == 0) {
                columnInfo.setColumnSize(ValUtil.toInt(map.get("numericPrecision"), 0));
            }
            columnInfo.setDecimalDigits(ValUtil.toInt(map.get("numericScale"), 0));
            // primaryKey 由 TableInfo.primaryKeys 推导
            columnInfo.setPrimaryKey(primaryKeys != null && primaryKeys.contains(columnInfo.getColumnName()));
            return columnInfo;
        }).collect(Collectors.toList());
        tableInfo.setColumnInfos(columnInfos);
        return tableInfo;
    }

    @Override
    public void createColumn(String tableName, String name, Field field) {
        String sql = "ALTER TABLE `" + tableName + "` ADD COLUMN `" + name + "` " + getDbColumnType(field);
        String columnComment = PojoCache.getColumnComment(field);
        if (StringUtils.isNotEmpty(columnComment)) {
            sql += " COMMENT '" + columnComment + "'";
        }
        DBHolder.getSqlExecutor().update(sql);
    }

    @Override
    public String getDbColumnType(Field field) {
        Class<?> classs = field.getType();
        if (classs == String.class) {
            return "varchar(255)";
        } else if (classs == Integer.class || "int".equals(classs.getCanonicalName())) {
            return "int";
        } else if (classs == Boolean.class || "boolean".equals(classs.getCanonicalName())) {
            return "tinyint";
        } else if (classs == Long.class || "long".equals(classs.getCanonicalName())) {
            return "bigint";
        } else if (Number.class.isAssignableFrom(classs)) {
            return "numeric(12, 1)";
        } else if (Date.class.isAssignableFrom(classs)||classs== LocalDateTime.class||classs== LocalDate.class) {
            return "datetime";
        }
        return "text";
    }
    private Class<?> getJavaType(String columnType) {
        columnType = columnType.toLowerCase();
        if (columnType.startsWith("varchar") || columnType.startsWith("char")) {
            return String.class;
        } else if (columnType.startsWith("int")) {
            return Integer.class;
        } else if (columnType.startsWith("tinyint")) {
            return Boolean.class;
        } else if (columnType.startsWith("bigint")) {
            return Long.class;
        } else if (columnType.startsWith("decimal") || columnType.startsWith("numeric")) {
            return Double.class;
        } else if (columnType.startsWith("date") || columnType.startsWith("datetime") || columnType.startsWith("timestamp")) {
            return Date.class;
//        } else if (columnType.startsWith("timestamp")) {
//            return LocalDateTime.class;
        } else {
            return Object.class; // 默认类型
        }
    }
}
