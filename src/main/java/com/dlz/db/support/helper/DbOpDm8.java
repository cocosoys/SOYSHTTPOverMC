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

public class DbOpDm8 extends SqlHelper {
    @Override
    public void createTable(String tableName, Class<?> clazz) {
        // 达梦数据库表名需大写
        final String columns = FieldReflections.getFields(clazz).stream().map(field -> {
                    String columnName = PojoCache.getColumnName(field);
                    String column = null;
                    if (columnName.equals("")) {
                        return column;
                    }
                    column = " \"" + columnName.toUpperCase() + "\" " + getDbColumnType(field);
                    if (PojoCache.isColumnPk(field)) {
                        column += " PRIMARY KEY";
                        TableId tableId = field.getAnnotation(TableId.class);
                        if (tableId != null && tableId.type() == IdType.AUTO) {
                            column += " IDENTITY";
                        }
                    }
                    return column;
                }).filter(Objects::nonNull)
                .collect(Collectors.joining(","));

        String sql = StringUtils.formatMsg("CREATE TABLE \"{}\" ({});", tableName,columns);

        String tableComment = PojoCache.getTableComment(clazz);
        if (StringUtils.isNotEmpty(tableComment)) {
            sql += "; COMMENT ON TABLE \"" + tableName.toUpperCase() + "\" IS '" + tableComment + "'";
        }

        final String columnsComment = FieldReflections.getFields(clazz).stream().map(field -> {
                    String columnName = PojoCache.getColumnName(field);
                    String columnComment = PojoCache.getColumnComment(field);
                    String column = null;
                    if (columnName.equals("") && StringUtils.isEmpty(columnComment)) {
                        return column;
                    }
                    return "COMMENT ON COLUMN \"" + tableName.toUpperCase() + "\".\"" + columnName.toUpperCase() + "\" IS '" + columnComment + "'";
                }).filter(Objects::nonNull)
                .collect(Collectors.joining(";"));
        if (StringUtils.isNotEmpty(columnsComment)) {
            sql += ";"+columnsComment;
        }


        DBHolder.getSqlExecutor().update(sql);
    }

    @Override
    public Set<String> getTableColumnNames(String tableName) {
        // 达梦系统表查询字段信息
        String sql = "SELECT COLUMN_NAME FROM ALL_TAB_COLUMNS WHERE OWNER = USER AND TABLE_NAME = ?";
        return DBHolder.getSqlExecutor().getList(sql, tableName.toUpperCase())
                .stream()
                .map(item -> item.getStr("columnName"))
                .collect(Collectors.toSet());
    }

    @Override
    public TableInfo getTableInfo(String tableName) {
        TableInfo tableInfo = new TableInfo();
        tableInfo.setTableName(tableName);

        // 查询表注释
        String sql = "SELECT COMMENTS FROM ALL_TAB_COMMENTS WHERE OWNER = USER AND TABLE_NAME = ?";
        tableInfo.setTableComment(DBHolder.getSqlExecutor().getFirstColumn(sql, String.class, tableName.toUpperCase()));

        // 查询主键信息
        sql = "SELECT COLUMN_NAME" +
                "  FROM ALL_CONSTRAINTS C" +
                "  JOIN ALL_CONS_COLUMNS CC" +
                "    ON C.CONSTRAINT_NAME = CC.CONSTRAINT_NAME" +
                " WHERE C.OWNER = USER" +
                "   AND C.TABLE_NAME = ?" +
                "   AND C.CONSTRAINT_TYPE = 'P'";
        List<String> primaryKeys = DBHolder.getSqlExecutor().getList(sql, tableName.toUpperCase())
                .stream()
                .map(map -> map.getStr("columnName", ""))
                .collect(Collectors.toList());
        tableInfo.setPrimaryKeys(primaryKeys);


        // 查询字段信息（补全 nullable/defaultValue/dataLength/dataPrecision/dataScale）
        sql="   SELECT A.COLUMN_NAME, " +
                "          A.DATA_TYPE, " +
                "          A.DATA_LENGTH, " +
                "          A.DATA_PRECISION, " +
                "          A.DATA_SCALE, " +
                "          A.NULLABLE, " +
                "          A.DATA_DEFAULT, " +
                "          C.COMMENTS" +
                "     FROM USER_TAB_COLUMNS A" +
                " LEFT JOIN ALL_COL_COMMENTS C " +
                "       ON C.OWNER = USER" +
                "      AND a.TABLE_NAME = C.TABLE_NAME " +
                "      AND a.COLUMN_NAME = C.COLUMN_NAME" +
                "    WHERE A.TABLE_NAME = ?";
        List<ColumnInfo> columnInfos = DBHolder.getSqlExecutor().getList(sql, tableName.toUpperCase())
                .stream()
                .map(map -> {
                    ColumnInfo columnInfo = new ColumnInfo();
                    columnInfo.setColumnName(map.getStr("columnName", ""));
                    columnInfo.setColumnType(map.getStr("dataType", ""));
                    columnInfo.setColumnComment(map.getStr("comments", ""));
                    columnInfo.setJavaType(getJavaType(columnInfo.getColumnType()));
                    // 6 个新字段
                    // ALL_TAB_COLUMNS.NULLABLE: 'Y' 或 'N'
                    columnInfo.setNullable("Y".equalsIgnoreCase(ValUtil.toStr(map.get("nullable"))));
                    columnInfo.setDefaultValue(ValUtil.toStr(map.get("dataDefault")));
                    columnInfo.setAutoIncrement(false); // DM8 IDENTITY 后期可补
                    // DATA_PRECISION 优先（数值类型），DATA_LENGTH 兜底（字符类型）
                    int precision = ValUtil.toInt(map.get("dataPrecision"), 0);
                    if (precision > 0) {
                        columnInfo.setColumnSize(precision);
                        columnInfo.setDecimalDigits(ValUtil.toInt(map.get("dataScale"), 0));
                    } else {
                        columnInfo.setColumnSize(ValUtil.toInt(map.get("dataLength"), 0));
                    }
                    columnInfo.setPrimaryKey(primaryKeys != null && primaryKeys.contains(columnInfo.getColumnName()));
                    return columnInfo;
                })
                .collect(Collectors.toList());
        tableInfo.setColumnInfos(columnInfos);
        return tableInfo;
    }
    @Override
    public void createColumn(String tableName, String name, Field field) {
        String sql = "ALTER TABLE \"" + tableName.toUpperCase() + "\" ADD \"" + name.toUpperCase() + "\" " + getDbColumnType(field);
        String columnComment = PojoCache.getColumnComment(field);
        if (StringUtils.isNotEmpty(columnComment)) {
            sql += "; COMMENT ON COLUMN \"" + tableName.toUpperCase() + "\".\"" + name.toUpperCase() + "\" IS '" + columnComment + "'";
        }
        DBHolder.getSqlExecutor().update(sql);
    }

    @Override
    public String getDbColumnType(Field field) {
        Class<?> clazz = field.getType();
        if (clazz == String.class) {
            return "varchar(255)"; // 达梦推荐使用VARCHAR2
        } else if (clazz == Integer.class || "int".equals(clazz.getCanonicalName())) {
            return "NUMBER(10)"; // 整数类型
        } else if (clazz == Boolean.class || "boolean".equals(clazz.getCanonicalName())) {
            return "NUMBER(1)"; // 布尔类型
        } else if (clazz == Long.class || "long".equals(clazz.getCanonicalName())) {
            return "NUMBER(19)"; // 长整型
        } else if (Number.class.isAssignableFrom(clazz)) {
            return "NUMBER(12, 2)"; // 数值类型
        } else if (Date.class.isAssignableFrom(clazz) || clazz == LocalDateTime.class || clazz == LocalDate.class) {
            return "TIMESTAMP"; // 时间类型
        }
        return "CLOB"; // 默认大文本类型
    }

    private Class<?> getJavaType(String columnType) {
        columnType = columnType.toLowerCase();
        if (columnType.contains("char") || columnType.startsWith("clob") || columnType.startsWith("text")) {
            return String.class;
        } else if (columnType.startsWith("int")) {
            return Integer.class;
        } else if (columnType.startsWith("tinyint")) {
            return Boolean.class;
        } else if (columnType.startsWith("bigint")) {
            return Long.class;
        } else if (columnType.startsWith("decimal") || columnType.startsWith("numeric") || columnType.startsWith("number")) {
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
