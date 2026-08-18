package com.dlz.db.modal.wrapper;

import com.dlz.db.inf.IExecutorQuery;
import com.dlz.db.inf.ISqlPage;
import com.dlz.db.inf.ISqlPara;
import com.dlz.db.modal.dto.Page;
import com.dlz.db.modal.para.ParaJdbc;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;

/**
 * 构造单表的查询操作sql
 *
 * @author dingkui
 */
@Slf4j
public class JdbcQuery extends ParaJdbc implements Serializable, ISqlPara, ISqlPage<JdbcQuery>, IExecutorQuery<JdbcQuery> {
    private static final long serialVersionUID = 8374167270612933157L;
    public JdbcQuery(String sql, Object... paras) {
        super(sql, paras);
    }
    @Override
    public JdbcQuery page(Page page) {
        if (page != null) {
            this.setPage(page);
        }
        return this;
    }

    @Override
    public JdbcQuery me() {
        return this;
    }
}
