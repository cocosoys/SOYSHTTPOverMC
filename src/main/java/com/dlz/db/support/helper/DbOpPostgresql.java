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
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class DbOpPostgresql extends SqlHelper {
    @Override
    public void createTable(String tableName, Class<?> clazz) {
        final String columns = FieldReflections.getFields(clazz).stream().map(field -> {
                    String columnName = PojoCache.getColumnName(field);
                    String column = null;
                    if (columnName.equals("")) {
                        return column;
                    }
                    column = "\"" + columnName + "\" " + getDbColumnType(field);
                    if (PojoCache.isColumnPk(field)) {
                        TableId tableId = field.getAnnotation(TableId.class);
                        if (tableId != null && tableId.type() == IdType.AUTO) {
                            // PostgreSQL AUTO 类型使用 SERIAL
                            Class<?> fieldType = field.getType();
                            if (fieldType == Long.class || "long".equals(fieldType.getCanonicalName())) {
                                column = "\"" + columnName + "\" BIGSERIAL";
                            } else {
                                column = "\"" + columnName + "\" SERIAL";
                            }
                        }
                        column += " PRIMARY KEY";
                    }
                    return column;
                }).filter(Objects::nonNull)
                .collect(Collectors.joining(","));

        String sql = "CREATE TABLE IF NOT EXISTS public.\"" + tableName + "\" (" + columns + ")";
        DBHolder.getSqlExecutor().update(sql);
        String columnComment = PojoCache.getTableComment(clazz);
        if (StringUtils.isNotEmpty(columnComment)) {
            sql = "COMMENT ON TABLE \"public\".\"" + tableName + "\" IS '" + columnComment + "'";
            DBHolder.getSqlExecutor().update(sql);
        }
    }

    @Override
    public Set<String> getTableColumnNames(String tableName) {
        // 获取表所有字段
        String sql = "SELECT column_name as name FROM information_schema.columns WHERE table_schema='public' AND table_name='" + tableName.toLowerCase() + "'";
        List<ResultMap> maps = DBHolder.getSqlExecutor().getList(sql);
        Set<String> re = new HashSet();
        maps.forEach(item -> re.add(ValUtil.toStr(item.get("name"), "").toUpperCase()));
        return re;
    }


    @Override
    public TableInfo getTableInfo(String tableName) {
        // 构建查询表注释的SQL语句
        String sql = "SELECT obj_description('public." + tableName + "'::regclass) AS TABLE_COMMENT";
        // 执行查询并获取结果
        TableInfo tableInfo = new TableInfo();
        tableInfo.setTableName(tableName);
        tableInfo.setTableComment(DBHolder.getSqlExecutor().getFirstColumn(sql, String.class));

        // 构建查询主键的SQL语句
        sql = "SELECT kcu.column_name " +
                "FROM information_schema.table_constraints tc " +
                "JOIN information_schema.key_column_usage kcu " +
                "ON tc.constraint_name = kcu.constraint_name " +
                "AND tc.table_schema = kcu.table_schema " +
                "WHERE tc.table_schema = 'public' " +
                "AND tc.table_name = ? " +
                "AND tc.constraint_type = 'PRIMARY KEY'";
        // 执行查询并获取结果
        List<ResultMap> maps = DBHolder.getSqlExecutor().getList(sql, tableName);
        List<String> primaryKeys = new ArrayList<>();
        for (ResultMap map : maps) {
            primaryKeys.add(ValUtil.toStr(map.get("column_name"), ""));
        }
        tableInfo.setPrimaryKeys(primaryKeys);


        // 构建查询字段信息的SQL语句（补全 nullable/defaultValue/atttypmod 推导 size/digits）
        sql = "SELECT a.attname AS COLUMN_NAME, pg_catalog.format_type(a.atttypid, a.atttypmod) AS COLUMN_TYPE, " +
                "pg_catalog.col_description(a.attrelid, a.attnum) AS COLUMN_COMMENT, " +
                "a.attnotnull AS IS_NOT_NULL, pg_get_expr(d.adbin, d.adrelid) AS COLUMN_DEFAULT, " +
                "a.atttypmod, t.typtype, t.typname " +
                "FROM pg_catalog.pg_attribute a " +
                "JOIN pg_catalog.pg_class c ON a.attrelid = c.oid " +
                "JOIN pg_catalog.pg_namespace n ON c.relnamespace = n.oid " +
                "JOIN pg_catalog.pg_type t ON a.atttypid = t.oid " +
                "LEFT JOIN pg_catalog.pg_attrdef d ON a.attrelid = d.adrelid AND a.attnum = d.adnum " +
                "WHERE c.relname = ? AND n.nspname = 'public' AND a.attnum > 0 AND NOT a.attisdropped " +
                "ORDER BY a.attnum";
        // 执行查询并获取结果
        maps = DBHolder.getSqlExecutor().getList(sql, tableName);
        List<ColumnInfo> columnInfos = new ArrayList<>();

        for (ResultMap map : maps) {
            ColumnInfo columnInfo = new ColumnInfo();
            columnInfo.setColumnName(ValUtil.toStr(map.get("COLUMN_NAME"), ""));
            columnInfo.setColumnType(ValUtil.toStr(map.get("COLUMN_TYPE"), ""));
            columnInfo.setColumnComment(ValUtil.toStr(map.get("COLUMN_COMMENT"), ""));
            // 转换字段类型为Java类型
            columnInfo.setJavaType(getJavaType(columnInfo.getColumnType()));
            // 6 个新字段
            Object notNullObj = map.get("IS_NOT_NULL");
            boolean notNull = Boolean.TRUE.equals(notNullObj) || "t".equals(ValUtil.toStr(notNullObj)) || "true".equalsIgnoreCase(ValUtil.toStr(notNullObj));
            columnInfo.setNullable(!notNull);
            columnInfo.setDefaultValue(ValUtil.toStr(map.get("COLUMN_DEFAULT")));
            columnInfo.setAutoIncrement(false); // PG 用 SERIAL/IDENTITY，attidentity 字段可后期补
            // PG 的 atttypmod 对 varchar 存储为 size+4（varchar 头部开销），对 numeric 存储为 (precision<<16)|scale+4
            int atttypmod = ValUtil.toInt(map.get("atttypmod"), -1);
            String typname = ValUtil.toStr(map.get("typname"), "");
            if (atttypmod > 0 && typname.contains("varchar")) {
                columnInfo.setColumnSize(atttypmod - 4);
            } else if (atttypmod > 0 && (typname.contains("numeric") || typname.contains("decimal"))) {
                int precision = (atttypmod - 4) >> 16;
                int scale = (atttypmod - 4) & 0xFFFF;
                columnInfo.setColumnSize(precision);
                columnInfo.setDecimalDigits(scale);
            }
            // primaryKey 由 TableInfo.primaryKeys 推导
            columnInfo.setPrimaryKey(primaryKeys != null && primaryKeys.contains(columnInfo.getColumnName()));
            columnInfos.add(columnInfo);
        }
        tableInfo.setColumnInfos(columnInfos);
        return tableInfo;
    }

    @Override
    public void createColumn(String tableName, String name, Field field) {
        String sql = "ALTER TABLE public." + tableName + " ADD COLUMN " + name + " " + getDbColumnType(field);
        DBHolder.getSqlExecutor().update(sql);
        String columnComment = PojoCache.getColumnComment(field);
        if (StringUtils.isNotEmpty(columnComment)) {
            sql = "COMMENT ON COLUMN " + tableName + "." + name + " IS '" + columnComment + "'";
            DBHolder.getSqlExecutor().update(sql);
        }
    }


    @Override
    public String getDbColumnType(Field field) {
        Class<?> classs = field.getType();
        if (classs == String.class) {
            return "varchar(255)";
        } else if (classs == Integer.class || "int".equals(classs.getCanonicalName())) {
            return "int2";
        } else if (classs == Boolean.class || "boolean".equals(classs.getCanonicalName())) {
            return "bool";
        } else if (classs == Long.class || "long".equals(classs.getCanonicalName())) {
            return "int4";
        } else if (Number.class.isAssignableFrom(classs)) {
            return "numeric(12, 1)";
        } else if (Date.class.isAssignableFrom(classs) || classs == LocalDateTime.class || classs == LocalDate.class) {
            return "date";
        }
        return "text";
    }

    private Class<?> getJavaType(String columnType) {
        if (columnType.toLowerCase().startsWith("varchar") || columnType.toLowerCase().startsWith("char")) {
            return String.class;
        } else if (columnType.toLowerCase().startsWith("int") || columnType.toLowerCase().startsWith("integer")) {
            return Integer.class;
        } else if (columnType.toLowerCase().startsWith("boolean")) {
            return Boolean.class;
        } else if (columnType.toLowerCase().startsWith("bigint")) {
            return Long.class;
        } else if (columnType.toLowerCase().startsWith("decimal") || columnType.toLowerCase().startsWith("numeric")) {
            return Double.class;
        } else if (columnType.toLowerCase().startsWith("date")) {
            return Date.class;
        } else if (columnType.toLowerCase().startsWith("timestamp")) {
            return LocalDateTime.class;
        } else {
            return Object.class; // 默认类型
        }
    }
}
