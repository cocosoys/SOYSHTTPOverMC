package com.github.cocosoys.mc.soyshttpovermc.spring.service;

import com.github.cocosoys.mc.soyshttpovermc.orm.query.Query;

import java.util.List;
import java.util.function.Consumer;

/**
 * 本地权限表「双后端 IO」存储抽象（SQL / YAML 双兼容）。
 *
 * <p>统一 ORM 门面 {@link com.github.cocosoys.mc.soyshttpovermc.orm.DATA} 的 4 个通用
 * CRUD 原语抽象：SQL 后端可用走 SQL，否则回退 YAML（见 {@code storage.backends.*}），
 * 对业务层透明。由 {@link com.github.cocosoys.mc.soyshttpovermc.spring.impl.LocalPermStorageImpl}
 * 实现，{@link com.github.cocosoys.mc.soyshttpovermc.permission.local.LocalPermissionStore}
 * 持有本接口完成全部本地权限表读写。</p>
 *
 * <p>实体：{@link com.github.cocosoys.mc.soyshttpovermc.spring.entity.SoysPermGroup} /
 * {@link com.github.cocosoys.mc.soyshttpovermc.spring.entity.SoysPermUser} /
 * {@link com.github.cocosoys.mc.soyshttpovermc.spring.entity.SoysPermUserGroup} /
 * {@link com.github.cocosoys.mc.soyshttpovermc.spring.entity.SoysPermPermission}。</p>
 */
public interface ILocalPermStorage {

    /**
     * 按主键取单条记录；不存在返回 null。
     *
     * @param c  实体类
     * @param id 主键（String 或包装类型）
     */
    <T> T get(Class<T> c, Object id);

    /**
     * 插入（或按主键覆盖）一条记录。
     *
     * @return 是否成功；失败（双后端均不可写等）返回 false
     */
    boolean save(Object bean);

    /**
     * 按主键删除一条记录。
     *
     * @return 是否成功
     */
    boolean delete(Class<?> c, Object id);

    /**
     * 条件查询列表；未命中返回空列表（不会返回 null）。
     *
     * @param c    实体类
     * @param cond 查询条件构造器（可为空 lambda，表示全量）
     */
    <T> List<T> list(Class<T> c, Consumer<Query<T>> cond);
}
