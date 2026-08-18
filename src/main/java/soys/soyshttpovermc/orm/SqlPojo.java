package soys.soyshttpovermc.orm;

import soys.soyshttpovermc.orm.executor.IBackendExecutor;
import soys.soyshttpovermc.orm.executor.SqlBackendExecutor;
import soys.soyshttpovermc.orm.query.ConditionTree;
import soys.soyshttpovermc.orm.query.Page;
import soys.soyshttpovermc.orm.query.Query;

import java.util.List;
import java.util.function.Consumer;

/**
 * SQL 存储的 Pojo 门面（与 {@link YAML.Pojo} 同一 API 表面，后端为 dlz-db-core + HikariCP）：
 * <pre>
 *   List&lt;User&gt; users = SQL.Pojo.select(User.class);
 *   List&lt;User&gt; admins = SQL.Pojo.select(User.class, q -&gt; q.eq(User::getRole, "admin"));
 *   User u = SQL.Pojo.get(User.class, "id-1");
 *   SQL.Pojo.insert(user); SQL.Pojo.updateById(user); SQL.Pojo.deleteById(User.class, "id-1");
 * </pre>
 * 数据源来自 {@code storage.backends.{mysql,sqlite}}（mysql 优先）；表按实体自动创建，
 * 列名与 YAML 端一致（小写下划线）。未装配 SQL 后端时方法返回空/false（见 {@link SqlBackendExecutor}）。
 */
public class SqlPojo {

    /** 当前 SQL 后端（未装配时 null）。 */
    public IBackendExecutor executor() {
        return SqlBackendExecutor.get();
    }

    /** SQL 后端是否可用。 */
    public boolean isAvailable() {
        SqlBackendExecutor e = SqlBackendExecutor.get();
        return e != null && e.isAvailable();
    }

    public <T> List<T> select(Class<T> beanClass) {
        IBackendExecutor e = executor();
        return e == null ? java.util.Collections.emptyList() : e.selectByTree(beanClass, null);
    }

    public <T> List<T> select(Class<T> beanClass, Consumer<Query<T>> condition) {
        Query<T> query = new Query<>(beanClass);
        IBackendExecutor e = executor();
        query.setExecutor(e);
        if (condition != null) {
            condition.accept(query);
        }
        return e == null ? java.util.Collections.emptyList() : e.selectByTree(beanClass, query.tree());
    }

    public <T> Query<T> selectW(Class<T> beanClass) {
        Query<T> query = new Query<>(beanClass);
        query.setExecutor(executor());
        return query;
    }

    public <T> T get(Class<T> beanClass, Object id) {
        IBackendExecutor e = executor();
        return e == null ? null : e.getById(beanClass, id);
    }

    public <T> Page<T> selectPage(Class<T> beanClass, long current, long size) {
        Query<T> query = new Query<>(beanClass);
        IBackendExecutor e = executor();
        query.setExecutor(e);
        query.page(new Page<>(current, size));
        return e == null ? new Page<>(current, size) : e.selectPageByTree(beanClass, query.tree());
    }

    public <T> boolean insert(T bean) {
        IBackendExecutor e = executor();
        return bean != null && e != null && e.insert((Class<T>) bean.getClass(), bean);
    }

    public <T> boolean updateById(T bean) {
        IBackendExecutor e = executor();
        return bean != null && e != null && e.updateById((Class<T>) bean.getClass(), bean);
    }

    public <T> boolean deleteById(Class<T> beanClass, Object id) {
        IBackendExecutor e = executor();
        return e != null && e.deleteById(beanClass, id);
    }

    /** 跨端搜索（LIKE 查询）。 */
    public <T> List<T> search(Class<T> beanClass, String keyword, String... fields) {
        IBackendExecutor e = executor();
        return e == null ? java.util.Collections.emptyList() : e.search(beanClass, keyword, fields);
    }

    // ===== 内部（Query 委托） =====

    public <T> List<T> selectByTree(Class<T> beanClass, ConditionTree tree) {
        IBackendExecutor e = executor();
        return e == null ? java.util.Collections.emptyList() : e.selectByTree(beanClass, tree);
    }

    public <T> Page<T> selectPageByTree(Class<T> beanClass, ConditionTree tree) {
        IBackendExecutor e = executor();
        return e == null ? new Page<>() : e.selectPageByTree(beanClass, tree);
    }
}
