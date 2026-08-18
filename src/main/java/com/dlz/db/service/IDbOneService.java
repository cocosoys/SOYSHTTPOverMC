package com.dlz.db.service;

import com.dlz.db.inf.IExecutorQuery;
import com.dlz.db.modal.dto.ResultMap;
import com.dlz.db.modal.wrapper.PojoQuery;
import com.dlz.kit.exception.ValidateException;
import com.dlz.kit.util.StringUtils;
import com.dlz.kit.util.system.ConvertUtil;

/**
 * 从数据库中取得单条map类型数据：{adEnddate=2015-04-08 13:47:12.0}
 * sql语句，可以带参数如：select AD_ENDDATE from JOB_AD t where ad_id=#{ad_id}
 * paraMap ：Map<String,Object> m=new HashMap<String,Object>();m.put("ad_id", "47");
 *
  * @throws Exception
 */
public interface IDbOneService extends IDbBaseService {

    /**
     * 从数据库中取得集合
     */
    default ResultMap getMap(IExecutorQuery paraMap) {
        return getMap(paraMap, false);
    }

    default ResultMap getMap(IExecutorQuery paraMap, boolean throwEx) {
        return doDb(paraMap, jdbcSql -> getSqlExecutor().getOne(jdbcSql.sql, throwEx, jdbcSql.paras));
    }

    default <T> T getBean(PojoQuery<T> wrapper, boolean throwEx) {
        return doDb(wrapper, jdbcSql -> ConvertUtil.convert(getSqlExecutor().getOne(jdbcSql.sql, throwEx, jdbcSql.paras),wrapper.getBeanClass()));
    }
    default <T> T getBean(IExecutorQuery paraMap, Class<T> t, boolean throwEx) {
        return doDb(paraMap, jdbcSql -> ConvertUtil.convert(getSqlExecutor().getOne(jdbcSql.sql, throwEx, jdbcSql.paras), t));
    }

    default <T> T getBean(IExecutorQuery paraMap, Class<T> t) {
        return getBean(paraMap, t, false);
    }

    default <T> T getBean(T bean) {
        final PojoQuery<T> wrapper = PojoQuery.wrapper(bean);
        return getBean(wrapper, wrapper.getBeanClass(), true);
    }
    default <T> T selectById(String id,Class<T> clazz){
        if(StringUtils.isEmpty(id)){
            throw new ValidateException("id不能为空");
        }
        return getBean(PojoQuery.wrapper(clazz).eq("id",id),clazz,true);
    }

}
