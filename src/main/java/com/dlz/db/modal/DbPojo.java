package com.dlz.db.modal;

import com.dlz.db.modal.wrapper.PojoDelete;
import com.dlz.db.modal.wrapper.PojoInsert;
import com.dlz.db.modal.wrapper.PojoQuery;
import com.dlz.db.modal.wrapper.PojoUpdate;
import com.dlz.db.support.PojoCache;
import com.dlz.db.support.bean.IdInfo;
import com.dlz.kit.exception.SystemException;
import com.dlz.kit.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.function.Function;

@Slf4j
public class DbPojo {
    public <T> PojoQuery<T> selectW(Class<T> re) {
        return PojoQuery.wrapper(re);
    }
    public <T> PojoDelete<T> deleteW(Class<T> beanClass) {
        return PojoDelete.wrapper(beanClass);
    }

    public <T> PojoUpdate<T> updateW(Class<T> beanClass) {
        return new PojoUpdate(beanClass);
    }
    /**
     *
     * @param value
     * @param ignore 字段忽略规则
     * @param <T>
     * @return
     */
    public <T> PojoUpdate<T> updateW(T value, Function<String, Boolean> ignore) {
        return new PojoUpdate((Class<T>) value.getClass()).set(value, ignore);
    }
    public <T> PojoUpdate<T> updateW(T value) {
        return new PojoUpdate((Class<T>) value.getClass()).set(value);
    }

    //以下都是直接操作执行
    public <T> T insert(T bean) {
        new PojoInsert(bean).execute();
        return bean;
    }

    public <T> T insertOrUpdate(T obj) {
        final Class<T> aClass = (Class<T>) obj.getClass();
        final IdInfo idInfo = PojoCache.getIdInfo(aClass);
        if (idInfo == null) {
            throw new SystemException("无主键信息,不支持:insertOrUpdate " + aClass.getSimpleName());
        }
        final Object id = idInfo.getValue(obj);
        if (StringUtils.isEmpty(id)) {
            return insert(obj);
        }
        return updateById(obj, id, aClass, idInfo);
    }

    private <T> T updateById(T obj, Object idValue, Class<T> aClass, IdInfo idInfo) {
        final String idName = idInfo.getName();
        if (StringUtils.isEmpty(idValue)) {
            throw new SystemException(idName + "不能为空");
        }
        final int execute = updateW(aClass).set(obj, name -> name.equalsIgnoreCase(idName)).eq(idName, idValue).execute();
        if (execute == 1) {
            return obj;
        }
        log.warn("更新数据条数应为1,实际更新数据为:{}", execute);
        return null;
    }

    public <T> T updateById(T obj) {
        final Class<T> aClass = (Class<T>) obj.getClass();
        final IdInfo idInfo = PojoCache.getIdInfo(aClass);
        final Object id = idInfo.getValue(obj);
        return updateById(obj, id, aClass, idInfo);
    }

    public <T> T selectById(Class<T> c, Object id) {
        final String idName = PojoCache.getIdName(c);
        if (StringUtils.isEmpty(id)) {
            throw new SystemException(idName + "不能为空");
        }
        return selectW(c).eq(idName, id).queryBean();
    }

    public <T> List<T> selectByIds(Class<T> c, Object id) {
        final String idName = PojoCache.getIdName(c);
        if (StringUtils.isEmpty(id)) {
            throw new SystemException(idName + "不能为空");
        }
        return selectW(c).in(idName, id).queryBeanList();
    }

    public <T> int deleteByIds(Class<T> c, String ids) {
        final String idName = PojoCache.getIdName(c);
        if (StringUtils.isEmpty(ids)) {
            throw new SystemException(idName + "不能为空");
        }
        return deleteW(c).in(idName, ids).execute();
    }

    public <T> int deleteByIds(Class<T> c, List<?> ids) {
        final String idName = PojoCache.getIdName(c);
        if (StringUtils.isEmpty(ids)) {
            throw new SystemException(idName + "不能为空");
        }
        return deleteW(c).in(idName, ids).execute();
    }

    public <T> int deleteById(Class<T> c, Object id) {
        final String idName = PojoCache.getIdName(c);
        if (StringUtils.isEmpty(id)) {
            throw new SystemException(idName + "不能为空");
        }
        return deleteW(c).eq(idName, id).execute();
    }
}