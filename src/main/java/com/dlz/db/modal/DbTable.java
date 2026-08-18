package com.dlz.db.modal;

import com.dlz.db.modal.dto.ResultMap;
import com.dlz.db.modal.wrapper.TableDelete;
import com.dlz.db.modal.wrapper.TableInsert;
import com.dlz.db.modal.wrapper.TableQuery;
import com.dlz.db.modal.wrapper.TableUpdate;
import com.dlz.db.support.PojoCache;
import com.dlz.kit.exception.SystemException;
import com.dlz.kit.json.JSONMap;
import com.dlz.kit.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class DbTable {
    public TableInsert insertW(String tableName) {
        return new TableInsert(tableName);
    }
    public TableDelete deleteW(String tableName) {
        return new TableDelete(tableName);
    }

    public TableUpdate updateW(String tableName) {
        return new TableUpdate(tableName);
    }
    public TableQuery selectW(String tableName) {
        return new TableQuery(tableName);
    }

    //以下都是直接操作执行
    public int insert(String tableName, JSONMap value) {
        return new TableInsert(tableName).value(value).execute();
    }
    public Long insertWithAutoKey(String tableName, JSONMap value) {
        return new TableInsert(tableName).value(value).insertWithAutoKey();
    }


    public int insertOrUpdate(String tableName, JSONMap value) {
        final String idName = PojoCache.getIdName(tableName);
        final Object id = value.get(idName);
        if (StringUtils.isEmpty(id)) {
            return insert(tableName,value);
        }
        value.remove(idName);
        final int updateResult = updateW(tableName).set(value).eq(idName, id).execute();
        value.put(idName, id);
        return updateResult;
    }

    private int updateById(String tableName, JSONMap value) {
        final String idName = PojoCache.getIdName(tableName);
        final Object id = value.getLong(idName);
        if (StringUtils.isEmpty(id)) {
            throw new SystemException(idName + "不能为空");
        }
        value.remove(idName);
        final int updateResult = updateW(tableName).set(value).eq(idName, id).execute();
        value.put(idName, id);
        if (updateResult == 1) {
            return updateResult;
        }
        log.warn("更新数据条数应为1,实际更新数据为:{}", updateResult);
        return updateResult;
    }
    public ResultMap selectById(String tableName, Object id) {
        final String idName = PojoCache.getIdName(tableName);
        return selectW(tableName).eq(idName, id).queryOne();
    }
    public List<ResultMap> selectByIds(String tableName, Object ids) {
        final String idName = PojoCache.getIdName(tableName);
        if (StringUtils.isEmpty(ids)) {
            throw new SystemException(idName + "不能为空");
        }
        return selectW(tableName).in(idName, ids).queryList();
    }

    public int deleteByIds(String tableName, Object ids) {
        final String idName = PojoCache.getIdName(tableName);
        if (StringUtils.isEmpty(ids)) {
            throw new SystemException(idName + "不能为空");
        }
        return deleteW(tableName).in(idName, ids).execute();
    }

    public int deleteByIds(String tableName, List<?> ids) {
        final String idName = PojoCache.getIdName(tableName);
        if (StringUtils.isEmpty(ids)) {
            throw new SystemException(idName + "不能为空");
        }
        return deleteW(tableName).in(idName, ids).execute();
    }

    public int deleteById(String tableName, Object id) {
        final String idName = PojoCache.getIdName(tableName);
        if (StringUtils.isEmpty(id)) {
            throw new SystemException(idName + "不能为空");
        }
        return deleteW(tableName).eq(idName, id).execute();
    }
}