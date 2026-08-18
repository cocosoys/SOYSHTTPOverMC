package com.dlz.db.modal.wrapper;

import com.dlz.db.annotation.IdType;
import com.dlz.db.convertor.dbtype.TableColumnMapper;
import com.dlz.db.interceptor.DbPlugin;
import com.dlz.db.interceptor.SqlBuildInterceptor;
import com.dlz.db.modal.para.AParaTable;
import com.dlz.db.modal.para.AQuery;
import com.dlz.db.support.DBHolder;
import com.dlz.db.support.PojoCache;
import com.dlz.db.support.SqlRunThreadHolder;
import com.dlz.db.support.bean.IdInfo;
import com.dlz.db.util.DbConvertUtil;
import com.dlz.kit.exception.SystemException;
import com.dlz.kit.json.JSONMap;
import com.dlz.kit.util.StringUtils;
import com.dlz.kit.util.system.FieldReflections;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;


/**
 * sql操作Util
 *
 * @author ding_kui 2010-12-14
 */
@Slf4j
public class WrapperBuildUtil {
    public static final String MAKER_SQL_INSERT = "insert into ${tableName}(${columns}) values(${values})";
    public static final String MAKER_SQL_DELETE = "delete from ${tableName} ${where}";
    public static final String MAKER_SQL_UPDATE = "update ${tableName} set ${sets} ${where}";
    public static final String MAKER_SQL_SEARCH = "select ${columns} from ${tableName} t ${where} ${otherwhere}";

    private static final String MAKER_TABLE_NAME = "tableName";
    private static final String MAKER_COLUMNS = "columns";
    private static final String MAKER_VALUES = "values";
    private static final String MAKER_STR_SETS = "sets";
    private static final String MAKER_WHERE = "where";


    /**
     * 生成查询条件sql
     *
     */
    public static void buildSql(AParaTable maker) {
        maker.getSqlItem().setSqlKey(maker.getSql());
        maker.addPara(MAKER_TABLE_NAME, maker.getTableName());
        if (maker instanceof AQuery) {
            buildWhere((AQuery) maker);
        }
        if (maker instanceof TableQuery) {
            buildWhereColumns((TableQuery) maker);
        }
        if (maker instanceof TableUpdate) {
            buildUpdateSql((TableUpdate) maker);
        }
        if (maker instanceof TableInsert) {
            buildInsertSql((TableInsert) maker);
        }
    }

    /**
     * 生成查询条件sql
     *
     */
    public static void buildWhereColumns(TableQuery maker) {
        maker.addPara(MAKER_COLUMNS, maker.columns);
    }

    /**
     * 生成查询条件sql
     * <p>先调用所有启用的插件的 {@link SqlBuildInterceptor#onBuildWhere}，
     * 再生成最终 WHERE 子句。
     */
    public static void buildWhere(AQuery maker) {
        // 调用插件链：逻辑删除/租户/权限 等自动注入 WHERE 条件
        DbPlugin.onBuildWhere( maker.getTableName(), maker.where());
        String where = maker.where().getRunsql(maker);
        if (!maker.isAllowFullQuery() && StringUtils.isEmpty(where)) {
            where = "where false";
        }
        maker.addPara(MAKER_WHERE, where);
    }

    /**
     * 生成掺入sql
     * <p>先调用所有启用的插件的 {@link SqlBuildInterceptor#onBuildInsert}，
     * 再生成最终 INSERT 语句。
     */
    public static void buildInsertSql(TableInsert maker) {
        StringBuilder sbColumns = new StringBuilder();
        StringBuilder sbValues = new StringBuilder();
        final Map<String, Object> insertValues = maker.insertValues;
        if (insertValues.isEmpty()) {
            throw new SystemException("插入字段信息未设置");
        }
        // 调用插件链：逻辑删除/租户 等自动注入插入字段
        final String tableName = maker.getTableName();
        DbPlugin.onBuildInsert(tableName, insertValues);
        for (Map.Entry<String, Object> entry : insertValues.entrySet()) {
            String paraName = entry.getKey();
            Object value = entry.getValue();
            String columnName = paraName.replaceAll("`", "");

            if (sbColumns.length() > 0) {
                sbColumns.append(',');
                sbValues.append(',');
            }
            sbColumns.append(paraName);
            if (appendSql(sbValues, value, columnName)) continue;
            if (value == null)
                value = "";
            maker.addPara(columnName, DbConvertUtil.getVal4Db(tableName, columnName, value));
        }
        maker.addPara(MAKER_COLUMNS, sbColumns.toString());
        maker.addPara(MAKER_VALUES, sbValues.toString());
        maker.addPara(MAKER_TABLE_NAME, tableName);
    }

    /**
     * 生成更新信息
     *
     */
    public static void buildUpdateSql(TableUpdate maker) {
        StringBuilder sbSets = new StringBuilder();
        if (maker.updateSets.isEmpty()) {
            throw new SystemException("更新字段信息未设置");
        }
        for (Map.Entry<String, Object> entry : maker.updateSets.entrySet()) {
            String paraName = entry.getKey();
            Object value = entry.getValue();
            String columnName = paraName.replaceAll("`", "");

            if (sbSets.length() > 0) {
                sbSets.append(",");
            }
            sbSets.append(paraName);
            sbSets.append('=');
            if (appendSql(sbSets, value, columnName)) continue;
            maker.addPara(columnName, DbConvertUtil.getVal4Db(maker.getTableName(), columnName, value));
        }
        maker.addPara(MAKER_STR_SETS, sbSets.toString());
    }

    private static boolean appendSql(StringBuilder sbSets, Object value, String columnName) {
        if (value instanceof String) {
            String v = (String) value;
            if (v.startsWith("sql:")) {
                sbSets.append(DbConvertUtil.toDbColumnNames(v.substring(4)));
                return true;
            }
        }
        sbSets.append("#{").append(columnName).append("}");
        return false;
    }


    public static String buildInsertSql(String dbName, List<Field> fields) {
        List<String> fieldsPart = new ArrayList<>();
        List<String> placeHolder = new ArrayList<>();
        for (Field field : fields) {
            String dbColumnName = PojoCache.getColumnName(field);
            if (!dbColumnName.equals("")) {
                fieldsPart.add(dbColumnName);
                placeHolder.add("?");
            }
        }
        return "INSERT INTO " + dbName + " (" + StringUtils.join(",", fieldsPart) + ") VALUES (" + StringUtils.join(",", placeHolder) + ")";
    }
    public static String buildInsertSql(String dbName, HashMap<String, Integer> fields) {
        List<String> fieldsPart = new ArrayList<>();
        List<String> placeHolder = new ArrayList<>();
        for (String dbColumnName : fields.keySet()) {
            if (!dbColumnName.equals("")) {
                fieldsPart.add(dbColumnName);
                placeHolder.add("?");
            }
        }
        return "INSERT INTO " + dbName + " (" + StringUtils.join(",", fieldsPart) + ") VALUES (" + StringUtils.join(",", placeHolder) + ")";
    }

    public static Object[] buildInsertParams(Object object, List<Field> fields) {
        List<Object> params = new ArrayList<>();
        for (Field field : fields) {
            String dbColumnName = PojoCache.getColumnName(field);
            if (!dbColumnName.equals("")) {
                params.add(FieldReflections.getValue(object, field));
            }
        }
        return params.toArray();
    }

    public static Object[] buildInsertParams(JSONMap object, HashMap<String, Integer> fields) {
        List<Object> params = new ArrayList<>();
        for (Map.Entry<String, Integer> field : fields.entrySet()) {
             params.add(TableColumnMapper.cover(field.getValue(),object.get(field.getKey())));
        }
        return params.toArray();
    }

    public static String buildUpdateSql(String dbName, List<Field> fields, String idName) {
        List<String> fieldsPart = new ArrayList<>();
        for (Field field : fields) {
            String dbColumnName = PojoCache.getColumnName(field);
            if (!dbColumnName.equals(idName) && !dbColumnName.equals("")) {
                fieldsPart.add(dbColumnName + "=?");
            }
        }
        return "UPDATE " + dbName + " SET " + StringUtils.join(",", fieldsPart) + " WHERE " + idName + " = ?";
    }

    public static Object[] buildUpdateParams(Object object, List<Field> fields, Field idField) {
        final Object value = FieldReflections.getValue(object, idField);
        if(value == null){
            throw new SystemException("更新操作"+idField.getName()+"不能为空");
        }
        List<Object> params = new ArrayList<>(fields.size());
        for (Field field : fields) {
            if (idField != field && !PojoCache.getColumnName(field).equals("")) {
                params.add(FieldReflections.getValue(object, field));
            }
        }
        params.add(value);
        return params.toArray();
    }


    public static String buildUpdateSql(String dbName, HashMap<String, Integer> fields, String idName) {
        List<String> fieldsPart = new ArrayList<>();
        for (String dbColumnName : fields.keySet()) {
            if (!dbColumnName.equals(idName) && !dbColumnName.equals("")) {
                fieldsPart.add(dbColumnName + "=?");
            }
        }
        return "UPDATE " + dbName + " SET " + StringUtils.join(",", fieldsPart) + " WHERE " + idName + " = ?";
    }

    public static Object[] buildUpdateParams(JSONMap object, HashMap<String, Integer> fields, String idName) {
        final Object value = object.get(idName);
        if(value == null){
            throw new SystemException("更新操作"+idName+"不能为空");
        }
        List<Object> params = new ArrayList<>(fields.size());
        for (Map.Entry<String, Integer> field : fields.entrySet()) {
            if (!idName.equals(field.getKey())) {
                params.add(TableColumnMapper.cover(field.getValue(),object.get(field.getKey())));
            }
        }
        params.add(value);
        return params.toArray();
    }

    public static void fillAutoId(String dbName, IdInfo idInfo, Object obj) {
        final IdType idType = idInfo.getType();
        if (idType == null){
            return;
        }
        Object idValue = idInfo.getValue(obj);
        if(idValue != null){
            return;
        }
        if (idType == IdType.INPUT) {
            throw new SystemException(obj.getClass().getSimpleName() + "." + idInfo.getField().getName() + "为手动输入,不能为空");
        }
        if (idType == IdType.AUTO) {
            return;
        }
        if (idType == IdType.SEQ) {
            idInfo.setId(obj, DBHolder.sequence(dbName, 1l));
        }else{
            idInfo.setId(obj, idType.mkId());
        }
    }
    public static void fillAutoIds(String dbName, IdInfo idInfo, List<?> obj) {
        final IdType idType = idInfo.getType();
        if (idType == null || idType == IdType.AUTO) {
            return;
        }
        
        // 单次遍历：收集需要生成ID的对象
        List<Object> needIdList = new ArrayList<>();
        for (Object o : obj) {
            if (idInfo.getValue(o) == null) {
                needIdList.add(o);
            }
        }
        
        if (needIdList.isEmpty()) {
            return;
        }
        
        int count = needIdList.size();
        
        if (idType == IdType.INPUT) {
            throw new SystemException(obj.getClass().getSimpleName() + "." + idInfo.getName() + "为手动输入,不能为空");
        }
        
        if (idType == IdType.SEQ) {
            // 批量预取：只调用1次 Redis INCRBY，然后在内存中分配
            long startSeq = DBHolder.sequence(dbName, count);
            for (int i = 0; i < count; i++) {
                idInfo.setId(needIdList.get(i), startSeq + i);
            }
        } else {
            // UUID/Snowflake等，逐个生成
            for (Object o : needIdList) {
                idInfo.setId(o, idType.mkId());
            }
        }
    }

}
