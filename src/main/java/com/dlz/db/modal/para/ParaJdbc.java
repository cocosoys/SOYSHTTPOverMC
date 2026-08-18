package com.dlz.db.modal.para;

import com.dlz.db.inf.ISqlPara;
import com.dlz.db.modal.dto.Page;
import com.dlz.db.modal.items.JdbcItem;
import com.dlz.db.modal.items.SqlItem;
import com.dlz.db.util.SqlUtil;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

public class ParaJdbc implements Serializable , ISqlPara{
    private static final long serialVersionUID = 8374167270612933157L;
    @Getter
    private final SqlItem sqlItem = new SqlItem();
    @Getter
    @Setter
    private Page<?> page;
    @Getter
    private final Object[] paras;
    public ParaJdbc(String sql,Object[] paras) {
        sqlItem.setSqlDeal(sql);
        this.paras = paras;
    }

    public JdbcItem jdbcSql() {
        if (this.getPage() == null) {
            return SqlUtil.dealJdbc(this,1);
        }
        return SqlUtil.dealJdbc(this,3);
    }
    public JdbcItem jdbcCnt() {
        return SqlUtil.dealJdbc(this,2);
    }
}
