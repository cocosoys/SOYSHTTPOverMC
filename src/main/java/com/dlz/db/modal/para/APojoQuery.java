package com.dlz.db.modal.para;

import com.dlz.db.inf.ICondAddByLamda;
import com.dlz.db.inf.ISqlQuery;
import com.dlz.db.modal.condition.Condition;
import com.dlz.db.support.PojoCache;
import com.dlz.kit.util.StringUtils;
import com.dlz.kit.util.system.FieldReflections;

import java.lang.reflect.Field;
import java.util.List;


/**
 * 构造单表的更新操作sql
 *
 * @author dingkui
 */
public abstract class APojoQuery<ME extends APojoQuery, T, PM extends AQuery>
        extends AParaPojo<T, PM>
        implements ISqlQuery<ME>, ICondAddByLamda<ME, T> {
    protected APojoQuery(Class<T> beanClass) {
        super(beanClass);
    }

    protected APojoQuery(T conditionBean) {
        super(conditionBean);
    }

    public Condition where() {
        return getPm().where();
    }
    public ME where(Condition cond) {
        getPm().where(cond);
        return me();
    }
    public ME where(T bean) {
        this.bean = bean;
        return me();
    }

    public ME setAllowFullQuery(boolean allowFullQuery) {
        getPm().setAllowFullQuery(allowFullQuery);
        return me();
    }
    public boolean isAllowFullQuery() {
        return getPm().isAllowFullQuery();
    }
    @Override
    protected void wrapValues(List<Field> fields, T bean) {
        fields.forEach(field->{
            Object value = FieldReflections.getValue(bean, field);
            if (StringUtils.isNotEmpty(value)) {
                getPm().eq(PojoCache.getColumnName(field), value);
            }
        });
    }
}
