package soys.soyshttpovermc.spring.impl;

import soys.soyshttpovermc.spring.entity.BaseEntity;
import soys.soyshttpovermc.spring.service.IBaseService;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 通用 Service 实现基类（仿 MyBatis-Plus 的 {@code ServiceImpl<M, T>}）：
 * 为 {@link IBaseService} 提供通用默认实现。本插件无持久化层，
 * 默认实现以"无操作 / 空集合"返回，业务 Service 仅覆写真正用到的抽象方法。
 */
public abstract class BaseServiceImpl<T extends BaseEntity> implements IBaseService<T> {

    @Override
    public T getById(Serializable id) {
        return null;
    }

    @Override
    public List<T> list() {
        return Collections.emptyList();
    }

    @Override
    public long count() {
        return 0L;
    }

    @Override
    public boolean save(T entity) {
        return false;
    }

    @Override
    public boolean removeById(Serializable id) {
        return false;
    }

    @Override
    public boolean updateById(T entity) {
        return false;
    }
}
