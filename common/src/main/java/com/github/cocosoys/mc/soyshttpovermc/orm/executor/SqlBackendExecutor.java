package com.github.cocosoys.mc.soyshttpovermc.orm.executor;

import com.github.cocosoys.mc.soyshttpovermc.orm.convertor.BeanCodec;
import com.dlz.db.convertor.columnname.ColumnNameLower;
import com.dlz.db.core.DlzDbProperties;
import com.dlz.db.core.ISqlExecutor;
import com.dlz.db.core.jdbc.JdbcSqlExecutor;
import com.dlz.db.core.jdbc.JdbcTxExecutor;
import com.dlz.db.modal.DB;
import com.dlz.db.modal.dto.ResultMap;
import com.dlz.db.support.DBHolder;
import com.github.cocosoys.mc.soyshttpovermc.enums.StorageType;
import com.github.cocosoys.mc.soyshttpovermc.orm.meta.FieldMeta;
import com.github.cocosoys.mc.soyshttpovermc.orm.meta.PojoMeta;
import com.github.cocosoys.mc.soyshttpovermc.orm.query.ConditionTree;
import com.github.cocosoys.mc.soyshttpovermc.orm.query.Op;
import com.github.cocosoys.mc.soyshttpovermc.orm.query.Page;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.github.cocosoys.mc.soyshttpovermc.spi.ConfigSection;
import com.github.cocosoys.mc.soyshttpovermc.spi.Platform;
import lombok.CustomLog;

import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

/**
 * SQL 后端执行器：基于 dlz-db-core 链路（HikariCP 数据源 + ISqlExecutor），
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

    /**
     * 从 config storage.backends 装配（mysql 优先，其次 sqlite）；失败返回 null。
     */
    public static SqlBackendExecutor init(Platform platform) {
        try {
            HikariDataSource ds = buildDataSource(platform);
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

    /**
     * 直接装配（本地测试 / 二次接入用）：Hikari 数据源 + dlz DBHolder 初始化 + 列名统一。
     */
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

    private static HikariDataSource buildDataSource(Platform platform) {
        ConfigSection mysql = platform.getConfig().getSection("storage.backends.mysql");
        if (mysql != null && mysql.getBoolean("enabled", false)) {
            return buildHikari(mysql.getString("url", ""), mysql.getString("username", "root"),
                    mysql.getString("password", ""), detectMysqlDriver());
        }
        ConfigSection sqlite = platform.getConfig().getSection("storage.backends.sqlite");
        if (sqlite != null && sqlite.getBoolean("enabled", false)) {
            String file = new java.io.File(platform.getDataFolder(),
                    sqlite.getString("file", "data/records.db")).getAbsolutePath();
            return buildHikari("jdbc:sqlite:" + file, null, null, "org.sqlite.JDBC");
        }
        return null;
    }

    /**
     * MySQL 驱动探测：8.x（com.mysql.cj.jdbc.Driver）优先，回退服务端自带 5.x
     * （com.mysql.jdbc.Driver）。与 {@link MysqlStorage#getDriverClass()} 口径一致；
     * 1.6.4/1.7.10 服务端仅带 5.x，1.12.2 若有 cj 则用 cj。
     */
    private static String detectMysqlDriver() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            return "com.mysql.cj.jdbc.Driver";
        } catch (ClassNotFoundException e) {
            return "com.mysql.jdbc.Driver";
        }
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
        // 兼容 JDBC3 旧驱动（1.6.4/1.7.10 服务端自带 sqlite/mysql 无 Connection.isValid）：
        // Hikari 官方对 legacy 驱动的兼容方式是 connectionTestQuery——设置后连接校验改走
        // 测试查询、跳过 isValid 探测（对支持 isValid 的驱动无影响，1.12.2 依旧用 isValid）。
        cfg.setConnectionTestQuery("SELECT 1");
        return new HikariDataSource(cfg);
    }

    /**
     * 当前实例（未装配时 null）。
     */
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

    /**
     * 执行器可用性（数据源未装配/失败时 false）。
     */
    public boolean isAvailable() {
        return available;
    }

    // ===== DDL 自动生成 =====

    /**
     * 建表（CREATE TABLE IF NOT EXISTS）+ 缺列补列（容忍式 ALTER）。
     */
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

    /**
     * 执行原生 SQL（自动运维 init.sql 等场景；多条语句请先自行拆分）。
     * @throws RuntimeException 执行失败（调用方按 auto.ops.fail 策略处理）
     */
    public void execSql(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return;
        }
        ex().update(sql.trim());
    }

    /**
     * 探测表是否存在（JDBC DatabaseMetaData，MySQL / SQLite 通用）。
     * 用于区分"全新安装"与"老版本升级但 meta 缺失"（避免把老数据当新装处理）。
     */
    public boolean tableExists(String table) {
        if (table == null || table.trim().isEmpty()) {
            return false;
        }
        try (java.sql.Connection c = ex().getConnectionSupplier().get();
             java.sql.ResultSet rs = c.getMetaData().getTables(null, null, table.trim(), null)) {
            try {
                return rs.next();
            } finally {
                rs.close();
            }
        } catch (Exception e) {
            return false;
        }
    }
    private static String sqlType(Class<?> type) {
        if (type == String.class) return "VARCHAR(255)";
        if (type == Integer.class || type == int.class) return "INT";
        if (type == Long.class || type == long.class) return "BIGINT";
        if (type == Double.class || type == double.class || type == Float.class || type == float.class) return "DOUBLE";
        if (type == Boolean.class || type == boolean.class) return "TINYINT";
        if (type == java.util.Date.class || type == Date.class) return "VARCHAR(255)"; // Date 统一存 yyyy-MM-dd HH:mm:ss 字符串（与 YAML 同构）
        if (type.isEnum()) return "VARCHAR(64)";
        return "TEXT"; // 嵌套（List/Map/对象）→ JSON
    }

    // ===== 条件树 → SQL =====

    /**
     * 翻译 WHERE + 参数；无条件返回空串与空参。
     */
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
                    case EQ:
                        where.append(" = ?");
                        args.add(encodeCond(c.value));
                        break;
                    case NE:
                        where.append(" <> ?");
                        args.add(encodeCond(c.value));
                        break;
                    case GT:
                        where.append(" > ?");
                        args.add(encodeCond(c.value));
                        break;
                    case GE:
                        where.append(" >= ?");
                        args.add(encodeCond(c.value));
                        break;
                    case LT:
                        where.append(" < ?");
                        args.add(encodeCond(c.value));
                        break;
                    case LE:
                        where.append(" <= ?");
                        args.add(encodeCond(c.value));
                        break;
                    case LIKE:
                        where.append(" LIKE ?");
                        args.add("%" + String.valueOf(c.value).replace("%", "") + "%");
                        break;
                    case IN:
                    case NOT_IN: {
                        where.append(c.op == Op.IN ? " IN (" : " NOT IN (");
                        List<?> vals = c.value instanceof List ? (List<?>) c.value : java.util.Collections.singletonList(c.value);
                        for (int i = 0; i < vals.size(); i++) {
                            if (i > 0) where.append(",");
                            where.append("?");
                            args.add(encodeCond(vals.get(i)));
                        }
                        where.append(')');
                        break;
                    }
                    case IS_NULL:
                        where.append(" IS NULL");
                        break;
                    case NOT_NULL:
                        where.append(" IS NOT NULL");
                        break;
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
        if (type == Integer.class || type == int.class)
            return raw instanceof Number ? ((Number) raw).intValue() : Integer.parseInt(String.valueOf(raw));
        if (type == Long.class || type == long.class)
            return raw instanceof Number ? ((Number) raw).longValue() : Long.parseLong(String.valueOf(raw));
        if (type == Double.class || type == double.class)
            return raw instanceof Number ? ((Number) raw).doubleValue() : Double.parseDouble(String.valueOf(raw));
        if (type == Boolean.class || type == boolean.class)
            return raw instanceof Boolean ? raw : raw instanceof Number ? ((Number) raw).intValue() != 0 : Boolean.parseBoolean(String.valueOf(raw));
        if (type == java.util.Date.class)
            return BeanCodec.coerceDate(raw);
        if (type.isEnum()) return Enum.valueOf((Class<Enum>) type, String.valueOf(raw));
        return raw;
    }

    /**
     * 实体 → 列值数组（写路径）。
     */
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

    /** 条件值编码（Date → 统一日期字符串，与列存储一致）。 */
    private static Object encodeCond(Object v) {
        return v instanceof java.util.Date ? BeanCodec.formatDate((java.util.Date) v) : v;
    }

    private static Object encodeValue(Object v) {
        if (v instanceof java.util.Date) return BeanCodec.formatDate((java.util.Date) v);
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

    /**
     * REPLACE INTO（MySQL/SQLite 均支持）= upsert，与 YAML 端语义一致。
     */
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

    // ===== 跨端搜索（实现：LIKE 查询，通配符转义统一字面语义） =====

    @Override
    public <T> List<T> search(Class<T> beanClass, String keyword, String... fields) {
        if (!available || keyword == null || keyword.isEmpty()) return java.util.Collections.emptyList();
        ensureTable(beanClass);
        PojoMeta meta = PojoMeta.of(beanClass);
        List<String> cols = searchColumns(meta, fields);
        if (cols.isEmpty()) return java.util.Collections.emptyList();
        String sql = "SELECT * FROM `" + table(beanClass) + "` WHERE " + buildLikeWhere(cols);
        try {
            List<ResultMap> rows = ex().getList(sql, buildLikeArgs(cols, keyword));
            List<T> out = new ArrayList<>();
            for (ResultMap row : rows) out.add(rowToBean(beanClass, row));
            return out;
        } catch (Throwable t) {
            log.warnT("log.orm.search-failed", "[ORM] search 失败: {0}", t.getMessage());
            return java.util.Collections.emptyList();
        }
    }

    @Override
    public <T> Page<T> searchPage(Class<T> beanClass, long current, long size, String keyword, String... fields) {
        Page<T> page = new Page<>(current, size);
        if (!available || keyword == null || keyword.isEmpty()) return page;
        ensureTable(beanClass);
        PojoMeta meta = PojoMeta.of(beanClass);
        List<String> cols = searchColumns(meta, fields);
        if (cols.isEmpty()) return page;
        String where = buildLikeWhere(cols);
        Object[] likeArgs = buildLikeArgs(cols, keyword);
        try {
            long total = countLike(beanClass, where, likeArgs);
            page.setTotal(total);
            String sql = "SELECT * FROM `" + table(beanClass) + "` WHERE " + where + " LIMIT ? OFFSET ?";
            List<ResultMap> rows = ex().getList(sql, concat(likeArgs, page.getSize(), page.offset()));
            List<T> out = new ArrayList<>();
            for (ResultMap row : rows) out.add(rowToBean(beanClass, row));
            page.setRecords((List) out);
            return page;
        } catch (Throwable t) {
            log.warnT("log.orm.search-page-failed", "[ORM] searchPage 失败: {0}", t.getMessage());
            return page;
        }
    }

    /**
     * 解析搜索目标列：未指定 fields 时默认 String/Enum 列；指定时按字段名解析（忽略不存在的字段）。
     */
    private static List<String> searchColumns(PojoMeta meta, String... fields) {
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
        return cols;
    }

    /**
     * LIKE WHERE 段：`col` LIKE ? ESCAPE '\' OR ...——通配符转义统一为字面包含（与 YAML contains 一致）。
     */
    private static String buildLikeWhere(List<String> cols) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cols.size(); i++) {
            if (i > 0) sb.append(" OR ");
            sb.append('`').append(cols.get(i)).append("` LIKE ? ESCAPE '\\'");
        }
        return sb.toString();
    }

    /**
     * LIKE 参数值：keyword 转义通配符（\ % _）后包裹 %。
     */
    private static Object[] buildLikeArgs(List<String> cols, String keyword) {
        String escaped = keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        Object[] args = new Object[cols.size()];
        for (int i = 0; i < cols.size(); i++) {
            args[i] = "%" + escaped + "%";
        }
        return args;
    }

    private long countLike(Class<?> beanClass, String where, Object[] args) {
        String sql = "SELECT COUNT(*) AS c FROM `" + table(beanClass) + "` WHERE " + where;
        try {
            List<ResultMap> rows = ex().getList(sql, args);
            if (rows.isEmpty()) return 0L;
            Object cnt = rows.get(0).get("c");
            return cnt instanceof Number ? ((Number) cnt).longValue() : 0L;
        } catch (Throwable t) {
            return 0L;
        }
    }
}
