package soys.soyshttpovermc.orm.executor;

import soys.soyshttpovermc.orm.query.ConditionTree;
import soys.soyshttpovermc.orm.query.Page;

import java.util.List;

/**
 * 后端执行器接口（双后端通解：YAML 内存过滤 / SQL 参数化翻译）。
 * 门面 {@code YAML.Pojo / SQL.Pojo（二期）} 通过本接口委托执行。
 */
public interface IBackendExecutor {

    /** 后端标识（yaml / sql）。 */
    String name();

    // ===== 读 =====

    /** 按主键取单条；无返回 null。 */
    <T> T getById(Class<T> beanClass, Object id);

    /** 按条件树查询全部命中（YAML=内存过滤；SQL=WHERE 翻译）。 */
    <T> List<T> selectByTree(Class<T> beanClass, ConditionTree tree);

    /** 按条件树分页查询（YAML=内存过滤后切片；SQL=LIMIT）。 */
    <T> Page<T> selectPageByTree(Class<T> beanClass, ConditionTree tree);

    // ===== 写 =====

    /** 插入（主键已赋值）。 */
    <T> boolean insert(Class<T> beanClass, Object bean);

    /** 按主键更新（整体覆盖字段）。 */
    <T> boolean updateById(Class<T> beanClass, Object bean);

    /** 按主键删除。 */
    <T> boolean deleteById(Class<T> beanClass, Object id);

    // ===== 预留：跨端搜索配置文件通道（二期实现） =====

    /**
     * 【预留】跨端搜索配置文件通道：跨 YAML/SQL 后端按关键字模糊搜索指定字段。
     * 二期实现（YAML=全量扫描 LIKE；SQL=LIKE 查询）。当前未实现，调用抛 UnsupportedOperationException。
     */
    default <T> List<T> search(Class<T> beanClass, String keyword, String... fields) {
        throw new UnsupportedOperationException("跨端搜索配置文件通道预留中（二期实现），请稍候");
    }
}
