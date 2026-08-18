package com.dlz.db.service;

import com.dlz.db.inf.IExecutorQuery;
import com.dlz.db.modal.dto.Page;
import com.dlz.db.modal.dto.ResultMap;
import com.dlz.db.modal.wrapper.PojoQuery;
import com.dlz.kit.util.system.ConvertUtil;

import java.util.List;

/**
 * 从数据库中取得单条map类型数据：{adEnddate=2015-04-08 13:47:12.0}
 * sql语句，可以带参数如：select AD_ENDDATE from JOB_AD t where ad_id=#{ad_id}
 * paraMap ：Map<String,Object> m=new HashMap<String,Object>();m.put("ad_id", "47");
 *
  * @throws Exception
 */
public interface IDbListService extends IDbBaseService{
    /**
     * 从数据库中取得map类型列表如：[{AD_ENDDATE=2015-04-08 13:47:12.0}]
     * sql语句，可以带参数如：select AD_ENDDATE from JOB_AD t where ad_id=#{ad_id}
     *
     * @param paraMap ：Map<String,Object> m=new HashMap<String,Object>();m.put("ad_id", "47");
     * @return List<ResultMap>
     */
    default List<ResultMap> getMapList(IExecutorQuery paraMap) {
        return doDb(paraMap, jdbcSql -> getSqlExecutor().getList(jdbcSql.sql, jdbcSql.paras));
    }
    default <T> List<T> getBeanList(PojoQuery<T> wrapper) {
        return doDb(wrapper, jdbcSql -> ConvertUtil.convertList(getSqlExecutor().getList(jdbcSql.sql, jdbcSql.paras),wrapper.getBeanClass()));
    }


    default <T> List<T> getBeanList(IExecutorQuery paraMap, Class<T> t) {
        return ConvertUtil.convertList(getMapList(paraMap), t);
    }
    default <T> List<T> getBeanList(T bean){
        final PojoQuery<T> wrapper = PojoQuery.wrapper(bean);
        return getBeanList(wrapper,wrapper.getBeanClass());
    }
    default long getCnt(IExecutorQuery paraMap) {
        return doCnt(paraMap, jdbcSql -> getSqlExecutor().getFirstColumn(jdbcSql.sql, Long.class, jdbcSql.paras));
    }
    /**
     * 取得分页数据
     *
     * @return Page<ResultMap>
     */
    default Page<ResultMap> getPage(IExecutorQuery paraMap) {
        Page<ResultMap> page = (Page<ResultMap>)paraMap.getPage();
        if (page == null) {
            page = Page.build();
            paraMap.setPage(page);
        }
        return page.doPage(() -> getCnt(paraMap), () -> getMapList(paraMap));
    }

    default <T> Page<T> getPage(IExecutorQuery paraMap, Class<T> t) {
        Page<T> page = (Page<T>)paraMap.getPage();
        if (page == null) {
            page = Page.build();
            paraMap.setPage(page);
        }
        return page.doPage(() -> getCnt(paraMap), () -> getBeanList(paraMap, t));
    }

}
