package com.dlz.db.service;

import com.dlz.db.inf.IExecutorQuery;
import com.dlz.db.util.DbConvertUtil;

import java.math.BigDecimal;
import java.util.List;

/**
 * 从数据库中取得单条map类型数据：{adEnddate=2015-04-08 13:47:12.0}
 * sql语句，可以带参数如：select AD_ENDDATE from JOB_AD t where ad_id=#{ad_id}
 * paraMap ：Map<String,Object> m=new HashMap<String,Object>();m.put("ad_id", "47");
 *
  * @throws Exception
 */
public interface IDbColumnService extends IDbBaseService {

    default String getStr(IExecutorQuery paraMap) {
        return getFirstColumn(paraMap, String.class);
    }

    default <T> List<T> getColumnList(IExecutorQuery paraMap, Class<T> tClass) {
        return doDb(paraMap, jdbcSql -> DbConvertUtil.getColumnList(getSqlExecutor().getList(jdbcSql.sql, jdbcSql.paras), tClass));
    }

    default <T> T getFirstColumn(IExecutorQuery paraMap, Class<T> tClass) {
        return doDb(paraMap, jdbcSql -> getSqlExecutor().getFirstColumn(jdbcSql.sql, tClass, jdbcSql.paras));
    }

    default BigDecimal getBigDecimal(IExecutorQuery paraMap) {
        return getFirstColumn(paraMap, BigDecimal.class);
    }

    default Float getFloat(IExecutorQuery paraMap) {
        return getFirstColumn(paraMap, Float.class);
    }

    default Integer getInt(IExecutorQuery paraMap) {
        return getFirstColumn(paraMap, Integer.class);
    }

    default Long getLong(IExecutorQuery paraMap) {
        return getFirstColumn(paraMap, Long.class);
    }

    default Double getDouble(IExecutorQuery paraMap) {
        return getFirstColumn(paraMap, Double.class);
    }

    default List<String> getStrList(IExecutorQuery paraMap) {
        return getColumnList(paraMap, String.class);
    }

    default List<BigDecimal> getBigDecimalList(IExecutorQuery paraMap) {
        return getColumnList(paraMap, BigDecimal.class);
    }

    default List<Float> getFloatList(IExecutorQuery paraMap) {
        return getColumnList(paraMap, Float.class);
    }

    default List<Integer> getIntList(IExecutorQuery paraMap) {
        return getColumnList(paraMap, Integer.class);
    }

    default List<Long> getLongList(IExecutorQuery paraMap) {
        return getColumnList(paraMap, Long.class);
    }

    default List<Double> getDoubleList(IExecutorQuery paraMap) {
        return getColumnList(paraMap, Double.class);
    }
}
