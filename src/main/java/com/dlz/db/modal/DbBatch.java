package com.dlz.db.modal;

import com.dlz.db.modal.wrapper.PojoInsert;
import com.dlz.db.modal.wrapper.PojoUpdate;
import com.dlz.db.modal.wrapper.TableInsert;
import com.dlz.db.modal.wrapper.TableUpdate;
import com.dlz.db.support.DBHolder;
import com.dlz.kit.json.JSONMap;

import java.util.List;

public class DbBatch {
    // Pojo 批量处理
    public <T> boolean pojoInsert(List<T> bean) {
        return pojoInsert(bean, 1000);
    }

    public <T> boolean pojoInsert(List<T> bean, int batchSize) {
        if (!bean.isEmpty()) {
            return new PojoInsert(bean.get(0)).batch(bean, batchSize);
        }
        return false;
    }
    public <T> boolean pojoUpdate(List<T> bean) {
        return pojoUpdate(bean, 1000);
    }

    public <T> boolean pojoUpdate(List<T> bean, int batchSize) {
        if (!bean.isEmpty()) {
            final Class<T> aClass = (Class<T>)bean.get(0).getClass();
            return new PojoUpdate(aClass).batch(bean, batchSize);
        }
        return false;
    }

    // Table 批量处理

    public boolean tableInsert(String tableName, List<JSONMap> bean) {
        return tableInsert(tableName, bean, 1000);
    }

    public boolean tableInsert(String tableName, List<JSONMap> bean, int batchSize) {
        if (!bean.isEmpty()) {
            return new TableInsert(tableName).batch(bean, batchSize);
        }
        return false;
    }
    public boolean tableUpdate(String tableName, List<JSONMap> bean) {
        return tableUpdate(tableName,bean, 1000);
    }

    public boolean tableUpdate(String tableName, List<JSONMap> bean, int batchSize) {
        if (!bean.isEmpty()) {
            return new TableUpdate(tableName).batch(bean, batchSize);
        }
        return false;
    }

    // JdbcSql 批量处理

    public boolean jdbcExecute(String sql, List<Object[]> valueBeans) {
        return jdbcExecute(sql, valueBeans, 1000);
    }

    public boolean jdbcExecute(String sql, List<Object[]> valueBeans, int batchSize) {
        for (; !valueBeans.isEmpty() && batchSize > 0; valueBeans = valueBeans.subList(batchSize, valueBeans.size())) {
            if (batchSize > valueBeans.size()) {
                batchSize = valueBeans.size();
            }
            List<Object[]> paramValues = valueBeans.subList(0, batchSize);
            DBHolder.getSqlExecutor().batch(sql, paramValues);
        }
        return true;
    }
}