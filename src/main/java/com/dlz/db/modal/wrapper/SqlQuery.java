package com.dlz.db.modal.wrapper;

import com.dlz.db.convertor.columnname.IConvertorToFieldName;
import com.dlz.db.inf.IExecutorQuery;
import com.dlz.db.inf.ISqlPage;
import com.dlz.db.modal.dto.Page;
import com.dlz.db.modal.para.ParaMap;
import com.dlz.db.support.SqlRunThreadHolder;
import lombok.extern.slf4j.Slf4j;

/**
 * 构造单表的查询操作sql
 *
 * @author dingkui
 */
@Slf4j
@SuppressWarnings("rawtypes")
public class SqlQuery extends ParaMap<SqlQuery> implements ISqlPage<SqlQuery>, IExecutorQuery<SqlQuery> {
    private static final long serialVersionUID = 8374167270612933157L;
    public SqlQuery(String sql) {
        super(sql);
    }
    public SqlQuery(String sql, Page page) {
        super(sql);
        this.setPage(page);
    }
    @Override
    public SqlQuery me() {
        return this;
    }
    @Override
    public SqlQuery page(Page page) {
        if (page != null) {
            this.setPage(page);
        }
        return this;
    }
    public SqlQuery convert(IConvertorToFieldName convertor) {
        SqlRunThreadHolder.setConvertorToFieldName(convertor);
        return this;
    }
}
