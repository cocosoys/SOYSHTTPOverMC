package com.dlz.db.service;

import com.dlz.db.annotation.IdType;
import com.dlz.db.inf.IExecutorInsert;
import com.dlz.db.inf.IExecutorUDI;
import com.dlz.db.interceptor.DbPlugin;
import com.dlz.db.modal.wrapper.PojoInsert;
import com.dlz.db.modal.wrapper.WrapperBuildUtil;
import com.dlz.db.support.PojoCache;
import com.dlz.db.support.SqlRunThreadHolder;
import com.dlz.db.support.bean.IdInfo;
import com.dlz.kit.util.system.FieldReflections;

import java.lang.reflect.Field;

/**
 * 从数据库中取得单条map类型数据：{adEnddate=2015-04-08 13:47:12.0}
 * sql语句，可以带参数如：select AD_ENDDATE from JOB_AD t where ad_id=#{ad_id}
 * paraMap ：Map<String,Object> m=new HashMap<String,Object>();m.put("ad_id", "47");
 *
  * @throws Exception
 */
public interface IDbExecuteService extends IDbBaseService{

    /**
     * 插入数据库
     * sql语句，可以带参数如：update JOB_AD set AD_text=#{adText} where ad_id in (${ad_id})
     *
     * @param paraMap ：Map<String,Object> m=new HashMap<String,Object>();m.put("ad_id", "47");
          * @throws Exception
     */
    default Long insertWithAutoKey(IExecutorInsert paraMap) {
        return doDb(paraMap, jdbcSql -> getSqlExecutor().updateForId(jdbcSql.sql, jdbcSql.paras));
    }

    /**
     * 更新或插入数据库
     * sql语句，可以带参数如：update JOB_AD set AD_text=#{adText} where ad_id in (${ad_id})
     *
     * @param paraMap ：Map<String,Object> m=new HashMap<String,Object>();m.put("ad_id", "47");
     * @return 影响行数
     */
    default int execute(IExecutorUDI paraMap) {
        if (paraMap instanceof PojoInsert) {
            PojoInsert p = (PojoInsert) paraMap;
            Object bean = p.getBean();
            final Class<?> beanClass = bean.getClass();
            final IdInfo idField = PojoCache.getIdInfo(beanClass);
            final String tableName = PojoCache.getTableName(beanClass);
            if(idField != null){
                WrapperBuildUtil.fillAutoId(tableName, idField, bean);
                if(idField.getType() == IdType.AUTO){
                    final Long newid = doDb(paraMap, jdbcSql -> getSqlExecutor().updateForId(jdbcSql.sql, jdbcSql.paras));
                    if(newid != null){
                        idField.setId(bean, newid);
                    }
                    return 1;
                }
            }
        }
        return doDb(paraMap, jdbcSql -> getSqlExecutor().update(jdbcSql.sql, jdbcSql.paras));
    }
}
