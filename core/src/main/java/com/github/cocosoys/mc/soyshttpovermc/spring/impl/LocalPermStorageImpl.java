package com.github.cocosoys.mc.soyshttpovermc.spring.impl;

import com.github.cocosoys.mc.soyshttpovermc.orm.DATA;
import com.github.cocosoys.mc.soyshttpovermc.orm.query.Query;
import com.github.cocosoys.mc.soyshttpovermc.spring.service.ILocalPermStorage;
import lombok.CustomLog;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * 本地权限表「双后端 IO」默认实现：直接经统一 ORM 门面 {@link DATA} 读写
 * （SQL 可用走 SQL，否则回退 YAML，见 {@code storage.backends.*}）。
 *
 * <p>写入 / 删除失败统一记录告警日志（吞异常返回 false，不向上抛），保证本地权限表
 * 读写失败不影响主链路。</p>
 */
@CustomLog
public class LocalPermStorageImpl implements ILocalPermStorage {

    @Override
    public <T> T get(Class<T> c, Object id) {
        return DATA.get(c, id);
    }

    @Override
    public boolean save(Object bean) {
        boolean ok = false;
        try {
            ok = DATA.insert(bean);
        } catch (Throwable t) {
            log.warnT("log.permission.local-write-failed", "[permission/local] 写入本地权限表失败: {0}", t);
        }
        return ok;
    }

    @Override
    public boolean delete(Class<?> c, Object id) {
        boolean ok = false;
        try {
            ok = DATA.deleteById(c, id);
        } catch (Throwable t) {
            log.warnT("log.permission.local-delete-failed", "[permission/local] 删除本地权限表记录失败: {0}", t);
        }
        return ok;
    }

    @Override
    public <T> List<T> list(Class<T> c, Consumer<Query<T>> cond) {
        List<T> r = DATA.select(c, cond);
        return r != null ? r : Collections.emptyList();
    }
}
