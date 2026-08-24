package soys.soyshttpovermc.orm.executor;
import lombok.CustomLog;

import soys.soyshttpovermc.i18n.I18n;
import soys.soyshttpovermc.log.LogKit;
import soys.soyshttpovermc.orm.meta.FieldMeta;
import soys.soyshttpovermc.orm.meta.PojoMeta;
import soys.soyshttpovermc.orm.query.ConditionTree;
import soys.soyshttpovermc.orm.query.Op;
import soys.soyshttpovermc.orm.query.Page;

import com.dlz.db.convertor.columnname.ColumnNameLower;
import com.dlz.db.core.DlzDbProperties;
import com.dlz.db.core.ISqlExecutor;
import com.dlz.db.core.jdbc.JdbcSqlExecutor;
import com.dlz.db.core.jdbc.JdbcTxExecutor;
import com.dlz.db.modal.DB;
import com.dlz.db.modal.dto.ResultMap;
import com.dlz.db.support.DBHolder;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import soys.soyshttpovermc.enums.StorageType;

import java.lang.reflect.Field;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

/**
 * SQL 后端执行器（二期实现）：基于 dlz-db-core 链路（HikariCP 数据源 + ISqlExecutor），
 * 由本类自拼参数化 SQL（条件树 → WHERE/ORDER BY/LIMIT），DDL 按 PojoMeta 自动生成。
 *
 * <p>数据源：config.yml {@code storage.backends.{mysql,sqlite}}（优先级 mysql &gt; sqlite，
 * 与多后端主辅一致）。列名使用 {@link ColumnNameLower}（小写下划线）→ 与 YAML 端键名一致。</p>
 */
@CustomLog
public class SqlBackendExecutor implements IBackendExecutor {

    private static volatile SqlBackendExecutor instance;
    private volatile boolean available = false;
    private String dbName = "sql";

    private SqlBackendExecutor() {
    }

    // ===== 初始化 =====

    /** 从 config storage.backends 装配（mysql 优先，其次 sqlite）；失败返回 null。 */
    public static SqlBackendExecutor init(JavaPlugin plugin) {
        try {
            HikariDataSource ds = buildDataSource(plugin);
            if (ds == null) {
                log.warnT("log.orm.sql-not-enabled",
                    "[ORM] 未启用 SQL 后端（storage.backends.mysql/sqlite），SQL.Pojo 不可用");
                return null;
            }
            return initDirect(ds, ds.getJdbcUrl().startsWith("jdbc:sqlite:")
                    ? StorageType.SQLITE.getId() : StorageType.MYSQL.getId());
        } catch (Throwable t) {
            log.warnT("log.orm.sql-init-failed", "[ORM] SQL 后端初始化失败: {0}", t);
            return null;
        }
    }

    /** 直接装配（本地测试 / 二次接入用）：Hikari 数据源 + dlz DBHolder 初始化 + 列名统一。 */
    public static SqlBackendExecutor initDirect(HikariDataSource ds, String name) {
        try {
            DB.Dynamic.setDefaultDataSource(ds);
            DBHolder.init(new DlzDbProperties(), () -> ds, JdbcSqlExecutor::new, JdbcTxExecutor::new);
            // 列名统一：小写下划线（与 YAML 端 PojoMeta 一致）
            com.dlz.db.util.DbConvertUtil.defaultColumnMapper = new ColumnNameLower();
            SqlBackendExecutor e = new SqlBackendExecutor();
            e.available = true;
            e.dbName = name == null ? "sql" : name;
            instance = e;
            log.infoT("log.orm.sql-assembled",
                    "[ORM] SQL 后端已装配: {0}（dlz-db-core + HikariCP）", e.dbName);
            return e;
        } catch (Throwable t) {
            log.warnT("log.orm.sql-init-failed", "[ORM] SQL 后端初始化失败: {0}", t);
            return null;
        }
    }

    private static HikariDataSource buildDataSource(JavaPlugin plugin) {
        ConfigurationSection mysql = plugin.getConfig().getConfigurationSection("storage.backends.mysql");
        if (mysql != null && mysql.getBoolean("enabled", false)) {
            return buildHikari(mysql.getString("url", ""), mysql.getString("username", "root"),
                    mysql.getString("password", ""), "com.mysql.cj.jdbc.Driver");
        }
        ConfigurationSection sqlite = plugin.getConfig().getConfigurationSection("storage.backends.sqlite");
        if (sqlite != null && sqlite.getBoolean("enabled", false)) {
            String file = new java.io.File(plugin.getDataFolder(),
                    sqlite.getString("file", "data/records.db")).getAbsolutePath();
            return buildHikari("jdbc:sqlite:" + file, null, null, "org.sqlite.JDBC");
        }
        return null;
    }

    private static HikariDataSource buildHikari(String url, String user, String pass, String driver) {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(url);
        if (user != null) cfg.setUsername(user);
        if (pass != null) cfg.setPassword(pass);
        cfg.setDriverClassName(driver);
        cfg.setMaximumPoolSize(4);
        cfg.setMinimumIdle(1);
        cfg.setConnectionTimeout(5000);
        return new HikariDataSource(cfg);
    }

    /** 当前实例（未装配时 null）。 */
    public static SqlBackendExecutor get() {
        return instance;
    }

    @Override
    public String name() {
        return dbName;
    }

    // ===== 内部 =====

    private ISqlExecutor ex() {
        return DBHolder.getSqlExecutor();
    }

    private String table(Class<?> beanClass) {
        return PojoMeta.of(beanClass).getTableName();
    }

    /** 执行器可用性（数据源未装配/失败时 false）。 */
    public boolean isAvailable() {
        return available;
    }

    // ===== DDL 自动生成 =====

    /** 建表（CREATE TABLE IF NOT EXISTS）+ 缺列补列（容忍式 ALTER）。 */
    public void ensureTable(Class<?> beanClass) {
        PojoMeta meta = PojoMeta.of(beanClass);
        StringBuilder ddl = new StringBuilder("CREATE TABLE IF NOT EXISTS `").append(meta.getTableName()).append("` (");
        StringBuilder cols = new StringBuilder();
        for (FieldMeta fm : meta.getFields()) {
            if (fm.isIgnored()) continue;
            if (cols.length() > 0) cols.append(", ");
            // 主键 String 列用 VARCHAR(64)：utf8mb4 下 VARCHAR(255) 主键索引超长（1020B>1000B）
            String type = fm.isPrimaryKey() && fm.type == String.class ? "VARCHAR(64)" : sqlType(fm.type);
            cols.append('`').append(fm.columnName).append("` ").append(type);
            if (fm.isPrimaryKey()) cols.append(" PRIMARY KEY");
        }
        ddl.append(cols).append(")");
        try {
            ex().update(ddl.toString());
        } catch (Throwable t) {
            log.warnT("log.orm.create-table-failed",
                    "[ORM] 建表失败 {0}: {1}", meta.getTableName(), t.getMessage());
            return;
        }
        // 缺列容忍式补列（旧表升级）
        for (FieldMeta fm : meta.getFields()) {
            if (fm.isIgnored() || fm.isPrimaryKey()) continue;
            try {
                ex().update("ALTER TABLE `" + meta.getTableName() + "` ADD COLUMN `"
                        + fm.columnName + "` " + sqlType(fm.type));
            } catch (Throwable ignored) {
                // 列已存在 = 预期
            }
        }
    }

    private static String sqlType(Class<?> type) {
        if (type == String.class) return "VARCHAR(255)";
        if (type == Integer.class || type == int.class) return "INT";
        if (type == Long.class || type == long.class) return "BIGINT";
        if (type == Double.class || type == double.class || type == Float.class || type == float.class) return "DOUBLE";
        if (type == Boolean.class || type == boolean.class) return "TINYINT";
        if (type == java.util.Date.class || type == Date.class) return "BIGINT";
        if (type.isEnum()) return "VARCHAR(64)";
        return "TEXT"; // 嵌套（List/Map/对象）→ JSON
    }

    // ===== 条件树 → SQL =====

    /** 翻译 WHERE + 参数；无条件返回空串与空参。 */
    private SqlParts buildWhere(ConditionTree tree) {
        StringBuilder where = new StringBuilder();
        List<Object> args = new ArrayList<>();
        if (tree != null) {
            for (ConditionTree.Cond c : tree.getConditions()) {
                if (where.length() > 0) {
                    where.append(c.and ? " AND " : " OR ");
                }
                where.append('`').append(c.column).append('`');
                switch (c.op) {
                    case EQ: where.append(" = ?"); args.add(c.value); break;
                    case NE: where.append(" <> ?"); args.add(c.value); break;
                    case GT: where.append(" > ?"); args.add(c.value); break;
                    case GE: where.append(" >= ?"); args.add(c.value); break;
                    case LT: where.append(" < ?"); args.add(c.value); break;
                    case LE: where.append(" <= ?"); args.add(c.value); break;
                    case LIKE: where.append(" LIKE ?"); args.add("%" + String.valueOf(c.value).replace("%", "") + "%"); break;
                    case IN: case NOT_IN: {
                        where.append(c.op == Op.IN ? " IN (" : " NOT IN (");
                        List<?> vals = c.value instanceof List ? (List<?>) c.value : java.util.Collections.singletonList(c.value);
                        for (int i = 0; i < vals.size(); i++) {
                            if (i > 0) where.append(",");
                            where.append("?");
                            args.add(vals.get(i));
                        }
                        where.append(')');
                        break;
                    }
                    case IS_NULL: where.append(" IS NULL"); break;
                    case NOT_NULL: where.append(" IS NOT NULL"); break;
                }
            }
        }
        return new SqlParts(where.toString(), args.toArray());
    }

    private String buildOrder(ConditionTree tree) {
        if (tree == null || tree.getOrders().isEmpty()) return "";
        StringBuilder sb = new StringBuilder(" ORDER BY ");
        boolean first = true;
        for (ConditionTree.OrderBy o : tree.getOrders()) {
            if (!first) sb.append(", ");
            sb.append('`').append(o.column).append('`').append(o.direction == ConditionTree.Order.DESC ? " DESC" : " ASC");
            first = false;
        }
        return sb.toString();
    }

    private static final class SqlParts {
        final String where;
        final Object[] args;

        SqlParts(String where, Object[] args) {
            this.where = where;
            this.args = args;
        }
    }

    // ===== 行 → Bean =====

    private <T> T rowToBean(Class<T> beanClass, ResultMap row) {
        PojoMeta meta = PojoMeta.of(beanClass);
        T bean;
        try {
            bean = beanClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            return null;
        }
        for (FieldMeta fm : meta.getFields()) {
            if (fm.isIgnored()) continue;
            // dlz ResultMap 键可能为小写下划线列名 / 驼峰字段名（取决于 rowMapper），兼容三种取值
            Object raw = row.get(fm.columnName);
            if (raw == null && !fm.columnName.equals(fm.fieldName)) {
                raw = row.get(fm.fieldName);
            }
            if (raw == null) {
                raw = row.get(com.dlz.db.util.DbConvertUtil.toFieldName(fm.columnName));
            }
            if (raw == null) continue;
            try {
                fm.field.setAccessible(true);
                fm.field.set(bean, convertValue(raw, fm.type));
            } catch (IllegalAccessException ignored) {
            }
        }
        return bean;
    }

    private static Object convertValue(Object raw, Class<?> type) {
        if (type == String.class) return String.valueOf(raw);
        if (type == Integer.class || type == int.class) return raw instanceof Number ? ((Number) raw).intValue() : Integer.parseInt(String.valueOf(raw));
        if (type == Long.class || type == long.class) return raw instanceof Number ? ((Number) raw).longValue() : Long.parseLong(String.valueOf(raw));
        if (type == Double.class || type == double.class) return raw instanceof Number ? ((Number) raw).doubleValue() : Double.parseDouble(String.valueOf(raw));
        if (type == Boolean.class || type == boolean.class) return raw instanceof Boolean ? raw : raw instanceof Number ? ((Number) raw).intValue() != 0 : Boolean.parseBoolean(String.valueOf(raw));
        if (type == java.util.Date.class) return raw instanceof java.util.Date ? raw : new java.util.Date(raw instanceof Number ? ((Number) raw).longValue() : Long.parseLong(String.valueOf(raw)));
        if (type.isEnum()) return Enum.valueOf((Class<Enum>) type, String.valueOf(raw));
        return raw;
    }

    /** 实体 → 列值数组（写路径）。 */
    private Object[] beanToValues(PojoMeta meta, Object bean, boolean includeId) {
        List<Object> vals = new ArrayList<>();
        for (FieldMeta fm : meta.getFields()) {
            if (fm.isIgnored()) continue;
            if (!includeId && fm.isPrimaryKey()) continue;
            try {
                fm.field.setAccessible(true);
                Object v = fm.field.get(bean);
                vals.add(encodeValue(v));
            } catch (IllegalAccessException ignored) {
                vals.add(null);
            }
        }
        return vals.toArray();
    }

    private static Object encodeValue(Object v) {
        if (v instanceof java.util.Date) return ((java.util.Date) v).getTime();
        if (v instanceof Enum) return ((Enum<?>) v).name();
        return v;
    }

    // ===== 读 =====

    @Override
    public <T> T getById(Class<T> beanClass, Object id) {
        if (!available || id == null) return null;
        ensureTable(beanClass);
        PojoMeta meta = PojoMeta.of(beanClass);
        if (!meta.hasId()) return null;
        String sql = "SELECT * FROM `" + table(beanClass) + "` WHERE `" + meta.getIdField().columnName + "` = ?";
        try {
            List<ResultMap> list = ex().getList(sql, id);
            return list.isEmpty() ? null : rowToBean(beanClass, list.get(0));
        } catch (Throwable t) {
            log.warnT("log.orm.get-by-id-failed", "[ORM] getById 失败: {0}", t.getMessage());
            return null;
        }
    }

    @Override
    public <T> List<T> selectByTree(Class<T> beanClass, ConditionTree tree) {
        if (!available) return java.util.Collections.emptyList();
        ensureTable(beanClass);
        SqlParts parts = buildWhere(tree);
        String sql = "SELECT * FROM `" + table(beanClass) + "`"
                + (parts.where.isEmpty() ? "" : " WHERE " + parts.where)
                + buildOrder(tree);
        // 分页（tree.page 存在时）
        Page<?> page = tree == null ? null : tree.getPage();
        try {
            if (page != null) {
                long total = countByTree(beanClass, tree);
                page.setTotal(total);
                List<ResultMap> rows = ex().getList(sql + " LIMIT ? OFFSET ?",
                        concat(parts.args, page.getSize(), page.offset()));
                List<T> out = new ArrayList<>();
                for (ResultMap row : rows) out.add(rowToBean(beanClass, row));
                page.setRecords((List) out);
                return out;
            }
            List<ResultMap> rows = ex().getList(sql, parts.args);
            List<T> out = new ArrayList<>();
            for (ResultMap row : rows) out.add(rowToBean(beanClass, row));
            return out;
        } catch (Throwable t) {
            log.warnT("log.orm.select-by-tree-failed", "[ORM] selectByTree 失败: {0}", t.getMessage());
            return java.util.Collections.emptyList();
        }
    }

    private long countByTree(Class<?> beanClass, ConditionTree tree) {
        SqlParts parts = buildWhere(tree);
        String sql = "SELECT COUNT(*) AS c FROM `" + table(beanClass) + "`"
                + (parts.where.isEmpty() ? "" : " WHERE " + parts.where);
        try {
            List<ResultMap> rows = ex().getList(sql, parts.args);
            if (rows.isEmpty()) return 0L;
            Object cnt = rows.get(0).get("c");
            return cnt instanceof Number ? ((Number) cnt).longValue() : 0L;
        } catch (Throwable t) {
            return 0L;
        }
    }

    @Override
    public <T> Page<T> selectPageByTree(Class<T> beanClass, ConditionTree tree) {
        Page<T> page = new Page<>(tree != null && tree.getPage() != null
                ? tree.getPage().getCurrent() : 1, tree != null && tree.getPage() != null
                ? tree.getPage().getSize() : 10);
        ConditionTree copy = new ConditionTree();
        if (tree != null) {
            for (ConditionTree.Cond c : tree.getConditions()) copy.add(c.column, c.op, c.value, c.and);
            for (ConditionTree.OrderBy o : tree.getOrders()) copy.orderBy(o.column, o.direction);
        }
        copy.setPage(page);
        selectByTree(beanClass, copy);
        return page;
    }

    private static Object[] concat(Object[] head, Object... tail) {
        Object[] out = new Object[head.length + tail.length];
        System.arraycopy(head, 0, out, 0, head.length);
        System.arraycopy(tail, 0, out, head.length, tail.length);
        return out;
    }

    // ===== 写 =====

    @Override
    public <T> boolean insert(Class<T> beanClass, Object bean) {
        return upsert(beanClass, bean);
    }

    @Override
    public <T> boolean updateById(Class<T> beanClass, Object bean) {
        return upsert(beanClass, bean);
    }

    /** REPLACE INTO（MySQL/SQLite 均支持）= upsert，与 YAML 端语义一致。 */
    private <T> boolean upsert(Class<T> beanClass, Object bean) {
        if (!available || bean == null) return false;
        ensureTable(beanClass);
        PojoMeta meta = PojoMeta.of(beanClass);
        if (!meta.hasId()) return false;
        StringBuilder cols = new StringBuilder();
        StringBuilder q = new StringBuilder();
        List<Object> vals = new ArrayList<>();
        for (FieldMeta fm : meta.getFields()) {
            if (fm.isIgnored()) continue;
            if (cols.length() > 0) {
                cols.append(", ");
                q.append(", ");
            }
            cols.append('`').append(fm.columnName).append('`');
            q.append('?');
            try {
                fm.field.setAccessible(true);
                vals.add(encodeValue(fm.field.get(bean)));
            } catch (IllegalAccessException ignored) {
                vals.add(null);
            }
        }
        String sql = "REPLACE INTO `" + table(beanClass) + "` (" + cols + ") VALUES (" + q + ")";
        try {
            ex().update(sql, vals.toArray());
            return true;
        } catch (Throwable t) {
            log.warnT("log.orm.upsert-failed", "[ORM] upsert 失败: {0}", t.getMessage());
            return false;
        }
    }

    @Override
    public <T> boolean deleteById(Class<T> beanClass, Object id) {
        if (!available || id == null) return false;
        ensureTable(beanClass);
        PojoMeta meta = PojoMeta.of(beanClass);
        if (!meta.hasId()) return false;
        try {
            ex().update("DELETE FROM `" + table(beanClass) + "` WHERE `" + meta.getIdField().columnName + "` = ?", id);
            return true;
        } catch (Throwable t) {
            log.warnT("log.orm.delete-by-id-failed", "[ORM] deleteById 失败: {0}", t.getMessage());
            return false;
        }
    }

    // ===== 跨端搜索通道（实现） =====

    @Override
    public <T> List<T> search(Class<T> beanClass, String keyword, String... fields) {
        if (!available || keyword == null || keyword.isEmpty()) return java.util.Collections.emptyList();
        ensureTable(beanClass);
        PojoMeta meta = PojoMeta.of(beanClass);
        List<String> cols = new ArrayList<>();
        if (fields == null || fields.length == 0) {
            for (FieldMeta fm : meta.getFields()) {
                if (!fm.isIgnored() && (fm.type == String.class || fm.type.isEnum())) cols.add(fm.columnName);
            }
        } else {
            for (String f : fields) {
                FieldMeta fm = meta.byField(f);
                if (fm != null) cols.add(fm.columnName);
            }
        }
        if (cols.isEmpty()) return java.util.Collections.emptyList();
        StringBuilder sql = new StringBuilder("SELECT * FROM `" + table(beanClass) + "` WHERE ");
        List<Object> args = new ArrayList<>();
        for (int i = 0; i < cols.size(); i++) {
            if (i > 0) sql.append(" OR ");
            sql.append('`').append(cols.get(i)).append("` LIKE ?");
            args.add("%" + keyword + "%");
        }
        try {
            List<ResultMap> rows = ex().getList(sql.toString(), args.toArray());
            List<T> out = new ArrayList<>();
            for (ResultMap row : rows) out.add(rowToBean(beanClass, row));
            return out;
        } catch (Throwable t) {
            log.warnT("log.orm.search-failed", "[ORM] search 失败: {0}", t.getMessage());
            return java.util.Collections.emptyList();
        }
    }
}
