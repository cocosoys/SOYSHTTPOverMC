package com.github.cocosoys.mc.soyshttpovermc.orm;

import com.github.cocosoys.mc.soyshttpovermc.orm.query.ConditionTree;
import com.github.cocosoys.mc.soyshttpovermc.orm.query.Page;
import com.github.cocosoys.mc.soyshttpovermc.orm.executor.IBackendExecutor;
import com.github.cocosoys.mc.soyshttpovermc.orm.query.Query;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 双存储路由门面（SQL / YAML 双兼容，业务层统一入口）：
 * <pre>
 *   List&lt;User&gt; users = Store.select(User.class);
 *   User u = Store.get(User.class, "id-1");
 *   Store.insert(user);
 *   Page&lt;User&gt; page = Store.selectPage(User.class, 1, 10);
 * </pre>
 * 路由规则：SOYS 配置了 mysql/sqlite 存储（{@code storage.backends.mysql/sqlite}）且已装配时
 * 自动走 SQL（{@link SQL#Pojo}），否则回退 YAML 文件存储（{@link YAML#Pojo}，
 * {@code data/&lt;表名&gt;.yml}）。业务层无需感知后端差异，也无需自行书写
 * {@code if (SQL.Pojo.isAvailable()) ... else ...} 分支——本门面统一路由。
 *
 * <p><b>边界</b>：{@link DATA} 只路由 SQL / YAML 的<b>对称方法</b>。YAML 独有的原始配置视图
 * （{@link YamlPojo#get(Class)}、{@link YamlPojo#save(Class)}）与装配入口
 * （{@code YAML.Pojo.init(File)}）不属于路由范围，需要时请直接访问 {@code YAML.Pojo}；
 * 底层执行器请经 {@link #executor()} 获取（Query 兜底等内部场景使用，业务层一般无需直接访问执行器）。</p>
 *
 * <p><b>异常</b>：SQL 与 YAML 后端均不可用时，所有操作抛出 {@link IllegalStateException}
 * 而非静默失败（正常配置下 YAML 懒初始化后恒可用，不会触发）。</p>
 */
public final class DATA {

    private DATA() {
    }

    /**
     * SQL 后端是否可用（配置了 mysql/sqlite 且已装配）。
     */
    public static boolean sqlEnabled() {
        return SQL.Pojo.isAvailable();
    }

    /**
     * 当前后端执行器（仅供 ORM 内部 / Query 兜底使用；业务层一般无需直接访问执行器）。
     */
    public static IBackendExecutor executor() {
        return route(p -> p.executor(), p -> p.executor());
    }

    /**
     * 路由执行：SQL 可用 → SQL.Pojo；否则 → YAML.Pojo；双不可用 → IllegalStateException。
     */
    public static <T> T route(Function<SqlPojo, T> sqlFn, Function<YamlPojo, T> yamlFn) {
        if (SQL.Pojo.isAvailable()) return sqlFn.apply(SQL.Pojo);
        if (YAML.Pojo.isAvailable()) return yamlFn.apply(YAML.Pojo);
        throw new IllegalStateException(
                "ORM 存储不可用：SQL 与 YAML 后端均未装配（请检查 storage.backends 配置或 data 目录）");
    }

    // ===== 读 =====

    /**
     * 查询全部（当前后端路由）。
     */
    public static <T> List<T> select(Class<T> beanClass) {
        return route(p -> p.select(beanClass), p -> p.select(beanClass));
    }

    /**
     * 条件查询（lambda 条件链，如 {@code q -> q.eq(User::getRole, "admin")}）。
     */
    public static <T> List<T> select(Class<T> beanClass, Consumer<Query<T>> condition) {
        return route(p -> p.select(beanClass, condition), p -> p.select(beanClass, condition));
    }

    /**
     * 条件链入口：返回的 Query 已按当前后端注入执行器，
     * 之后调用 {@link Query#queryBeanList()} / {@link Query#queryBean()} / {@link Query#queryBeanPage()} 执行。
     */
    public static <T> Query<T> selectW(Class<T> beanClass) {
        return route(p -> p.selectW(beanClass), p -> p.selectW(beanClass));
    }

    /**
     * 按主键取单条；无结果返回 null。
     */
    public static <T> T get(Class<T> beanClass, Object id) {
        return route(p -> p.get(beanClass, id), p -> p.get(beanClass, id));
    }

    /**
     * 分页查询（current 从 1 起）。
     */
    public static <T> Page<T> selectPage(Class<T> beanClass, long current, long size) {
        return route(p -> p.selectPage(beanClass, current, size), p -> p.selectPage(beanClass, current, size));
    }

    /**
     * 条件树查询（内部结构，一般用 {@link #select(Class, Consumer)}）。
     */
    public static <T> List<T> selectByTree(Class<T> beanClass, ConditionTree tree) {
        return route(p -> p.selectByTree(beanClass, tree), p -> p.selectByTree(beanClass, tree));
    }

    /**
     * 条件树分页查询。
     */
    public static <T> Page<T> selectPageByTree(Class<T> beanClass, ConditionTree tree) {
        return route(p -> p.selectPageByTree(beanClass, tree), p -> p.selectPageByTree(beanClass, tree));
    }

    // ===== 写 =====

    /**
     * 插入（主键已存在时取决于后端语义，通常返回 false）。
     */
    public static <T> boolean insert(T bean) {
        return route(p -> p.insert(bean), p -> p.insert(bean));
    }

    /**
     * 按主键更新。
     */
    public static <T> boolean updateById(T bean) {
        return route(p -> p.updateById(bean), p -> p.updateById(bean));
    }

    /**
     * 按主键删除。
     */
    public static <T> boolean deleteById(Class<T> beanClass, Object id) {
        return route(p -> p.deleteById(beanClass, id), p -> p.deleteById(beanClass, id));
    }

    // ===== 跨端搜索（二期：当前后端可能抛 UnsupportedOperationException） =====

    /**
     * 跨端关键字搜索（YAML=全量扫描 contains；SQL=LIKE 查询；二期实现，当前可能抛异常）。
     */
    public static <T> List<T> search(Class<T> beanClass, String keyword, String... fields) {
        return route(p -> p.search(beanClass, keyword, fields), p -> p.search(beanClass, keyword, fields));
    }

    /**
     * 跨端关键字分页搜索（二期实现，当前可能抛异常）。
     */
    public static <T> Page<T> searchPage(Class<T> beanClass, long current, long size, String keyword, String... fields) {
        return route(p -> p.searchPage(beanClass, current, size, keyword, fields),
                p -> p.searchPage(beanClass, current, size, keyword, fields));
    }
}
