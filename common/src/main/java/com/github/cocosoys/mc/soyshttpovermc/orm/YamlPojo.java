package com.github.cocosoys.mc.soyshttpovermc.orm;

import com.github.cocosoys.mc.soyshttpovermc.i18n.I18n;
import com.github.cocosoys.mc.soyshttpovermc.orm.executor.IBackendExecutor;
import com.github.cocosoys.mc.soyshttpovermc.orm.executor.YamlBackendExecutor;
import com.github.cocosoys.mc.soyshttpovermc.orm.query.ConditionTree;
import com.github.cocosoys.mc.soyshttpovermc.orm.query.Page;
import com.github.cocosoys.mc.soyshttpovermc.orm.query.Query;
import com.github.cocosoys.mc.soyshttpovermc.spi.ConfigSection;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;

/**
 * YAML 存储的 Pojo 门面（仿 dlz-db-core 的 {@code DB.Pojo}，存储为 YAML）：
 * <pre>
 *   List&lt;User&gt; users = YAML.Pojo.select(User.class);                          // 直接返回列表
 *   List&lt;User&gt; admins = YAML.Pojo.select(User.class, q -&gt; q.eq(User::getRole, "admin"));
 *   User u = YAML.Pojo.get(User.class, "id-1");                                // 按主键取单条
 *   YamlConfiguration raw = YAML.Pojo.get(User.class);                        // 原始文件视图
 *   YAML.Pojo.insert(user); YAML.Pojo.updateById(user); YAML.Pojo.deleteById(User.class, "id-1");
 *   Page&lt;User&gt; page = YAML.Pojo.selectPage(User.class, 1, 10);
 * </pre>
 * 数据文件：{@code data/&lt;@TableName&gt;.yml}，根节点为表名，主键值为键（见 {@link YamlBackendExecutor}）。
 */
public class YamlPojo {

    private volatile IBackendExecutor executor;
    private volatile File dataDir = new File("data");

    /**
     * 插件装配：设置数据目录（dataFolder），创建共享 YAML 后端。
     */
    public void init(File dataFolder) {
        this.dataDir = dataFolder == null ? new File("data") : dataFolder;
        this.executor = YamlBackendExecutor.get(this.dataDir);
    }

    /**
     * 当前 YAML 后端是否已装配（{@link #init(File)} 已调用）。
     * 与 {@link SqlPojo#isAvailable()} 对称，供上层在 SQL / YAML 后端间选择。
     */
    public boolean isAvailable() {
        return executor != null;
    }

    /**
     * 当前后端（未 init 时按默认 data/ 懒初始化）。
     */
    public IBackendExecutor executor() {
        IBackendExecutor e = executor;
        if (e == null) {
            synchronized (this) {
                e = executor;
                if (e == null) {
                    e = YamlBackendExecutor.get(dataDir);
                    executor = e;
                }
            }
        }
        return e;
    }

    // ===== 查询 =====

    /**
     * 查询全部（直接返回 List）。
     */
    public <T> List<T> select(Class<T> beanClass) {
        return executor().selectByTree(beanClass, null);
    }

    /**
     * 按条件查询（Consumer 构建条件链）。
     */
    public <T> List<T> select(Class<T> beanClass, Consumer<Query<T>> condition) {
        Query<T> query = new Query<>(beanClass);
        query.setExecutor(executor());
        if (condition != null) {
            condition.accept(query);
        }
        return executor().selectByTree(beanClass, query.tree());
    }

    /**
     * 链式查询构建器（返回 Query，可继续 eq/orderBy 后 queryBeanList()）。
     */
    public <T> Query<T> selectW(Class<T> beanClass) {
        Query<T> query = new Query<>(beanClass);
        query.setExecutor(executor());
        return query;
    }

    /**
     * 按主键取单条；无返回 null。
     */
    public <T> T get(Class<T> beanClass, Object id) {
        return executor().getById(beanClass, id);
    }

    /**
     * 分页查询（YAML 全量过滤后切片）。
     */
    public <T> Page<T> selectPage(Class<T> beanClass, long current, long size) {
        Query<T> query = new Query<>(beanClass);
        query.setExecutor(executor());
        query.page(new Page<>(current, size));
        return executor().selectPageByTree(beanClass, query.tree());
    }

    // ===== 原始文件视图 =====

    /**
     * 获取实体对应文件的 {@link YamlConfiguration} 原始视图（FileConfiguration）。
     * 返回 ORM 共享的缓存视图：可自由读；外部写入后需调用 {@link #save(Class)} 落盘。
     */
    public ConfigSection get(Class<?> beanClass) {
        IBackendExecutor e = executor();
        if (e instanceof YamlBackendExecutor) {
            return ((YamlBackendExecutor) e).getConfig(beanClass);
        }
        throw new IllegalStateException(I18n.t("exception.orm.not-yaml-backend", "当前后端不是 YAML：{0}", e == null ? "null" : e.name()));
    }

    /**
     * 把 {@link #get(Class)} 视图的修改显式落盘（原子写）。
     */
    public void save(Class<?> beanClass) {
        IBackendExecutor e = executor();
        if (e instanceof YamlBackendExecutor) {
            ((YamlBackendExecutor) e).save(beanClass);
        }
    }

    // ===== 写 =====

    /**
     * 插入（主键必须已赋值）。
     */
    public <T> boolean insert(T bean) {
        return bean != null && executor().insert((Class<T>) bean.getClass(), bean);
    }

    /**
     * 按主键更新（YAML 端整体覆盖 = upsert）。
     */
    public <T> boolean updateById(T bean) {
        return bean != null && executor().updateById((Class<T>) bean.getClass(), bean);
    }

    /**
     * 按主键删除。
     */
    public <T> boolean deleteById(Class<T> beanClass, Object id) {
        return executor().deleteById(beanClass, id);
    }

    // ===== 内部（Query 委托） =====

    /**
     * 供 {@link Query#queryBeanList()} 委托。
     */
    public <T> List<T> selectByTree(Class<T> beanClass, ConditionTree tree) {
        return executor().selectByTree(beanClass, tree);
    }

    /**
     * 供 {@link Query#queryBeanPage()} 委托。
     */
    public <T> Page<T> selectPageByTree(Class<T> beanClass, ConditionTree tree) {
        return executor().selectPageByTree(beanClass, tree);
    }

    // ===== 预留：跨端搜索配置文件通道（二期实现） =====

    /**
     * 【预留】跨端搜索：按关键字在指定字段上模糊搜索（跨 YAML/SQL 后端统一入口）。
     * 二期实现；当前调用抛 UnsupportedOperationException。
     */
    public <T> List<T> search(Class<T> beanClass, String keyword, String... fields) {
        return executor().search(beanClass, keyword, fields);
    }
}
