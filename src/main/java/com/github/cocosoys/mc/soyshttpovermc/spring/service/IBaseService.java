package com.github.cocosoys.mc.soyshttpovermc.spring.service;

import com.github.cocosoys.mc.soyshttpovermc.spring.entity.BaseEntity;
import com.github.cocosoys.mc.soyshttpovermc.spring.impl.BaseServiceImpl;

import java.io.Serializable;
import java.util.List;

/**
 * 通用 Service 接口（仿 MyBatis-Plus 的 {@code IService<T>}）：
 * 声明一组与实体无关的通用抽象方法，供所有业务 Service 继承复用。
 *
 * <p>本插件无持久化层（数据来自隧道统计/内存），故方法语义以"抽象声明"为主，
 * 通用默认实现由 {@link BaseServiceImpl} 提供，
 * 业务 Service 仅覆写真正用到的抽象方法（如 {@code IStatusService.getStatus()}）。
 */
public interface IBaseService<T extends BaseEntity> {

    /** 按主键获取实体 */
    T getById(Serializable id);

    /** 查询全部 */
    List<T> list();

    /** 计数 */
    long count();

    /** 保存（新增或更新） */
    boolean save(T entity);

    /** 按主键删除 */
    boolean removeById(Serializable id);

    /** 按主键更新 */
    boolean updateById(T entity);
}
