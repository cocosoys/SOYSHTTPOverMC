package com.dlz.db.enums;

import com.dlz.db.modal.condition.Condition;
import com.dlz.db.support.PojoCache;
import com.dlz.db.util.DbConvertUtil;
import com.dlz.db.util.SqlUtil;
import com.dlz.kit.exception.SystemException;
import com.dlz.kit.exception.ValidateException;
import com.dlz.kit.fn.DlzFn;
import com.dlz.kit.util.ValUtil;
import lombok.AllArgsConstructor;

import java.util.regex.Pattern;

@AllArgsConstructor
public enum DbOperateEnum {
    isNull("#n is null"),//为空
    isNotNull("#n is not null"),//不为空
    eq("#n = #{#k}"),
    lt("#n < #{#k}"),//小于
    le("#n <= #{#k}"),//小于等于
    gt("#n > #{#k}"),//大于
    ge("#n >= #{#k}"),//大于等于
    ne("#n <> #{#k}"),//不等于
    in("#n in (${#k})"),
    notIn("#n not in (${#k})"),
    like("#n like #{#k}"),//like:%xxx%
    likeLeft("#n like #{#k}"),//左like:xxx%
    likeRight("#n like #{#k}"),//右like：%xxx
    notLike("#n not like #{#k}"),//不like
    between("#n between #{#k1} and #{#k2}"),//BETWEEN 值1 AND 值2
    notBetween("#n not between #{#k1} and #{#k2}");//BETWEEN 值1 AND 值2
    public final String _sql;
    private final static Pattern patternKey = Pattern.compile("#k");
    private final static Pattern patternColumnName = Pattern.compile("#n");

    private final static Pattern COLUMN_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_.]+$");
    private String mkSql(String dbn, String key) {
        if (!COLUMN_NAME_PATTERN.matcher(dbn).matches()) {
            throw new ValidateException("非法列名: " + dbn);
        }
        final String dbnSql = patternColumnName.matcher(this._sql).replaceAll(DbConvertUtil.toDbColumnName(dbn));
        return key == null ? dbnSql : patternKey.matcher(dbnSql).replaceAll(key);
    }

    private Condition paraZero(String dbn,String tableName) {
        Condition condition = new Condition(tableName);
        condition.setRunSql(mkSql(dbn, null));
        return condition;
    }

    private Condition paraOne(String dbn, Object value, String tableName) {
        String key = KeyUtil.getKeyName(this + "_");
        Condition condition = new Condition(tableName);
        condition.addPara(key, tableName==null?value:DbConvertUtil.getVal4Db(tableName, dbn, value));
        condition.setRunSql(mkSql(dbn, key));
        return condition;
    }

    private Condition paraTwo(String dbn, Object value, String tableName) {
        String key = KeyUtil.getKeyName(this + "_");
        Object[] array = ValUtil.toArray(value);
        if (array.length < 2) {
            throw new SystemException("参数有误，需要有2个值：" + this);
        }
        Condition condition = new Condition(tableName);
        String key1 = key + "1";
        String key2 = key + "2";
        condition.addPara(key1, tableName==null?array[0]:DbConvertUtil.getVal4Db(tableName, dbn, array[0]));
        condition.addPara(key2, tableName==null?array[1]:DbConvertUtil.getVal4Db(tableName, dbn, array[1]));
        condition.setRunSql(mkSql(dbn, key));
        return condition;
    }

    private Condition paraIn(String dbn, Object value,String tableName) {
        String key = KeyUtil.getKeyName(this + "_");
        Condition condition = new Condition(tableName);
        condition.setRunSql(mkSql(dbn, key));
        if (value instanceof String) {
            String v = (String) value;
            if (v.startsWith("sql:")) {
                condition.addPara(key, v.substring(4));
                return condition;
            }
        }
        condition.addPara(key, SqlUtil.getSqlInStr(value));
        return condition;
    }


    public <T> Condition mk(DlzFn<T, ?> dbn, Object value, String tableName) {
        return mk(PojoCache.fnName(dbn), value, tableName);
    }

    public Condition mk(String dbn, Object value, String tableName) {
        switch (this) {
            case like:
            case notLike:
                return paraOne(dbn, "%" + value + "%", null);
            case likeLeft:
                return paraOne(dbn, "%" + value, null);
            case likeRight:
                return paraOne(dbn, value + "%", null);

            case between:
            case notBetween:
                return paraTwo(dbn, value, tableName);
            case isNull:
            case isNotNull:
                return paraZero(dbn,tableName);
            case in:
            case notIn:
                return paraIn(dbn, value,tableName);
            default:
//            case eq:
//            case ne:
//            case gt:
//            case ge:
//            case lt:
//            case le:
//                return paraOne(dbn, value, tableName);
                return paraOne(dbn, value, tableName);
        }
    }

    public static DbOperateEnum getDbOperateEnum(String operate) {
        for (DbOperateEnum dbOperateEnum : DbOperateEnum.values()) {
            if (dbOperateEnum.toString().equals(operate)) {
                return dbOperateEnum;
            }
        }
        return null;
    }
}
