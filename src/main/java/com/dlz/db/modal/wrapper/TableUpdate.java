package com.dlz.db.modal.wrapper;

import com.dlz.db.inf.IExecutorUDI;
import com.dlz.db.interceptor.DbPlugin;
import com.dlz.db.modal.DB;
import com.dlz.db.modal.para.AQuery;
import com.dlz.db.support.DBHolder;
import com.dlz.db.support.PojoCache;
import com.dlz.db.support.bean.IdInfo;
import com.dlz.db.util.DbConvertUtil;
import com.dlz.db.util.SqlUtil;
import com.dlz.kit.fn.DlzFn;
import com.dlz.kit.json.JSONMap;
import com.dlz.kit.util.system.FieldReflections;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;


/**
 * 构造单表的更新操作sql
 *
 * @author dingkui
 */
@Slf4j
public class TableUpdate extends AQuery<TableUpdate> implements IExecutorUDI {
    private static final long serialVersionUID = 8374167270612933157L;
    final Map<String, Object> updateSets = new HashMap<>();

    public TableUpdate(String tableName) {
        super(tableName);
    }

    public TableUpdate set(String paraName, Object value) {
        paraName = DbConvertUtil.toDbColumnName(paraName);
        if (!PojoCache.isColumnExists(getTableName(),paraName)) {
            log.warn("column is not exists:" + getTableName() + "." + paraName);
            return this;
        }
        updateSets.put(paraName, value);
        return this;
    }

    public <T> void set(DlzFn<T, ?> column, Object value) {
        set(PojoCache.fnName(column), value);
    }

    /**
     * 添加要更新的值集合
     *
     * @param setValues
          */
    public TableUpdate set(Map<String, Object> setValues) {
        return set(setValues,null);
    }

    public TableUpdate set(Map<String, Object> setValues, Function<String, Boolean> ignore) {
        for (String str : setValues.keySet()) {
            Object fieldValue = setValues.get(str);
            if (fieldValue != null) {
                if (ignore != null && ignore.apply(str)) {
                    continue;
                }
                set(str, setValues.get(str));
            }
        }
        return this;
    }

    @Override
    public TableUpdate me() {
        return this;
    }

    @Override
    public String getSql() {
        return WrapperBuildUtil.MAKER_SQL_UPDATE;
    }
    public boolean batch(List<JSONMap> valueBeans) {
        return batch(valueBeans, 1000);
    }
    public boolean batch(List<JSONMap> valueBeans, int batchSize) {
        if (valueBeans.isEmpty()) {
            return false;
        }
        final String tableName = getTableName();
        final String idName = PojoCache.getIdName(tableName);
        final HashMap<String, Integer> dbColumns = PojoCache.getTableColumnsInfo(tableName);
        final HashMap<String, Integer> dbColumnsF = new LinkedHashMap<>(dbColumns.size());
        dbColumns.entrySet().forEach(entry -> dbColumnsF.put(entry.getKey(), entry.getValue()));
        String sql = WrapperBuildUtil.buildUpdateSql(tableName, dbColumnsF, DbConvertUtil.toDbColumnName(idName));
        final HashMap<String, Integer> fields = new LinkedHashMap<>(dbColumns.size());
        dbColumns.entrySet().forEach(entry -> fields.put(DbConvertUtil.toFieldName(entry.getKey()), entry.getValue()));
        // Pojo 批量插入走原生 JDBC（buildInsertSql(dbColumns) + batch），不经过 TableInsert.buildInsertSql
        final String logicDeleteField = DbPlugin.getLogicDeleteField(tableName);

        while (!valueBeans.isEmpty() && batchSize > 0) {
            if (batchSize > valueBeans.size()) {
                batchSize = valueBeans.size();
            }
            final List<JSONMap> ts = valueBeans.subList(0, batchSize);

            List<Object[]> paramValues = ts.stream()
                    .map(v -> {
                        if(logicDeleteField!=null && v.get(logicDeleteField)==null){
                            v.set(logicDeleteField, 0);
                        }
                        return WrapperBuildUtil.buildUpdateParams(v, fields, idName);
                    })
                    .collect(Collectors.toList());
            DBHolder.getSqlExecutor().batch(sql, paramValues);
            log.info(SqlUtil.getRunSqlByJdbc(sql, paramValues.get(0)).trim());
            valueBeans = valueBeans.subList(batchSize, valueBeans.size());
        }
        return true;
    }
}
