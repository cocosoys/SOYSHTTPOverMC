package com.dlz.db.support.bean;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ColumnInfo {
    private String columnName;
    private String columnType;
    private String columnComment;
    private Class<?> javaType;
    /** 列长度（VARCHAR(255) 的 255） */
    private int columnSize;
    /** 小数位（DECIMAL(10,2) 的 2） */
    private int decimalDigits;
    /** 是否可空 */
    private boolean nullable = true;
    /** 默认值 */
    private String defaultValue;
    /** 是否自增 */
    private boolean autoIncrement;
    /** 是否主键（由 TableInfo.primaryKeys 推导） */
    private boolean primaryKey;
}
