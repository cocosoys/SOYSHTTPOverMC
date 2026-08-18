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
import java.util.*;
import java.util.stream.Collectors;

public class DbOpSqlite extends SqlHelper {


    @Override
    public void createTable(String tableName, Class<?> clazz) {
        String createSql = "CREATE TABLE IF NOT EXISTS `{}` ({})";
        final String columns = FieldReflections.getFields(clazz).stream().map(field -> {
            String columnName = PojoCache.getColumnName(field);
            if (columnName.equals("")) {
                return null;
            }
            String column = " `" + columnName + "` " + getDbColumnType(field);
            if (PojoCache.isColumnPk(field)) {
                column += " PRIMARY KEY";
                TableId tableId = field.getAnnotation(TableId.class);
                if (tableId != null && tableId.type() == IdType.AUTO) {
                    column += " AUTOINCREMENT";
                }
            }
            return column;
        }).filter(Objects::nonNull)
        .collect(Collectors.joining(","));

//        String tableComment = BeanInfoHolder.getTableComment(clazz);
//        if(StringUtils.isNotEmpty(tableComment)){
//            tableComment = " COMMENT '" + tableComment + "'";
//        }

        String sql = StringUtils.formatMsg(createSql, tableName,columns);

        DBHolder.getSqlExecutor().update(sql);
    }

    @Override
    public Set<String> getTableColumnNames(String tableName) {
        // 获取表所有字段
        String sql = "PRAGMA TABLE_INFO(`" + tableName + "`)";
        List<ResultMap> maps = DBHolder.doDao(w->w.getList(sql));
        Set<String> re = new HashSet();
        maps.forEach(item -> re.add(ValUtil.toStr(item.get("name"), "").toUpperCase()));
        return re;
    }

    @Override
    public TableInfo getTableInfo(String tableName) {
        // 获取表注释
//        String sql = "SELECT table_comment FROM table_comments WHERE table_name = ?";
//        String tableComment = DBHolder.getDao().getFirstColumn(sql, String.class, tableName);

        // 构建查询主键的SQL语句
        String sql = "PRAGMA table_info(`" + tableName + "`)";
        // 执行查询并获取结果
        List<ResultMap> maps = DBHolder.getSqlExecutor().getList(sql);
        List<String> primaryKeys = new ArrayList<>();

        for (ResultMap map : maps) {
            int pk = ValUtil.toInt(map.get("pk"), 0);
            if (pk == 1) {
                primaryKeys.add(ValUtil.toStr(map.get("name"), ""));
            }
        }

        // 获取字段信息（PRAGMA table_info 含 notnull/dflt_value/pk）
        sql = "PRAGMA TABLE_INFO(`" + tableName + "`)";
        maps = DBHolder.getSqlExecutor().getList(sql);
        List<ColumnInfo> columnInfos = new ArrayList<>();

        for (ResultMap map : maps) {
            ColumnInfo columnInfo = new ColumnInfo();
            columnInfo.setColumnName(ValUtil.toStr(map.get("name"), ""));
            columnInfo.setColumnType(ValUtil.toStr(map.get("type"), ""));
            // SQLite doesn't support column comments directly, so set it to empty
            columnInfo.setColumnComment("");
            // 转换字段类型为Java类型
            columnInfo.setJavaType(getJavaType(columnInfo.getColumnType()));
            // 6 个新字段
            int notnull = ValUtil.toInt(map.get("notnull"), 0);
            columnInfo.setNullable(notnull == 0);
            columnInfo.setDefaultValue(ValUtil.toStr(map.get("dflt_value")));
            columnInfo.setAutoIncrement(false); // SQLite AUTOINCREMENT 后期可从 sql 解析
            columnInfo.setColumnSize(0); // SQLite 类型不强制长度
            columnInfo.setDecimalDigits(0);
            int pkFlag = ValUtil.toInt(map.get("pk"), 0);
            columnInfo.setPrimaryKey(pkFlag > 0);
            columnInfos.add(columnInfo);
        }

        TableInfo tableInfo = new TableInfo();
        tableInfo.setTableName(tableName);
//        tableInfo.setTableComment(tableComment);
        tableInfo.setColumnInfos(columnInfos);
        tableInfo.setPrimaryKeys(primaryKeys);

        return tableInfo;
    }

    @Override
    public void createColumn(String tableName, String name, Field field) {
        String sql = "ALTER TABLE `" + tableName + "` ADD COLUMN `" + name + "` " + getDbColumnType(field);
        DBHolder.getSqlExecutor().update(sql);
    }

    //    1.NULL：空值。
    //    2.INTEGER：带符号的整型，具体取决有存入数字的范围大小。
    //    3.REAL：浮点数字，存储为8-byte IEEE浮点数。
    //    4.TEXT：字符串文本。
    //    5.BLOB：二进制对象。
    @Override
    public String getDbColumnType(Field field) {
        Class<?> classs = field.getType();
        if (classs == String.class) {
            return "TEXT";
        } else if (classs == Boolean.class || classs == Integer.class || "int".equals(classs.getCanonicalName())) {
            return "INTEGER";
        } else if ("boolean".equals(classs.getCanonicalName())) {
            return "TEXT";
        } else if (classs == Long.class || "long".equals(classs.getCanonicalName())) {
            return "INTEGER";
        } else if (Number.class.isAssignableFrom(classs)) {
            return "REAL";
        } else if (Date.class.isAssignableFrom(classs)) {
            return "TEXT";
        }
        return "TEXT";
    }
    private Class<?> getJavaType(String columnType) {
        if (columnType.equalsIgnoreCase("TEXT")) {
            return String.class;
        } else if (columnType.equalsIgnoreCase("INTEGER")) {
            return Integer.class;
        } else if (columnType.equalsIgnoreCase("REAL")) {
            return Double.class;
        } else if (columnType.equalsIgnoreCase("BLOB")) {
            return byte[].class;
        } else {
            return Object.class; // 默认类型
        }
    }
}
