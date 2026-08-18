package soys.soyshttpovermc.orm.executor;

import soys.soyshttpovermc.log.LogKit;
import soys.soyshttpovermc.orm.convertor.BeanCodec;
import soys.soyshttpovermc.orm.meta.FieldMeta;
import soys.soyshttpovermc.orm.meta.PojoMeta;
import soys.soyshttpovermc.orm.query.ConditionTree;
import soys.soyshttpovermc.orm.query.Page;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * YAML 后端执行器（零依赖先行）：实体 ↔ {@code data/&lt;表名&gt;.yml} 文件。
 * <p>文件布局（与 SQL 表同构）：</p>
 * <pre>
 * user:                      ← @TableName("user") 根节点
 *   "id-1":                  ← @TableId 主键值作键
 *     name: "a"
 *     role: "admin"
 *     create_time: 1759...   ← 列名驼峰转下划线；Date 存毫秒
 * </pre>
 * <ul>
 *   <li>读：全量加载 YamlConfiguration（内存缓存文件视图），按 {@link ConditionTree} 逐条求值（O(n)）；</li>
 *   <li>写：对象锁 + 临时文件 rename 原子替换（同本项目 YamlStorage.flush 模式）。</li>
 * </ul>
 */
public class YamlBackendExecutor implements IBackendExecutor {

    private static final Map<File, YamlBackendExecutor> INSTANCES = new ConcurrentHashMap<>();

    private final File dataDir;
    private final Object lock = new Object();
    /** 表名 → 文件视图（惰性加载，写时更新）。 */
    private final Map<String, YamlConfiguration> cache = new ConcurrentHashMap<>();

    public YamlBackendExecutor(File dataDir) {
        this.dataDir = dataDir == null ? new File("data") : dataDir;
    }

    /** 获取共享实例（按 dataDir 缓存，插件装配时 init 一次）。 */
    public static YamlBackendExecutor get(File dataDir) {
        return INSTANCES.computeIfAbsent(dataDir == null ? new File("data") : dataDir,
                YamlBackendExecutor::new);
    }

    @Override
    public String name() {
        return "yaml";
    }

    // ===== 文件视图 =====

    /** 数据目录。 */
    public File getDataDir() {
        return dataDir;
    }

    /** 实体对应的数据文件（data/&lt;表名&gt;.yml）。 */
    public File fileOf(Class<?> beanClass) {
        return new File(dataDir, PojoMeta.of(beanClass).getTableName() + ".yml");
    }

    /**
     * 获取实体对应文件的 YamlConfiguration 视图（{@code YAML.Pojo.get(User.class)} 的返回对象）。
     * 返回的是 ORM 共享的缓存视图：ORM 写路径与外部写共用同一实例与锁，
     * 外部修改后需调用 {@link #save(Class)} 显式落盘。
     */
    public YamlConfiguration getConfig(Class<?> beanClass) {
        String table = PojoMeta.of(beanClass).getTableName();
        YamlConfiguration config = cache.get(table);
        if (config != null) {
            return config;
        }
        synchronized (lock) {
            config = cache.get(table);
            if (config == null) {
                File file = fileOf(beanClass);
                if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
                    LogKit.warn("[HTTP-Over-MC] [ORM] 无法创建数据目录: " + file.getParentFile());
                }
                config = file.isFile() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
                if (!config.isConfigurationSection(table)) {
                    config.createSection(table);
                }
                cache.put(table, config);
            }
            return config;
        }
    }

    /** 显式落盘（getConfig 视图被外部修改后调用）。 */
    public void save(Class<?> beanClass) {
        synchronized (lock) {
            String table = PojoMeta.of(beanClass).getTableName();
            YamlConfiguration config = cache.get(table);
            if (config == null) return;
            flush(config, fileOf(beanClass));
        }
    }

    /** 原子写：写临时文件后 rename 替换。 */
    private void flush(YamlConfiguration config, File target) {
        try {
            if (!target.getParentFile().exists() && !target.getParentFile().mkdirs()) {
                LogKit.warn("[HTTP-Over-MC] [ORM] 无法创建数据目录: " + target.getParentFile());
                return;
            }
            File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
            config.save(tmp);
            Files.move(tmp.toPath(), target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException e) {
            LogKit.warn("[HTTP-Over-MC] [ORM] YAML 落盘失败: " + e.getMessage());
        }
    }

    // ===== 读 =====

    @Override
    public <T> T getById(Class<T> beanClass, Object id) {
        if (id == null) return null;
        PojoMeta meta = PojoMeta.of(beanClass);
        if (!meta.hasId()) {
            throw new IllegalArgumentException("实体 " + beanClass.getSimpleName() + " 未标注 @TableId");
        }
        ConfigurationSection root = getConfig(beanClass).getConfigurationSection(meta.getTableName());
        ConfigurationSection section = root == null ? null : root.getConfigurationSection(String.valueOf(id));
        return BeanCodec.deserialize(beanClass, section);
    }

    @Override
    public <T> List<T> selectByTree(Class<T> beanClass, ConditionTree tree) {
        PojoMeta meta = PojoMeta.of(beanClass);
        ConfigurationSection root = getConfig(beanClass).getConfigurationSection(meta.getTableName());
        List<T> out = new ArrayList<>();
        if (root == null) return out;
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) continue;
            T bean = BeanCodec.deserialize(beanClass, section);
            if (bean == null) continue;
            if (tree == null || tree.matches(QueryValueAccessor(bean, meta))) {
                out.add(bean);
            }
        }
        applyOrders(out, meta, tree);
        Page<?> page = tree == null ? null : tree.getPage();
        if (page != null) {
            long offset = page.offset();
            int from = (int) Math.min(offset, out.size());
            int to = (int) Math.min(offset + page.getSize(), out.size());
            List<T> sliced = new ArrayList<>(out.subList(from, to));
            page.setTotal(out.size());
            page.setRecords((List) sliced);
            return sliced;
        }
        return out;
    }

    @Override
    public <T> Page<T> selectPageByTree(Class<T> beanClass, ConditionTree tree) {
        Page<T> page = new Page<>(tree != null && tree.getPage() != null
                ? tree.getPage().getCurrent() : 1, tree != null && tree.getPage() != null
                ? tree.getPage().getSize() : 10);
        ConditionTree copy = new ConditionTree();
        if (tree != null) {
            for (ConditionTree.Cond c : tree.getConditions()) {
                copy.add(c.column, c.op, c.value, c.and);
            }
            for (ConditionTree.OrderBy o : tree.getOrders()) {
                copy.orderBy(o.column, o.direction);
            }
        }
        copy.setPage(page);
        selectByTree(beanClass, copy);
        return page;
    }

    private static java.util.function.Function<String, Object> QueryValueAccessor(Object bean, PojoMeta meta) {
        return column -> {
            FieldMeta fm = meta.byColumn(column);
            if (fm == null) return null;
            try {
                fm.field.setAccessible(true);
                Object v = fm.field.get(bean);
                return v instanceof java.util.Date ? ((java.util.Date) v).getTime() : v;
            } catch (IllegalAccessException e) {
                return null;
            }
        };
    }

    private <T> void applyOrders(List<T> list, PojoMeta meta, ConditionTree tree) {
        if (tree == null || tree.getOrders().isEmpty()) return;
        Comparator<T> comparator = null;
        for (ConditionTree.OrderBy o : tree.getOrders()) {
            FieldMeta fm = meta.byColumn(o.column);
            if (fm == null) continue;
            Comparator<T> c = Comparator.comparing(bean -> {
                try {
                    fm.field.setAccessible(true);
                    Object v = fm.field.get(bean);
                    return v == null ? "" : v.toString();
                } catch (IllegalAccessException e) {
                    return "";
                }
            });
            if (o.direction == ConditionTree.Order.DESC) {
                c = c.reversed();
            }
            comparator = comparator == null ? c : comparator.thenComparing(c);
        }
        if (comparator != null) {
            list.sort(comparator);
        }
    }

    // ===== 写 =====

    @Override
    public <T> boolean insert(Class<T> beanClass, Object bean) {
        if (bean == null) return false;
        PojoMeta meta = PojoMeta.of(beanClass);
        Object id = idOf(meta, bean);
        if (id == null) {
            throw new IllegalArgumentException("实体 " + beanClass.getSimpleName() + " 主键(@TableId)未赋值，无法插入");
        }
        synchronized (lock) {
            YamlConfiguration config = getConfig(beanClass);
            String base = meta.getTableName() + "." + id;
            config.set(base, null);
            ConfigurationSection section = config.createSection(base);
            BeanCodec.serialize(bean, section);
            flush(config, fileOf(beanClass));
        }
        return true;
    }

    @Override
    public <T> boolean updateById(Class<T> beanClass, Object bean) {
        return insert(beanClass, bean); // YAML 布局按主键覆盖 = upsert
    }

    @Override
    public <T> boolean deleteById(Class<T> beanClass, Object id) {
        if (id == null) return false;
        PojoMeta meta = PojoMeta.of(beanClass);
        synchronized (lock) {
            YamlConfiguration config = getConfig(beanClass);
            String path = meta.getTableName() + "." + id;
            if (!config.isSet(path)) return false;
            config.set(path, null);
            flush(config, fileOf(beanClass));
        }
        return true;
    }

    private Object idOf(PojoMeta meta, Object bean) {
        FieldMeta id = meta.getIdField();
        if (id == null) return null;
        try {
            id.field.setAccessible(true);
            return id.field.get(bean);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    // ===== 跨端搜索通道（实现：全量扫描 LIKE） =====

    @Override
    public <T> List<T> search(Class<T> beanClass, String keyword, String... fields) {
        if (keyword == null || keyword.isEmpty()) return new ArrayList<>();
        PojoMeta meta = PojoMeta.of(beanClass);
        List<String> targets = new ArrayList<>();
        if (fields == null || fields.length == 0) {
            for (FieldMeta fm : meta.getFields()) {
                if (!fm.isIgnored() && (fm.type == String.class || fm.type.isEnum())) {
                    targets.add(fm.columnName);
                }
            }
        } else {
            for (String f : fields) {
                FieldMeta fm = meta.byField(f);
                if (fm != null) targets.add(fm.columnName);
            }
        }
        if (targets.isEmpty()) return new ArrayList<>();
        String kw = keyword.toLowerCase();
        ConfigurationSection root = getConfig(beanClass).getConfigurationSection(meta.getTableName());
        List<T> out = new ArrayList<>();
        if (root == null) return out;
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) continue;
            T bean = BeanCodec.deserialize(beanClass, section);
            if (bean == null) continue;
            for (String col : targets) {
                Object v = QueryValueAccessor(bean, meta).apply(col);
                if (v != null && String.valueOf(v).toLowerCase().contains(kw)) {
                    out.add(bean);
                    break;
                }
            }
        }
        return out;
    }
}
