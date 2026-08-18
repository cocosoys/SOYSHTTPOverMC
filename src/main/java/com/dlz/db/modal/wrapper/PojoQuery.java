package com.dlz.db.modal.wrapper;

import com.dlz.db.inf.ICondAddByLamda;
import com.dlz.db.inf.IExecutorQuery;
import com.dlz.db.inf.ISqlPage;
import com.dlz.db.inf.ISqlQuery;
import com.dlz.db.modal.dto.Order;
import com.dlz.db.modal.dto.Page;
import com.dlz.db.modal.items.JdbcItem;
import com.dlz.db.modal.para.APojoQuery;
import com.dlz.db.support.DBHolder;
import com.dlz.db.support.PojoCache;
import com.dlz.kit.fn.DlzFn;

import java.util.List;
import java.util.Map;

/**
 * 查询语句生成器
 *
 * @author dk
 */
public class PojoQuery<T> extends APojoQuery<PojoQuery<T>, T, TableQuery> implements
        ISqlQuery<PojoQuery<T>>,
        ICondAddByLamda<PojoQuery<T>, T>,
        ISqlPage<PojoQuery<T>>,
        IExecutorQuery<PojoQuery<T>> {

    public static <T> PojoQuery<T> wrapper(T conditionBean) {
        return new PojoQuery(conditionBean);
    }

    public static <T> PojoQuery<T> wrapper(Class<T> beanClass) {
        return new PojoQuery(beanClass);
    }

    private PojoQuery(Class<T> beanClass) {
        super(beanClass);
        setPm(new TableQuery(getTableName()));
        setAllowFullQuery(true);
    }

    private PojoQuery(T conditionBean) {
        super(conditionBean);
        setPm(new TableQuery(getTableName()));
        setAllowFullQuery(true);
    }

    public PojoQuery<T> select(String... columns) {
        if (columns.length > 0) {
            getPm().select(columns);
        }
        return this;
    }
    @SuppressWarnings("unchecked")
    public PojoQuery<T> select(DlzFn<T, ?>... columns) {
        if (columns.length > 0) {
            getPm().select(columns);
        }
        return this;
    }
    /**
     * 自动根据map的键值对添加查询条件
     *
     * @param req {key:列名，value:值} key值为列名 可带$前缀，如$eq_key:表示 key=key DbOperateEnum=eq
     *            value值为值
     * @return 返回当前条件对象，支持链式调用
     */
    public PojoQuery<T> auto(Map<String, Object> req) {
        String tableName = PojoCache.getTableName(getBeanClass());
        return auto(req, (key) -> PojoCache.isColumnExists(tableName, key));
    }

    @Override
    public JdbcItem jdbcCnt() {
        generatWithBean(bean);
        return getPm().jdbcCnt();
    }

    @Override
    public PojoQuery<T> me() {
        return this;
    }

    @Override
    public Page getPage() {
        return getPm().getPage();
    }

    @Override
    public void setPage(Page<?> page) {
        getPm().setPage(page);
    }

    @Override
    public PojoQuery<T> page(Page page) {
        getPm().setPage(page);
        return this;
    }
    public T queryBean() {
        return DBHolder.doDb(s -> s.getBean(this, true));
    }

    public List<T> queryBeanList() {
        return DBHolder.doDb(s -> s.getBeanList(this));
    }

    public Page<T> queryBeanPage() {
        return DBHolder.doDb(s -> s.getPage(this, this.getBeanClass()));
    }

    @SuppressWarnings("unchecked")
    public PojoQuery<T> orderByAsc(DlzFn<T, ?>... column) {
        return sort(Order.ascs(column));
    }

    @SuppressWarnings("unchecked")
    public PojoQuery<T> orderByDesc(DlzFn<T, ?>... column) {
        return sort(Order.descs(column));
    }
}
