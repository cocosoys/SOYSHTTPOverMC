package com.dlz.db.modal.para;

import com.dlz.db.inf.ISqlPara;
import com.dlz.kit.exception.ValidateException;

import java.util.regex.Pattern;

/**
 * 构造单表的增删改查操作sql
 *
 * @author dingkui
 */
public abstract class AParaTable<T extends AParaTable> extends ParaMap<T> implements ISqlPara {
    private static final long serialVersionUID = 8374167270612933157L;
    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_.]+$");
    private final String tableName;
    protected AParaTable(String tableName) {
        if (tableName == null || !TABLE_NAME_PATTERN.matcher(tableName).matches()) {
            throw new ValidateException("非法表名: " + tableName);
        }
        this.tableName = tableName;
    }
    public String getTableName() {
        return tableName;
    }
    public abstract String getSql();
}
