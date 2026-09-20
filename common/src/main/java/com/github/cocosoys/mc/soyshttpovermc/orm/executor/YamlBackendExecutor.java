package com.github.cocosoys.mc.soyshttpovermc.orm.executor;

import com.github.cocosoys.mc.soyshttpovermc.enums.StorageType;
import com.github.cocosoys.mc.soyshttpovermc.i18n.I18n;
import com.github.cocosoys.mc.soyshttpovermc.orm.convertor.AuditFields;
import com.github.cocosoys.mc.soyshttpovermc.orm.convertor.BeanCodec;
import com.github.cocosoys.mc.soyshttpovermc.orm.meta.FieldMeta;
import com.github.cocosoys.mc.soyshttpovermc.orm.meta.PojoMeta;
import com.github.cocosoys.mc.soyshttpovermc.orm.query.ConditionTree;
import com.github.cocosoys.mc.soyshttpovermc.orm.query.Page;
import com.github.cocosoys.mc.soyshttpovermc.spi.ConfigSection;
import com.github.cocosoys.mc.soyshttpovermc.spi.Platforms;
import lombok.CustomLog;

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
 *     create_time: "2026-09-18 12:00:00"   ← 列名驼峰转下划线；Date 存 yyyy-MM-dd HH:mm:ss 字符串
 * </pre>
 * <ul>
 *   <li>读：全量加载 ConfigSection（内存缓存文件视图），按 {@link ConditionTree} 逐条求值（O(n)）；</li>
 *   <li>写：对象锁 + 临时文件 rename 原子替换（原子写）。</li>
 * </ul>
 */
@CustomLog
public class YamlBackendExecutor implements IBackendExecutor {

    private static final Map<File, YamlBackendExecutor> INSTANCES = new ConcurrentHashMap<>();

    private final File dataDir;
    private final Object lock = new Object();
    /**
     * 自增主键表重编号检查（每表每进程一次）。
     */
    private final java.util.Set<String> autoIdKeysChecked = ConcurrentHashMap.newKeySet();
    /**
     * 表名 → 文件视图（惰性加载，写时更新）。
     */
    private final Map<String, ConfigSection> cache = new ConcurrentHashMap<>();

    public YamlBackendExecutor(File dataDir) {
        this.dataDir = dataDir == null ? new File("data") : dataDir;
    }

    /**
     * 获取共享实例（按 dataDir 缓存，插件装配时 init 一次）。
     */
    public static YamlBackendExecutor get(File dataDir) {
        return INSTANCES.computeIfAbsent(dataDir == null ? new File("data") : dataDir,
                YamlBackendExecutor::new);
    }

    @Override
    public String name() {
        return StorageType.YAML.getId();
    }

    // ===== 文件视图 =====

    /**
     * 数据目录。
     */
    public File getDataDir() {
        return dataDir;
    }

    /**
     * 实体对应的数据文件（data/&lt;表名&gt;.yml）。
     */
    public File fileOf(Class<?> beanClass) {
        return new File(dataDir, PojoMeta.of(beanClass).getTableName() + ".yml");
    }

    /**
     * 获取实体对应文件的 ConfigSection 视图（{@code YAML.Pojo.get(User.class)} 的返回对象）。
     * 返回的是 ORM 共享的缓存视图：ORM 写路径与外部写共用同一实例与锁，
     * 外部修改后需调用 {@link #save(Class)} 显式落盘。
     */
    public ConfigSection getConfig(Class<?> beanClass) {
        String table = PojoMeta.of(beanClass).getTableName();
        ConfigSection config = cache.get(table);
        if (config != null) {
            return config;
        }
        synchronized (lock) {
            config = cache.get(table);
            if (config == null) {
                File file = fileOf(beanClass);
                if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
                    log.warnT("log.orm.mkdir-failed", "[ORM] 无法创建数据目录: {0}", file.getParentFile());
                }
                config = file.isFile() ? (Platforms.getOrNull() == null ? null : Platforms.getOrNull().loadYaml(file)) : Platforms.getOrNull().createYaml();
                if (!config.isSection(table)) {
                    config.createSection(table);
                }
                cache.put(table, config);
            }
            return config;
        }
    }

    /**
     * 显式落盘（getConfig 视图被外部修改后调用）。
     */
    public void save(Class<?> beanClass) {
        synchronized (lock) {
            String table = PojoMeta.of(beanClass).getTableName();
            ConfigSection config = cache.get(table);
            if (config == null) return;
            flush(config, fileOf(beanClass));
        }
    }

    /**
     * 原子写：写临时文件后 rename 替换。
     */
    private void flush(ConfigSection config, File target) {
        try {
            if (!target.getParentFile().exists() && !target.getParentFile().mkdirs()) {
                log.warnT("log.orm.mkdir-failed", "[ORM] 无法创建数据目录: {0}", target.getParentFile());
                return;
            }
            File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
            Platforms.getOrNull().saveYaml(config, tmp);
            Files.move(tmp.toPath(), target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException e) {
            log.warnT("log.orm.yaml-flush-failed", "[ORM] YAML 落盘失败: {0}", e.getMessage());
        }
    }

    // ===== 读 =====

    @Override
    public <T> T getById(Class<T> beanClass, Object id) {
        if (id == null) return null;
        PojoMeta meta = PojoMeta.of(beanClass);
        if (!meta.hasId()) {
            throw new IllegalArgumentException(I18n.t("exception.orm.no-tableid-annotation", "实体 {0} 未标注 @TableId", beanClass.getSimpleName()));
        }
        ConfigSection root = getConfig(beanClass).getSection(meta.getTableName());
        ConfigSection section = root == null ? null : root.getSection(encodeId(String.valueOf(id)));
        return BeanCodec.deserialize(beanClass, section);
    }

    @Override
    public <T> List<T> selectByTree(Class<T> beanClass, ConditionTree tree) {
        PojoMeta meta = PojoMeta.of(beanClass);
        ConfigSection root = getConfig(beanClass).getSection(meta.getTableName());
        List<T> out = new ArrayList<>();
        if (root == null) return out;
        for (String key : root.getKeys(false)) {
            ConfigSection section = root.getSection(key);
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
                return v instanceof java.util.Date ? BeanCodec.formatDate((java.util.Date) v) : v;
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

    /**
     * 自增主键旧数据自愈（与 SQL 端 ensureAutoIdTable 对称）：老版本合成键（非纯数字键）
     * 逐条重编号为 1..n，保证 AUTO 主键下 YAML 键与 SQL id 同为数字。每表仅执行一次。
     */
    private void ensureAutoIdKeys(Class<?> beanClass, PojoMeta meta) {
        FieldMeta idFm = meta.getIdField();
        if (idFm == null || !idFm.isAutoId()) return;
        String table = meta.getTableName();
        if (!autoIdKeysChecked.add(table)) return;
        ConfigSection config = getConfig(beanClass);
        // 只遍历表名 section 下的记录键；根级只有表名一个键，绝不能把表名当“旧合成键”重编号
        ConfigSection root = config.getSection(table);
        if (root == null) return;
        java.util.List<String> legacy = new java.util.ArrayList<>();
        long max = 0;
        for (String key : root.getKeys(false)) {
            try {
                long v = Long.parseLong(key);
                if (v > max) max = v;
            } catch (NumberFormatException e) {
                legacy.add(key);
            }
        }
        if (legacy.isEmpty()) return;
        synchronized (lock) {
            for (String oldKey : legacy) {
                ConfigSection src = root.getSection(oldKey);
                if (src == null) continue;
                max++;
                ConfigSection dst = root.createSection(Long.toString(max));
                for (String k : src.getKeys(false)) {
                    if (src.isSection(k)) {
                        copySection(src.getSection(k), dst.createSection(k));
                    } else {
                        dst.set(k, src.get(k));
                    }
                }
                root.set(oldKey, null);
            }
            flush(config, fileOf(beanClass));
        }
        log.infoT("log.orm.auto-id-keys-renumbered",
                "[ORM] 自增主键旧数据已重编号（{0} 条）: {1}", legacy.size(), table);
    }

    private static void copySection(ConfigSection src, ConfigSection dst) {
        if (src == null || dst == null) return;
        for (String key : src.getKeys(false)) {
            if (src.isSection(key)) {
                copySection(src.getSection(key), dst.createSection(key));
            } else {
                dst.set(key, src.get(key));
            }
        }
    }

    @Override
    public <T> boolean insert(Class<T> beanClass, Object bean) {
        if (bean == null) return false;
        AuditFields.fill(bean);
        PojoMeta meta = PojoMeta.of(beanClass);
        FieldMeta idFm = meta.getIdField();
        Object id = idOf(meta, bean);
        ensureAutoIdKeys(beanClass, meta);
        synchronized (lock) {
            ConfigSection config = getConfig(beanClass);
            if (id == null && idFm != null && idFm.isAutoId()) {
                // 自增主键：取表名 section 下当前最大数值键 + 1（旧合成键字符串忽略），并回填实体
                long max = 0;
                ConfigSection root = config.getSection(meta.getTableName());
                if (root != null) {
                    for (String key : root.getKeys(false)) {
                        try {
                            long v = Long.parseLong(key);
                            if (v > max) max = v;
                        } catch (NumberFormatException ignored) {
                            // 旧合成键字符串：忽略
                        }
                    }
                }
                id = max + 1;
                try {
                    idFm.field.setAccessible(true);
                    idFm.field.set(bean, id);
                } catch (IllegalAccessException ignored) {
                }
            }
            if (id == null) {
                throw new IllegalArgumentException(I18n.t("exception.orm.tableid-not-assigned", "实体 {0} 主键(@TableId)未赋值，无法插入", beanClass.getSimpleName()));
            }
            String base = meta.getTableName() + "." + encodeId(id);
            config.set(base, null);
            ConfigSection section = config.createSection(base);
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
            ConfigSection config = getConfig(beanClass);
            String path = meta.getTableName() + "." + encodeId(id);
            if (config.get(path) == null) return false;
            config.set(path, null);
            flush(config, fileOf(beanClass));
        }
        return true;
    }

    /**
     * 清空整表（表名 section 置空并落盘；覆盖迁移"先清后写"的"清"）。
     */
    public void clear(Class<?> beanClass) {
        PojoMeta meta = PojoMeta.of(beanClass);
        synchronized (lock) {
            ConfigSection config = getConfig(beanClass);
            config.set(meta.getTableName(), null);
            flush(config, fileOf(beanClass));
        }
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

    /**
     * YAML 点路径编码：Bukkit YamlConfiguration 以 {@code .} 作为路径分隔符，
     * 主键 id 中含点（如权限节点 {@code USER|uuid|system.user.query}）会被误拆成嵌套路径，
     * 导致写入后无法读回。统一将 {@code .} 替换为 {@code ·}（U+00B7），读写删除三处保持一致。
     */
    private static String encodeId(Object id) {
        return String.valueOf(id).replace(".", "·");
    }

    // ===== 跨端搜索（实现：全量扫描 contains） =====

    @Override
    public <T> List<T> search(Class<T> beanClass, String keyword, String... fields) {
        if (keyword == null || keyword.isEmpty()) return new ArrayList<>();
        PojoMeta meta = PojoMeta.of(beanClass);
        List<String> cols = searchColumns(meta, fields);
        if (cols.isEmpty()) return new ArrayList<>();
        String kw = keyword.toLowerCase();
        ConfigSection root = getConfig(beanClass).getSection(meta.getTableName());
        List<T> out = new ArrayList<>();
        if (root == null) return out;
        for (String key : root.getKeys(false)) {
            ConfigSection section = root.getSection(key);
            if (section == null) continue;
            T bean = BeanCodec.deserialize(beanClass, section);
            if (bean == null) continue;
            for (String col : cols) {
                Object v = QueryValueAccessor(bean, meta).apply(col);
                if (v != null && String.valueOf(v).toLowerCase().contains(kw)) {
                    out.add(bean);
                    break;
                }
            }
        }
        return out;
    }

    @Override
    public <T> Page<T> searchPage(Class<T> beanClass, long current, long size, String keyword, String... fields) {
        Page<T> page = new Page<>(current, size);
        if (keyword == null || keyword.isEmpty()) return page;
        List<T> all = search(beanClass, keyword, fields);
        page.setTotal(all.size());
        long offset = page.offset();
        int from = (int) Math.min(offset, all.size());
        int to = (int) Math.min(offset + page.getSize(), all.size());
        page.setRecords((List) new ArrayList<>(all.subList(from, to)));
        return page;
    }

    /**
     * 解析搜索目标列：未指定 fields 时默认 String/Enum 列；指定时按字段名解析（忽略不存在的字段）。
     */
    private static List<String> searchColumns(PojoMeta meta, String... fields) {
        List<String> cols = new ArrayList<>();
        if (fields == null || fields.length == 0) {
            for (FieldMeta fm : meta.getFields()) {
                if (!fm.isIgnored() && (fm.type == String.class || fm.type.isEnum())) {
                    cols.add(fm.columnName);
                }
            }
        } else {
            for (String f : fields) {
                FieldMeta fm = meta.byField(f);
                if (fm != null) cols.add(fm.columnName);
            }
        }
        return cols;
    }
}
