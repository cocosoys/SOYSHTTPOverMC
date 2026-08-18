package com.dlz.db.modal.wrapper;

import com.dlz.db.inf.IExecutorInsert;
import com.dlz.db.interceptor.DbPlugin;
import com.dlz.db.modal.para.AParaTable;
import com.dlz.db.support.DBHolder;
import com.dlz.db.support.PojoCache;
import com.dlz.db.util.DbConvertUtil;
import com.dlz.kit.fn.DlzFn;
import com.dlz.kit.json.JSONMap;
import com.dlz.kit.util.system.FieldReflections;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 构造单表的添加操作sql
 *
 * @author dingkui
 */
@Slf4j
public class TableInsert extends AParaTable implements IExecutorInsert {
    private static final long serialVersionUID = 8374167270612933157L;
    public final Map<String, Object> insertValues = new HashMap<>();
    public TableInsert(String tableName) {
        super(tableName);
    }

    @Override
    public String getSql() {
        return WrapperBuildUtil.MAKER_SQL_INSERT;
    }

    public <T> void value(DlzFn<T, ?> column, Object value) {
        value(PojoCache.fnName(column), value);
    }

    public TableInsert value(String key, Object value) {
        String paraName = DbConvertUtil.toDbColumnName(key);
        if (!PojoCache.isColumnExists(getTableName(), paraName)) {
            log.warn("column is not exists:" + getTableName() + "." + paraName);
            return this;
        }
        insertValues.put(paraName, value);
        return this;
    }

    public TableInsert value(Map<String, Object> values) {
        for (String str : values.keySet()) {
            value(str, values.get(str));
        }
        return this;
    }
    public boolean batch(List<JSONMap> valueBeans) {
        return batch(valueBeans, 1000);
    }

    public boolean batch(List<JSONMap> valueBeans, int batchSize) {
        if (valueBeans.isEmpty()) {
            return false;
        }
        final String tableName = getTableName();
//        final String idName = PojoCache.getIdName(tableName);

        final HashMap<String, Integer> dbColumns = PojoCache.getTableColumnsInfo(tableName);
        String sql = WrapperBuildUtil.buildInsertSql(tableName, dbColumns);

        // Pojo 批量插入走原生 JDBC（buildInsertSql(dbColumns) + batch），不经过 TableInsert.buildInsertSql
        final String logicDeleteField = DbPlugin.getLogicDeleteField(tableName);


        final HashMap<String, Integer> fields = new LinkedHashMap<>(dbColumns.size());
        dbColumns.entrySet().forEach(entry -> fields.put(DbConvertUtil.toFieldName(entry.getKey()), entry.getValue()));

        while (!valueBeans.isEmpty() && batchSize > 0) {
            if (batchSize > valueBeans.size()) {
                batchSize = valueBeans.size();
            }
            final List<JSONMap> ts = valueBeans.subList(0, batchSize);
            List<Object[]> paramValues = ts.stream()
                    .map(v -> {
                        if(logicDeleteField!=null){
                            v.set(logicDeleteField, 0);
                        }
                        return WrapperBuildUtil.buildInsertParams(v, fields);
                    })
                    .collect(Collectors.toList());
            DBHolder.getSqlExecutor().batch(sql, paramValues);
            valueBeans = valueBeans.subList(batchSize, valueBeans.size());
        }
        return true;
    }
}
