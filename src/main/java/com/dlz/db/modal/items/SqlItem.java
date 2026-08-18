package com.dlz.db.modal.items;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
public class SqlItem implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 输入的key
     */
    private String sqlKey;
    /**
     * 解析过后的sql(只存在填充符)
     */
    private String sqlDeal;
    /**
     * 解析过后的sql(只存在填充符)
     */
    private String sqlRun;

//
//    private String sqlJdbc;
//    private Object[] sqlJdbcPara;


}