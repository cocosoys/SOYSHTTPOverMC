package com.dlz.db.modal.wrapper;

import com.dlz.db.inf.ICondAddByLamda;
import com.dlz.db.inf.IExecutorUDI;
import com.dlz.db.inf.ISqlQuery;
import com.dlz.db.modal.para.APojoQuery;
import com.dlz.db.support.DBHolder;
import com.dlz.db.support.PojoCache;
import com.dlz.db.support.bean.IdInfo;
import com.dlz.kit.exception.ValidateException;
import com.dlz.kit.fn.DlzFn;
import com.dlz.kit.util.system.FieldReflections;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 更新语句生成器
 *
 * @author dk
 *
 */
public class PojoUpdate<T> extends APojoQuery<PojoUpdate<T>, T, TableUpdate> implements
        ISqlQuery<PojoUpdate<T>>,
        ICondAddByLamda<PojoUpdate<T>, T>,
        IExecutorUDI {
    public PojoUpdate(Class<T> beanClass) {
        super(beanClass);
        setPm(new TableUpdate(getTableName()));
    }

    public PojoUpdate<T> set(DlzFn<T, ?> column, Object value) {
        getPm().set(column, value);
        return this;
    }

    public PojoUpdate<T> set(String column, Object value) {
        getPm().set(column, value);
        return this;
    }

    public PojoUpdate<T> set(Map<String, Object> setValues) {
        getPm().set(setValues);
        return this;
    }

    /**
     * 以原生 SQL 片段更新字段，形如 "col = expr"。
     * 内部等价于 {@code set(col, "sql:" + expr)}，复用 sql: 前缀分支。
     * <p>⚠️ expr 为原生 SQL，严禁拼接外部输入。
     * <pre>
     *   .setSql("score = score + 10")
     *   .setSql("view_count = view_count + 1")
     * </pre>
     */
    public PojoUpdate<T> setSql(String sqlFragment) {
        if (sqlFragment == null || sqlFragment.trim().isEmpty()) {
            return this;
        }
        int eq = sqlFragment.indexOf('=');
        if (eq <= 0) {
            throw new ValidateException("setSql 需要 'col = expr' 形式: " + sqlFragment);
        }
        String col = sqlFragment.substring(0, eq).trim();
        String expr = sqlFragment.substring(eq + 1).trim();
        return set(col, "sql:" + expr);
    }

    public PojoUpdate<T> set(T bean, Function<String, Boolean> ignore) {
        List<Field> fields = FieldReflections.getFields(bean.getClass());
        for (Field field : fields) {
            Object fieldValue = FieldReflections.getValue(bean, field);
            if (fieldValue != null) {
                final String columnName = PojoCache.getColumnName(field);
                if (ignore != null && ignore.apply(columnName)) {
                    continue;
                }
                getPm().set(columnName, fieldValue);
            }
        }
        return this;
    }

    public PojoUpdate<T> set(T bean) {
        final IdInfo idInfo = PojoCache.getIdInfo(getBeanClass());
        return set(bean, name -> name.equals(idInfo.getName()));
    }

    @Override
    public PojoUpdate<T> me() {
        return this;
    }

    public boolean batch(List<T> valueBeans) {
        return batch(valueBeans, 1000);
    }

    public boolean batch(List<T> valueBeans, int batchSize) {
        if (valueBeans.isEmpty()) {
            return false;
        }
        final Class<T> beanClass = getBeanClass();
        String dbName = PojoCache.getTableName(beanClass);
        final IdInfo idInfo = PojoCache.getIdInfo(beanClass);
        final List<Field> fields = PojoCache.getBeanFields(getBeanClass());
        String sql = WrapperBuildUtil.buildUpdateSql(dbName, fields, idInfo.getName());
        while (!valueBeans.isEmpty() && batchSize > 0) {
            if (batchSize > valueBeans.size()) {
                batchSize = valueBeans.size();
            }
            final List<T> ts = valueBeans.subList(0, batchSize);

            List<Object[]> paramValues = ts.stream()
                    .map(v -> WrapperBuildUtil.buildUpdateParams(v, fields, idInfo.getField()))
                    .collect(Collectors.toList());
            DBHolder.getSqlExecutor().batch(sql, paramValues);
            valueBeans = valueBeans.subList(batchSize, valueBeans.size());
        }
        return true;
    }
}
