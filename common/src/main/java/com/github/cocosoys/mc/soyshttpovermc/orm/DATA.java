package com.github.cocosoys.mc.soyshttpovermc.orm;

import com.github.cocosoys.mc.soyshttpovermc.enums.StorageType;
import com.github.cocosoys.mc.soyshttpovermc.orm.executor.IBackendExecutor;
import com.github.cocosoys.mc.soyshttpovermc.orm.executor.SecondarySqlBackend;
import com.github.cocosoys.mc.soyshttpovermc.orm.executor.SqlBackendExecutor;
import com.github.cocosoys.mc.soyshttpovermc.orm.executor.YamlBackendExecutor;
import com.github.cocosoys.mc.soyshttpovermc.orm.meta.FieldMeta;
import com.github.cocosoys.mc.soyshttpovermc.orm.meta.PojoMeta;
import com.github.cocosoys.mc.soyshttpovermc.orm.query.ConditionTree;
import com.github.cocosoys.mc.soyshttpovermc.orm.query.Page;
import com.github.cocosoys.mc.soyshttpovermc.orm.query.Query;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 单主存储路由门面（SQL / YAML 兼容，业务层统一入口）：
 * <pre>
 *   List&lt;User&gt; users = DATA.select(User.class);
 *   User u = DATA.get(User.class, "id-1");
 *   DATA.insert(user);
 *   Page&lt;User&gt; page = DATA.selectPage(User.class, 1, 10);
 * </pre>
 * <b>单主语义</b>：默认读写只落在 priority 最高的已启用后端（{@code storage.backends.*}：
 * MYSQL 30 &gt; SQLITE 20 &gt; YAML 10），SQL 可用时走 SQL（{@link SQL#Pojo}），否则 YAML
 * （{@link YAML#Pojo}）。写不镜像到其他后端。
 *
 * <p><b>指定类型读写</b>：其余已启用后端仍可被访问——所有方法均提供带
 * {@link StorageType} 参数的重载（如 {@link #get(StorageType, Class, Object)}、
 * {@link #upsertAll(StorageType, Class, List)}），显式选择后端执行读写，操作与默认
 * 读写无异常、仅多一个后端类型参数。SQL 类型若为当前装配的主 SQL 则复用主执行器
 * （避免双连接池），否则经 {@link SecondarySqlBackend} 独立直连。
 * 批量/运维方法（{@link #ensureTable(Class)}/{@link #count(Class)}/{@link #clear(Class)}/
 * {@link #upsertAll(Class, List)}/{@link #mergeAll(Class, List)}）另有无 type 版本，
 * 自动识别当前使用的主数据库（见 {@link #primaryType()}，按实际装配判定）。</p>
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

    // ===== 跨端搜索（主存储；指定类型与多后端版本见下方对应区段） =====

    /**
     * 主存储关键字模糊搜索（SQL=LIKE 字面包含、YAML/非主 SQL=全量 contains，三者语义一致）；
     * 后端不支持或查询失败时静默降级为空列表。
     */
    public static <T> List<T> search(Class<T> beanClass, String keyword, String... fields) {
        return route(p -> p.search(beanClass, keyword, fields), p -> p.search(beanClass, keyword, fields));
    }

    /**
     * 主存储关键字模糊分页搜索（失败时静默降级为空页）。
     */
    public static <T> Page<T> searchPage(Class<T> beanClass, long current, long size, String keyword, String... fields) {
        return route(p -> p.searchPage(beanClass, current, size, keyword, fields),
                p -> p.searchPage(beanClass, current, size, keyword, fields));
    }

    // ===== 指定类型读写（显式选择后端，绕过默认单主路由） =====

    /**
     * 按类型解析后端执行器：YAML → YamlBackendExecutor；SQL 类型 → 若该类型是当前装配的
     * 主 SQL（{@link SqlBackendExecutor#name()} == type）则复用主执行器（避免双连接池），
     * 否则经 {@link SecondarySqlBackend} 独立直连；后端未启用/未装配返回 null。
     */
    private static IBackendExecutor executorOf(StorageType type) {
        if (type == null) {
            return null;
        }
        if (type == StorageType.YAML) {
            return YamlBackendExecutor.get(YAML.Pojo.getDataDir());
        }
        SqlBackendExecutor main = mainSqlOf(type);
        return main != null ? main : SecondarySqlBackend.of(type);
    }

    /** 当前装配的主 SQL 执行器；仅当其类型与指定类型一致时返回（避免同一数据库双连接池）。 */
    private static SqlBackendExecutor mainSqlOf(StorageType type) {
        SqlBackendExecutor main = SqlBackendExecutor.get();
        if (main != null && main.isAvailable() && type != StorageType.YAML
                && type.getId().equals(main.name())) {
            return main;
        }
        return null;
    }

    /**
     * 指定类型取单条（如 {@code DATA.get(StorageType.SQLITE, User.class, 1L)}）；无结果返回 null。
     */
    public static <T> T get(StorageType type, Class<T> beanClass, Object id) {
        IBackendExecutor e = executorOf(type);
        return e == null ? null : e.getById(beanClass, id);
    }

    /**
     * 指定类型查询全部。
     */
    public static <T> List<T> select(StorageType type, Class<T> beanClass) {
        IBackendExecutor e = executorOf(type);
        return e == null ? java.util.Collections.emptyList() : e.selectByTree(beanClass, null);
    }

    /**
     * 指定类型条件查询（lambda 条件链）。
     */
    public static <T> List<T> select(StorageType type, Class<T> beanClass, Consumer<Query<T>> condition) {
        IBackendExecutor e = executorOf(type);
        if (e == null) {
            return java.util.Collections.emptyList();
        }
        Query<T> query = new Query<>(beanClass);
        query.setExecutor(e);
        if (condition != null) {
            condition.accept(query);
        }
        return e.selectByTree(beanClass, query.tree());
    }

    /**
     * 指定类型条件链入口（Query 已按指定后端注入执行器）。
     */
    public static <T> Query<T> selectW(StorageType type, Class<T> beanClass) {
        Query<T> query = new Query<>(beanClass);
        query.setExecutor(executorOf(type));
        return query;
    }

    /**
     * 指定类型分页查询（current 从 1 起）。
     */
    public static <T> Page<T> selectPage(StorageType type, Class<T> beanClass, long current, long size) {
        IBackendExecutor e = executorOf(type);
        if (e == null) {
            return new Page<>(current, size);
        }
        Query<T> query = new Query<>(beanClass);
        query.setExecutor(e);
        query.page(new Page<>(current, size));
        return e.selectPageByTree(beanClass, query.tree());
    }

    /**
     * 指定类型插入。
     */
    public static <T> boolean insert(StorageType type, T bean) {
        IBackendExecutor e = executorOf(type);
        return bean != null && e != null && e.insert((Class<T>) bean.getClass(), bean);
    }

    /**
     * 指定类型按主键更新。
     */
    public static <T> boolean updateById(StorageType type, T bean) {
        IBackendExecutor e = executorOf(type);
        return bean != null && e != null && e.updateById((Class<T>) bean.getClass(), bean);
    }

    /**
     * 指定类型按主键删除。
     */
    public static <T> boolean deleteById(StorageType type, Class<T> beanClass, Object id) {
        IBackendExecutor e = executorOf(type);
        return e != null && e.deleteById(beanClass, id);
    }

    /**
     * 指定类型关键字模糊搜索（语义同 {@link #search(Class, String, String...)}，显式指定后端）。
     */
    public static <T> List<T> search(StorageType type, Class<T> beanClass, String keyword, String... fields) {
        IBackendExecutor e = executorOf(type);
        return e == null ? java.util.Collections.emptyList() : e.search(beanClass, keyword, fields);
    }

    /**
     * 指定类型关键字模糊分页搜索（语义同 {@link #searchPage(Class, long, long, String, String...)}）。
     */
    public static <T> Page<T> searchPage(StorageType type, Class<T> beanClass, long current, long size,
                                         String keyword, String... fields) {
        IBackendExecutor e = executorOf(type);
        return e == null ? new Page<>(current, size) : e.searchPage(beanClass, current, size, keyword, fields);
    }

    // ===== 主存储批量（无 type 参数，自动识别当前使用的主数据库） =====

    /**
     * 识别当前使用的主数据库类型（与默认路由语义一致）：
     * SQL 后端已装配 → 主 SQL 执行器的类型（mysql / sqlite）；否则 → YAML。
     * 与 {@link Backends#primary()}（仅按配置 enabled + priority 判定）不同：
     * 此处以<b>实际装配</b>为准，装配失败时回退 YAML，与 {@link #route} 行为一致。
     */
    public static StorageType primaryType() {
        SqlBackendExecutor main = SqlBackendExecutor.get();
        if (main != null && main.isAvailable()) {
            StorageType t = StorageType.fromId(main.name());
            if (t != null) {
                return t;
            }
        }
        return StorageType.YAML;
    }

    /**
     * 当前主数据库建表（语义同 {@link #ensureTable(StorageType, Class)}）。
     */
    public static void ensureTable(Class<?> beanClass) {
        ensureTable(primaryType(), beanClass);
    }

    /**
     * 当前主数据库行数（语义同 {@link #count(StorageType, Class)}）。
     */
    public static long count(Class<?> beanClass) {
        return count(primaryType(), beanClass);
    }

    /**
     * 当前主数据库清空整表（语义同 {@link #clear(StorageType, Class)}）。
     */
    public static void clear(Class<?> beanClass) {
        clear(primaryType(), beanClass);
    }

    /**
     * 当前主数据库覆盖写入（语义同 {@link #upsertAll(StorageType, Class, List)}）。
     */
    public static <T> int upsertAll(Class<T> beanClass, List<T> beans) {
        return upsertAll(primaryType(), beanClass, beans);
    }

    /**
     * 当前主数据库合并写入（语义同 {@link #mergeAll(StorageType, Class, List)}）。
     */
    public static <T> int mergeAll(Class<T> beanClass, List<T> beans) {
        return mergeAll(primaryType(), beanClass, beans);
    }

    // ===== 指定类型批量（sync/migrate 与运维场景） =====

    /**
     * 指定类型建表（CREATE TABLE IF NOT EXISTS + 容忍补列；YAML 无需建表，文件惰性创建）。
     */
    public static void ensureTable(StorageType type, Class<?> beanClass) {
        if (type == StorageType.YAML) {
            return;
        }
        SqlBackendExecutor main = mainSqlOf(type);
        if (main != null) {
            main.ensureTable(beanClass);
            return;
        }
        SecondarySqlBackend sec = SecondarySqlBackend.of(type);
        if (sec != null) {
            sec.ensureTable(beanClass);
        }
    }

    /**
     * 指定类型行数（SQL COUNT(*)，YAML 内存视图键数；不可读返回 -1）。
     */
    public static long count(StorageType type, Class<?> beanClass) {
        if (type == StorageType.YAML) {
            return YamlBackendExecutor.get(YAML.Pojo.getDataDir()).count(beanClass);
        }
        SqlBackendExecutor main = mainSqlOf(type);
        if (main != null) {
            return main.count(beanClass);
        }
        SecondarySqlBackend sec = SecondarySqlBackend.of(type);
        return sec == null ? -1 : sec.count(beanClass);
    }

    /**
     * 指定类型清空整表（覆盖语义的"清"；SQL 端 DELETE FROM，YAML 端内存清空 + 原子落盘）。
     */
    public static void clear(StorageType type, Class<?> beanClass) {
        if (type == StorageType.YAML) {
            YamlBackendExecutor.get(YAML.Pojo.getDataDir()).clear(beanClass);
            return;
        }
        SqlBackendExecutor main = mainSqlOf(type);
        if (main != null) {
            main.clear(beanClass);
            return;
        }
        SecondarySqlBackend sec = SecondarySqlBackend.of(type);
        if (sec != null) {
            sec.clear(beanClass);
        }
    }

    /**
     * 指定类型覆盖写入（先清后写；SQL 端同一事务失败回滚，YAML 端内存构建后一次性原子落盘）。
     *
     * @return 实际写入条数
     */
    public static <T> int upsertAll(StorageType type, Class<T> beanClass, List<T> beans) {
        if (type == StorageType.YAML) {
            return YamlBackendExecutor.get(YAML.Pojo.getDataDir()).replaceAll(beanClass, beans);
        }
        SqlBackendExecutor main = mainSqlOf(type);
        if (main != null) {
            return main.upsertAll(beanClass, beans);
        }
        SecondarySqlBackend sec = SecondarySqlBackend.of(type);
        return sec == null ? 0 : sec.upsertAll(beanClass, beans);
    }

    /**
     * 指定类型合并写入（按主键 upsert，不清空目标；SQL 端同一事务失败回滚，YAML 端内存构建后一次落盘）。
     *
     * @return 实际写入条数
     */
    public static <T> int mergeAll(StorageType type, Class<T> beanClass, List<T> beans) {
        if (type == StorageType.YAML) {
            return YamlBackendExecutor.get(YAML.Pojo.getDataDir()).mergeAll(beanClass, beans);
        }
        SqlBackendExecutor main = mainSqlOf(type);
        if (main != null) {
            return main.mergeAll(beanClass, beans);
        }
        SecondarySqlBackend sec = SecondarySqlBackend.of(type);
        return sec == null ? 0 : sec.mergeAll(beanClass, beans);
    }

    // ===== 跨端搜索（指定范围多后端汇总；单后端见指定类型区段） =====

    /**
     * 在指定的多个后端中搜索并汇总（跨端搜索）：
     * 按 {@code typeIds} 顺序依次搜索（仅已启用后端），按<b>主键</b>去重合并，
     * 先命中的后端优先保留（后续后端同主键结果被忽略）。后端未知 / 未启用自动跳过。
     *
     * @param typeIds 后端类型 id 数组（如 {@code {"mysql", "sqlite", "yaml"}}）
     */
    public static <T> List<T> searchAcross(String[] typeIds, Class<T> beanClass, String keyword, String... fields) {
        if (typeIds == null || typeIds.length == 0) {
            return java.util.Collections.emptyList();
        }
        PojoMeta meta = PojoMeta.of(beanClass);
        LinkedHashMap<String, T> merged = new LinkedHashMap<>();
        for (String id : typeIds) {
            StorageType t = StorageType.fromId(id);
            if (t == null || !Backends.isEnabled(t)) {
                continue;
            }
            for (T bean : search(t, beanClass, keyword, fields)) {
                merged.putIfAbsent(idKey(bean, meta), bean);
            }
        }
        return new ArrayList<>(merged.values());
    }

    /**
     * 跨端搜索分页：多后端汇总去重后统一分页（total = 去重后总数）。
     */
    public static <T> Page<T> searchPageAcross(String[] typeIds, Class<T> beanClass, long current, long size,
                                               String keyword, String... fields) {
        Page<T> page = new Page<>(current, size);
        if (keyword == null || keyword.isEmpty()) {
            return page;
        }
        List<T> all = searchAcross(typeIds, beanClass, keyword, fields);
        page.setTotal(all.size());
        long offset = page.offset();
        int from = (int) Math.min(offset, all.size());
        int to = (int) Math.min(offset + page.getSize(), all.size());
        page.setRecords((List) new ArrayList<>(all.subList(from, to)));
        return page;
    }

    /** 实体主键去重键；无主键 / 主键为 null 时退化为实例标识（ORM 实体均含 @TableId，正常不触发）。 */
    private static String idKey(Object bean, PojoMeta meta) {
        try {
            for (FieldMeta fm : meta.getFields()) {
                if (fm.isPrimaryKey()) {
                    fm.field.setAccessible(true);
                    Object v = fm.field.get(bean);
                    return v == null ? "@" + System.identityHashCode(bean) : String.valueOf(v);
                }
            }
        } catch (IllegalAccessException ignored) {
        }
        return "@" + System.identityHashCode(bean);
    }
}
