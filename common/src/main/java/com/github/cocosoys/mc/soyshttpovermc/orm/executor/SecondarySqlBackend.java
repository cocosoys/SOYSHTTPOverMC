package com.github.cocosoys.mc.soyshttpovermc.orm.executor;

import com.github.cocosoys.mc.soyshttpovermc.enums.StorageType;
import com.github.cocosoys.mc.soyshttpovermc.orm.convertor.AuditFields;
import com.github.cocosoys.mc.soyshttpovermc.orm.convertor.BeanCodec;
import com.github.cocosoys.mc.soyshttpovermc.orm.meta.FieldMeta;
import com.github.cocosoys.mc.soyshttpovermc.orm.meta.PojoMeta;
import com.github.cocosoys.mc.soyshttpovermc.orm.query.ConditionTree;
import com.github.cocosoys.mc.soyshttpovermc.orm.query.Page;
import com.github.cocosoys.mc.soyshttpovermc.spi.ConfigSection;
import com.github.cocosoys.mc.soyshttpovermc.spi.Platform;
import com.github.cocosoys.mc.soyshttpovermc.spi.Platforms;
import com.zaxxer.hikari.HikariDataSource;
import lombok.CustomLog;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 非主 SQL 后端访问器（独立 JDBC 直连，绕过 dlz 全局单数据源）。
 *
 * <p>当 {@code storage.backends.mysql} 与 {@code storage.backends.sqlite} 同时启用时，
 * dlz-db-core 只能装配一个 SQL 后端（主 SQL，priority 最高者）；其余 SQL 后端
 * （非主）经本类以独立 Hikari 数据源直连，实现 {@link IBackendExecutor} 全量单条读写
 * （“指定类型读写”门面的非主 SQL 路由），并提供迁移所需的</p>
 * <ul>
 *   <li>建表（复用 {@link SqlBackendExecutor#buildCreateTableDdl}，DDL 口径一致）；</li>
 *   <li>全量查询 / 条件查询 / 单条读写（SELECT / REPLACE INTO / DELETE）；</li>
 *   <li>清空（DELETE FROM）；</li>
 *   <li>事务批量写入（REPLACE INTO，同一事务失败回滚）。</li>
 * </ul>
 */
@CustomLog
public final class SecondarySqlBackend implements IBackendExecutor {

    private static final Map<StorageType, SecondarySqlBackend> INSTANCES = new ConcurrentHashMap<>();

    private final HikariDataSource ds;
    private final boolean sqlite;

    private SecondarySqlBackend(HikariDataSource ds, boolean sqlite) {
        this.ds = ds;
        this.sqlite = sqlite;
    }

    /**
     * 获取指定辅助 SQL 后端的直连访问器（按 config 段构建独立数据源；未启用返回 null）。
     */
    public static SecondarySqlBackend of(StorageType type) {
        if (type != StorageType.MYSQL && type != StorageType.SQLITE) {
            return null;
        }
        return INSTANCES.computeIfAbsent(type, SecondarySqlBackend::build);
    }

    private static SecondarySqlBackend build(StorageType type) {
        Platform platform = Platforms.getOrNull();
        if (platform == null) {
            return null;
        }
        ConfigSection sec = platform.getConfig().getSection("storage.backends." + type.getId());
        if (sec == null || !sec.getBoolean("enabled", false)) {
            return null;
        }
        try {
            if (type == StorageType.MYSQL) {
                HikariDataSource mysql = SqlBackendExecutor.buildHikari(
                        sec.getString("url", ""), sec.getString("username", "root"),
                        sec.getString("password", ""), SqlBackendExecutor.detectMysqlDriver());
                return new SecondarySqlBackend(mysql, false);
            }
            String file = new java.io.File(platform.getDataFolder(),
                    sec.getString("file", "data/records.db")).getAbsolutePath();
            HikariDataSource sqlite = SqlBackendExecutor.buildHikari(
                    "jdbc:sqlite:" + file, null, null, "org.sqlite.JDBC");
            return new SecondarySqlBackend(sqlite, true);
        } catch (Throwable t) {
            log.warnT("log.orm.secondary-sql-init-failed",
                    "[ORM] 辅助 SQL 后端初始化失败 {0}: {1}", type.getId(), t.getMessage());
            return null;
        }
    }

    /** 数据源 JDBC 前缀（"mysql" / "sqlite"）。 */
    public String name() {
        return sqlite ? StorageType.SQLITE.getId() : StorageType.MYSQL.getId();
    }

    /** 建表（CREATE TABLE IF NOT EXISTS + 容忍式补列，与主 SQL 后端口径一致）。 */
    public void ensureTable(Class<?> beanClass) {
        PojoMeta meta = PojoMeta.of(beanClass);
        try (Connection conn = ds.getConnection(); Statement st = conn.createStatement()) {
            st.executeUpdate(SqlBackendExecutor.buildCreateTableDdl(meta, sqlite));
            for (FieldMeta fm : meta.getFields()) {
                if (fm.isIgnored() || fm.isPrimaryKey()) {
                    continue;
                }
                try {
                    st.executeUpdate("ALTER TABLE `" + meta.getTableName() + "` ADD COLUMN `"
                            + fm.columnName + "` " + SqlBackendExecutor.sqlType(fm.type));
                } catch (SQLException ignored) {
                    // 列已存在 = 预期
                }
            }
        } catch (SQLException t) {
            log.warnT("log.orm.secondary-create-table-failed",
                    "[ORM] 辅助 SQL 建表失败 {0}: {1}", meta.getTableName(), t.getMessage());
        }
    }

    /** 目标表当前行数（覆盖前打印用）。 */
    public long count(Class<?> beanClass) {
        String table = PojoMeta.of(beanClass).getTableName();
        try (Connection conn = ds.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM `" + table + "`")) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException t) {
            return -1; // 表不存在/不可读
        }
    }

    /** 全量读取（SELECT * → 实体列表）。 */
    public <T> List<T> selectAll(Class<T> beanClass) {
        PojoMeta meta = PojoMeta.of(beanClass);
        List<T> out = new ArrayList<>();
        try (Connection conn = ds.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM `" + meta.getTableName() + "`")) {
            while (rs.next()) {
                T bean = rowToBean(beanClass, rs);
                if (bean != null) {
                    out.add(bean);
                }
            }
            return out;
        } catch (SQLException t) {
            log.warnT("log.orm.secondary-select-failed",
                    "[ORM] 辅助 SQL 全量读取失败 {0}: {1}", meta.getTableName(), t.getMessage());
            return out;
        }
    }

    private <T> T rowToBean(Class<T> beanClass, ResultSet rs) {
        PojoMeta meta = PojoMeta.of(beanClass);
        try {
            T bean = beanClass.getDeclaredConstructor().newInstance();
            for (FieldMeta fm : meta.getFields()) {
                if (fm.isIgnored()) {
                    continue;
                }
                Object raw = rs.getObject(fm.columnName);
                if (raw == null) {
                    continue;
                }
                try {
                    fm.field.setAccessible(true);
                    fm.field.set(bean, SqlBackendExecutor.convertValue(raw, fm.type));
                } catch (IllegalAccessException ignored) {
                }
            }
            return bean;
        } catch (Exception t) {
            return null;
        }
    }

    // ===== IBackendExecutor 单条读写（“指定类型读写”门面的非主 SQL 路由） =====

    @Override
    public <T> T getById(Class<T> beanClass, Object id) {
        if (id == null) return null;
        PojoMeta meta = PojoMeta.of(beanClass);
        if (!meta.hasId()) return null;
        String table = meta.getTableName();
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM `" + table + "` WHERE `" + meta.getIdField().columnName + "` = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rowToBean(beanClass, rs) : null;
            }
        } catch (SQLException t) {
            log.warnT("log.orm.secondary-get-failed",
                    "[ORM] 非主 SQL getById 失败 {0}: {1}", table, t.getMessage());
            return null;
        }
    }

    @Override
    public <T> List<T> selectByTree(Class<T> beanClass, ConditionTree tree) {
        List<T> out = selectAll(beanClass);
        if (tree == null) {
            return out;
        }
        PojoMeta meta = PojoMeta.of(beanClass);
        List<T> filtered = new ArrayList<>();
        for (T bean : out) {
            if (tree.matches(valueAccessor(bean, meta))) {
                filtered.add(bean);
            }
        }
        applyOrders(filtered, meta, tree);
        Page<?> page = tree.getPage();
        if (page != null) {
            long offset = page.offset();
            int from = (int) Math.min(offset, filtered.size());
            int to = (int) Math.min(offset + page.getSize(), filtered.size());
            List<T> sliced = new ArrayList<>(filtered.subList(from, to));
            page.setTotal(filtered.size());
            page.setRecords((List) sliced);
            return sliced;
        }
        return filtered;
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

    @Override
    public <T> boolean insert(Class<T> beanClass, Object bean) {
        return replaceSingle(beanClass, bean);
    }

    @Override
    public <T> boolean updateById(Class<T> beanClass, Object bean) {
        return replaceSingle(beanClass, bean);
    }

    /** 单条 REPLACE INTO（= upsert，与主 SQL / YAML 端语义一致）。 */
    private <T> boolean replaceSingle(Class<T> beanClass, Object bean) {
        if (bean == null) return false;
        AuditFields.fill(bean);
        PojoMeta meta = PojoMeta.of(beanClass);
        if (!meta.hasId()) return false;
        StringBuilder cols = new StringBuilder();
        StringBuilder marks = new StringBuilder();
        List<Object> vals = new ArrayList<>();
        for (FieldMeta fm : meta.getFields()) {
            if (fm.isIgnored()) {
                continue;
            }
            if (cols.length() > 0) {
                cols.append(", ");
                marks.append(", ");
            }
            cols.append('`').append(fm.columnName).append('`');
            marks.append('?');
            try {
                fm.field.setAccessible(true);
                Object v = fm.field.get(bean);
                vals.add(SqlBackendExecutor.encodeValue(v));
            } catch (IllegalAccessException ignored) {
                vals.add(null);
            }
        }
        String sql = "REPLACE INTO `" + meta.getTableName() + "` (" + cols + ") VALUES (" + marks + ")";
        try (Connection conn = ds.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < vals.size(); i++) {
                ps.setObject(i + 1, vals.get(i));
            }
            ps.executeUpdate();
            return true;
        } catch (SQLException t) {
            log.warnT("log.orm.secondary-single-write-failed",
                    "[ORM] 非主 SQL 单条写入失败 {0}: {1}", meta.getTableName(), t.getMessage());
            return false;
        }
    }

    @Override
    public <T> boolean deleteById(Class<T> beanClass, Object id) {
        if (id == null) return false;
        PojoMeta meta = PojoMeta.of(beanClass);
        if (!meta.hasId()) return false;
        String table = meta.getTableName();
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM `" + table + "` WHERE `" + meta.getIdField().columnName + "` = ?")) {
            ps.setObject(1, id);
            ps.executeUpdate();
            return true;
        } catch (SQLException t) {
            log.warnT("log.orm.secondary-delete-failed",
                    "[ORM] 非主 SQL deleteById 失败 {0}: {1}", table, t.getMessage());
            return false;
        }
    }

    // ===== 跨端搜索（实现：selectAll 全量内存 contains，与 YAML 语义一致） =====

    @Override
    public <T> List<T> search(Class<T> beanClass, String keyword, String... fields) {
        if (keyword == null || keyword.isEmpty()) return new ArrayList<>();
        PojoMeta meta = PojoMeta.of(beanClass);
        List<String> cols = searchColumns(meta, fields);
        if (cols.isEmpty()) return new ArrayList<>();
        String kw = keyword.toLowerCase();
        List<T> out = new ArrayList<>();
        for (T bean : selectAll(beanClass)) {
            for (String col : cols) {
                Object v = valueAccessor(bean, meta).apply(col);
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
     * 与主 SQL / YAML 端口径一致。
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

    /** 条件求值取值器（列名 → 属性值，Date 归一为日期字符串，与 YAML 端一致）。 */
    private static Function<String, Object> valueAccessor(Object bean, PojoMeta meta) {
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
        if (tree.getOrders().isEmpty()) return;
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

    /** 清空表（DELETE FROM；先清后写覆盖语义的"清"）。 */
    public void clear(Class<?> beanClass) {
        String table = PojoMeta.of(beanClass).getTableName();
        try (Connection conn = ds.getConnection(); Statement st = conn.createStatement()) {
            st.executeUpdate("DELETE FROM `" + table + "`");
        } catch (SQLException t) {
            log.warnT("log.orm.secondary-clear-failed",
                    "[ORM] 辅助 SQL 清空失败 {0}: {1}", table, t.getMessage());
        }
    }

    /**
     * 覆盖写入（先清后写，同一事务失败回滚）：目标表清空后逐条 REPLACE INTO。
     *
     * @return 实际写入条数
     */
    public int upsertAll(Class<?> beanClass, List<?> beans) {
        if (beans == null || beans.isEmpty()) {
            return 0;
        }
        PojoMeta meta = PojoMeta.of(beanClass);
        StringBuilder cols = new StringBuilder();
        StringBuilder marks = new StringBuilder();
        for (FieldMeta fm : meta.getFields()) {
            if (fm.isIgnored()) {
                continue;
            }
            if (cols.length() > 0) {
                cols.append(", ");
                marks.append(", ");
            }
            cols.append('`').append(fm.columnName).append('`');
            marks.append('?');
        }
        String sql = "REPLACE INTO `" + meta.getTableName() + "` (" + cols + ") VALUES (" + marks + ")";
        int written = 0;
        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (Statement st = conn.createStatement()) {
                    st.executeUpdate("DELETE FROM `" + meta.getTableName() + "`");
                }
                for (Object bean : beans) {
                    Object[] vals = SqlBackendExecutor.beanToValues(meta, bean, true);
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        for (int i = 0; i < vals.length; i++) {
                            ps.setObject(i + 1, vals[i]);
                        }
                        ps.executeUpdate();
                    }
                    written++;
                }
                conn.commit();
            } catch (SQLException t) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {
                }
                log.warnT("log.orm.secondary-upsert-failed",
                        "[ORM] 辅助 SQL 覆盖写入失败 {0}（已回滚）: {1}", meta.getTableName(), t.getMessage());
                throw t;
            }
        } catch (SQLException t) {
            return 0;
        }
        return written;
    }

    /** 关闭数据源（reload 时释放）。 */
    public void close() {
        try {
            if (ds != null && !ds.isClosed()) {
                ds.close();
            }
        } catch (Throwable ignored) {
        }
        INSTANCES.entrySet().removeIf(e -> e.getValue() == this);
    }

    /**
     * 合并写入（按 key upsert，不清空目标表）：逐条 REPLACE INTO，同一事务失败回滚。
     * 用于 /soyshttp migrate 的 SQL 目标端（合并语义，与覆盖语义 {@link #upsertAll} 相对）。
     *
     * @return 实际写入条数
     */
    public int mergeAll(Class<?> beanClass, List<?> beans) {
        if (beans == null || beans.isEmpty()) {
            return 0;
        }
        PojoMeta meta = PojoMeta.of(beanClass);
        StringBuilder cols = new StringBuilder();
        StringBuilder marks = new StringBuilder();
        for (FieldMeta fm : meta.getFields()) {
            if (fm.isIgnored()) {
                continue;
            }
            if (cols.length() > 0) {
                cols.append(", ");
                marks.append(", ");
            }
            cols.append('`').append(fm.columnName).append('`');
            marks.append('?');
        }
        String sql = "REPLACE INTO `" + meta.getTableName() + "` (" + cols + ") VALUES (" + marks + ")";
        int written = 0;
        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);
            try {
                for (Object bean : beans) {
                    Object[] vals = SqlBackendExecutor.beanToValues(meta, bean, true);
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        for (int i = 0; i < vals.length; i++) {
                            ps.setObject(i + 1, vals[i]);
                        }
                        ps.executeUpdate();
                    }
                    written++;
                }
                conn.commit();
            } catch (SQLException t) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {
                }
                log.warnT("log.orm.secondary-merge-failed",
                        "[ORM] 辅助 SQL 合并写入失败 {0}（已回滚）: {1}", meta.getTableName(), t.getMessage());
                throw t;
            }
        } catch (SQLException t) {
            return 0;
        }
        return written;
    }
}
