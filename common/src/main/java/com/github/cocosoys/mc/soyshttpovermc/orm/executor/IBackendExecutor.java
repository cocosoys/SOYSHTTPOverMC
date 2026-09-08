package com.github.cocosoys.mc.soyshttpovermc.orm.executor;

import com.github.cocosoys.mc.soyshttpovermc.i18n.I18n;
import com.github.cocosoys.mc.soyshttpovermc.orm.query.ConditionTree;
import com.github.cocosoys.mc.soyshttpovermc.orm.query.Page;

import java.util.List;

/**
 * 后端执行器接口（双后端通解：YAML 内存过滤 / SQL 参数化翻译）。
 * 门面 {@code YAML.Pojo / SQL.Pojo} 通过本接口委托执行。
 */
public interface IBackendExecutor {

    /**
     * 后端标识（yaml / sql）。
     */
    String name();

    // ===== 读 =====

    /**
     * 按主键取单条；无返回 null。
     */
    <T> T getById(Class<T> beanClass, Object id);

    /**
     * 按条件树查询全部命中（YAML=内存过滤；SQL=WHERE 翻译）。
     */
    <T> List<T> selectByTree(Class<T> beanClass, ConditionTree tree);

    /**
     * 按条件树分页查询（YAML=内存过滤后切片；SQL=LIMIT）。
     */
    <T> Page<T> selectPageByTree(Class<T> beanClass, ConditionTree tree);

    // ===== 写 =====

    /**
     * 插入（主键已赋值）。
     */
    <T> boolean insert(Class<T> beanClass, Object bean);

    /**
     * 按主键更新（整体覆盖字段）。
     */
    <T> boolean updateById(Class<T> beanClass, Object bean);

    /**
     * 按主键删除。
     */
    <T> boolean deleteById(Class<T> beanClass, Object id);

    // ===== 跨端搜索（YAML=全量扫描 contains；SQL=LIKE 查询） =====

    /**
     * 跨端搜索：跨 YAML/SQL 后端按关键字模糊搜索指定字段（默认 String/Enum 字段）。
     * 默认实现抛 UnsupportedOperationException——各后端必须 override；
     * YAML=全量扫描 contains；SQL=参数化 LIKE 查询。
     */
    default <T> List<T> search(Class<T> beanClass, String keyword, String... fields) {
        throw new UnsupportedOperationException(I18n.t("exception.orm.search-unsupported", "当前后端不支持跨端搜索"));
    }

    /**
     * 跨端搜索分页：按关键字模糊搜索指定字段并分页（YAML=全量扫描后切片；SQL=LIKE + LIMIT）。
     * 默认实现抛 UnsupportedOperationException——各后端必须 override。
     */
    default <T> Page<T> searchPage(Class<T> beanClass, long current, long size, String keyword, String... fields) {
        throw new UnsupportedOperationException(I18n.t("exception.orm.search-unsupported", "当前后端不支持跨端搜索"));
    }
}
