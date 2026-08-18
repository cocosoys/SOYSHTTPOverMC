package com.dlz.db.service;

import com.dlz.db.convertor.rowMapper.IRowMapper;
import com.dlz.db.core.ISqlExecutor;
import com.dlz.db.exception.DbException;
import com.dlz.db.modal.dto.ResultMap;
import com.dlz.db.support.DBHolder;
import com.dlz.db.util.DbConvertUtil;
import com.dlz.kit.util.system.ConvertUtil;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;


/**
 * 从数据库中取得单条map类型数据：{adEnddate=2015-04-08 13:47:12.0}
 * sql语句，可以带参数如：select AD_ENDDATE from JOB_AD t where ad_id=#{ad_id}
 * paraMap ：Map<String,Object> m=new HashMap<String,Object>();m.put("ad_id", "47");

  * @throws Exception
 */
public interface IDbJdbcService {
    ISqlExecutor getSqlExecutor();

    default List<ResultMap> getMapList(String sql, Object... para) {
        return doJdbc(() -> getSqlExecutor().getList(sql, para));
    }
    default <T> List<T> getList(String sql, IRowMapper<T> rowMapper, Object... para) {
        return doJdbc(() -> {
            List<T> list = DBHolder.getSqlExecutor().getList(sql, rowMapper::mapRow, para);
            return list;
        });
    }

    default <T> T doJdbc(Callable<T> executor) {
        try {
            return executor.call();
        } catch (Exception e) {
            if (e instanceof DbException) {
                throw (DbException)e;
            }
            throw new DbException(e.getMessage(), 1005, e);
        }
    }
    default <T> List<T> getColumnList(String sql, Class<T> tClass, Object... para) {
        return doJdbc(() -> DbConvertUtil.getColumnList(getSqlExecutor().getList(sql, para), tClass));
    }

    default <T> T getFirstColumn(String sql, Class<T> tClass, Object... para) {
        return doJdbc(() -> getSqlExecutor().getFirstColumn(sql,tClass, para));
    }

    /**
     * 新的一套操作api,用于比较简单的sql,直接用问号传参
     *
     * @param sql  sql语句，可以用问号传参数如：update JOB_AD set AD_text=? where ad_id = ?
     * @param para ：参数数组
     */
    default int executeSql(String sql, Object... para) {
        return doJdbc(() -> getSqlExecutor().update(sql, para));
    }


    default ResultMap getMap(String sql, Boolean throwEx, Object... para) {
        return doJdbc(() -> getSqlExecutor().getOne(sql,throwEx, para));
    }

    default String getStr(String sql, Object... para) {
        return this.getFirstColumn(sql, String.class, para);
    }

    default BigDecimal getBigDecimal(String sql, Object... para) {
        return this.getFirstColumn(sql, BigDecimal.class, para);
    }

    default Float getFloat(String sql, Object... para) {
        return this.getFirstColumn(sql, Float.class, para);
    }

    default Integer getInt(String sql, Object... para) {
        return this.getFirstColumn(sql, Integer.class, para);
    }

    default Long getLong(String sql, Object... para) {
        return this.getFirstColumn(sql, Long.class, para);
    }

    default Double getDouble(String sql, Object... para) {
        return this.getFirstColumn(sql, Double.class, para);
    }

    default List<String> getStrList(String sql, Object... para) {
        return this.getColumnList(sql, String.class, para);
    }

    default List<BigDecimal> getBigDecimalList(String sql, Object... para) {
        return this.getColumnList(sql, BigDecimal.class, para);
    }

    default List<Float> getFloatList(String sql, Object... para) {
        return this.getColumnList(sql, Float.class, para);
    }

    default List<Integer> getIntList(String sql, Object... para) {
        return this.getColumnList(sql, Integer.class, para);
    }

    default List<Long> getLongList(String sql, Object... para) {
        return this.getColumnList(sql, Long.class, para);
    }

    default List<Double> getDoubleList(String sql, Object... para) {
        return this.getColumnList(sql, Double.class, para);
    }

    default ResultMap getMap(String sql, Object... para) {
        return getMap(sql, true, para);
    }

    default <T> T getBean(String sql, Class<T> t, Object... para) {
        return doJdbc(() -> ConvertUtil.convert(getSqlExecutor().getOne(sql, true,para),t));
    }

    default <T> List<T> getBeanList(String sql, Class<T> t, Object... para) {
        return doJdbc(() -> ConvertUtil.convertList(getSqlExecutor().getList(sql, para),t));
    }
}
