package com.dlz.db.modal.para;

import com.dlz.db.enums.ParaTypeEnum;
import com.dlz.db.inf.IChained;
import com.dlz.db.inf.ISqlPara;
import com.dlz.db.modal.dto.Page;
import com.dlz.db.modal.items.JdbcItem;
import com.dlz.db.modal.items.SqlItem;
import com.dlz.db.support.PojoCache;
import com.dlz.db.util.SqlUtil;
import com.dlz.kit.fn.DlzFn;
import com.dlz.kit.json.JSONMap;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Map;

public class ParaMap<ME extends ParaMap> implements Serializable , ISqlPara, IChained<ME> {
    private static final long serialVersionUID = 8374167270612933157L;
    @Getter
    private final SqlItem sqlItem = new SqlItem();
    @Getter
    @Setter
    private Page page;

    @Getter
    private final JSONMap para = new JSONMap();

    public ParaMap() {
    }
    public ParaMap(String sqlKey) {
        sqlItem.setSqlKey(sqlKey);
    }

    public ME addParas(Map<String, Object> map) {
        para.putAll(map);
        return me();
    }

    /**
     * 添加参数
     *
     * @param key
     * @param value
          */
    public ME addPara(String key, Object value) {
		para.put(key, value == null ? "" : value);
        return (ME)me();
    }
    public <T> ME addPara(DlzFn<T, ?> column, Object value){
        return addPara(PojoCache.fnName(column),value);
    }

    public JdbcItem jdbcSql() {
        if (this.getPage() == null) {
            SqlUtil.dealParm(this,1);
        }else{
            SqlUtil.dealParm(this,3);
        }
        return SqlUtil.dealParmToJdbc(this);
    }
    public JdbcItem jdbcCnt() {
        SqlUtil.dealParm(this,2);
        return SqlUtil.dealParmToJdbc(this);
    }

    @Override
    public ME me() {
        return (ME)this;
    }
}
