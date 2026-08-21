package soys.soyshttpovermc.storage.impl;
import lombok.CustomLog;

import soys.soyshttpovermc.i18n.I18n;
import soys.soyshttpovermc.log.LogKit;
import soys.soyshttpovermc.storage.DataStorage;
import soys.soyshttpovermc.storage.SyncRecord;
import soys.soyshttpovermc.storage.StorageType;

import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SQL 存储后端的公共实现：
 * SQLite 与 MySQL 共用同一套表结构与 CRUD，子类只需提供驱动类名、连接创建方式、
 * 建表语句与容忍式迁移语句（方言）。
 *
 * <p>表结构（table-prefix 前缀）：{@code records(key PK, type, data TEXT, updated_at BIGINT)}
 * —— 与 YAML 后端的 {@code records: {key: {type, data, updated_at}}} 天然同构（通解）。</p>
 *
 * <p>连接策略：单长连接 + 使用前有效性探测 + 对象锁串行化访问；keepAlive 定时防断连。</p>
 */
@CustomLog
public abstract class SqlStorage implements DataStorage {

    protected final JavaPlugin plugin;
    protected final Object lock = new Object();
    protected String tablePrefix = "mc_shttp_";
    protected volatile boolean available = false;
    private Connection connection;

    protected SqlStorage(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // ===== 子类需实现的方言部分 =====

    /** JDBC 驱动类名 */
    protected abstract String getDriverClass();

    /** 创建一个全新的数据库连接 */
    protected abstract Connection createConnection() throws SQLException;

    /** 建表与建索引语句（按顺序执行） */
    protected abstract String[] getSchemaStatements();

    /** 为已存在旧表补列的 ALTER（容忍式执行，列已存在忽略） */
    protected abstract String[] getMigrationStatements();

    // ===== 表名 =====

    protected String recordsTable() {
        return tablePrefix + "records";
    }

    // ===== 生命周期 =====

    @Override
    public void initialize() throws Exception {
        try {
            Class.forName(getDriverClass());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(I18n.t("exception.storage.jdbc-driver-not-found", "未找到 JDBC 驱动 {0}，请确认服务端已提供该驱动或手动放入 libraries 目录", getDriverClass()));
        }
        synchronized (lock) {
            connection = createConnection();
            try (Statement statement = connection.createStatement()) {
                for (String sql : getSchemaStatements()) {
                    statement.execute(sql);
                }
                for (String sql : getMigrationStatements()) {
                    try {
                        statement.execute(sql);
                    } catch (SQLException e) {
                        String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
                        if (msg.contains("duplicate") || msg.contains("already exists")
                                || msg.contains("exist") || msg.contains("重复")) {
                            continue; // 列已存在，预期情况
                        }
                        log.warnT("log.storage.schema-migration-skip",
                            "[{0}] 表结构迁移跳过: {1}", getType().getId(), e.getMessage());
                    }
                }
            }
        }
        available = true;
    }

    @Override
    public void shutdown() {
        synchronized (lock) {
            available = false;
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException ignored) {
                }
                connection = null;
            }
        }
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    /** 主动探测连接（保活任务调用）。 */
    public void keepAlive() {
        synchronized (lock) {
            try {
                connection().isValid(3);
            } catch (SQLException e) {
                log.warnT("log.storage.keepalive-failed",
                        "[{0}] 保活探测失败: {1}", getType().getId(), e.getMessage());
            }
        }
    }

    /** 获取可用连接，失效自动重建；调用方须持有 {@link #lock}。 */
    protected Connection connection() throws SQLException {
        if (connection == null || connection.isClosed() || !connection.isValid(3)) {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException ignored) {
                }
            }
            connection = createConnection();
        }
        return connection;
    }

    // ===== 读 =====

    @Override
    public SyncRecord load(String key) throws Exception {
        synchronized (lock) {
            String sql = "SELECT type, data, updated_at FROM " + recordsTable() + " WHERE `key` = ?";
            try (PreparedStatement ps = connection().prepareStatement(sql)) {
                ps.setString(1, key);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new SyncRecord(key, rs.getString("type"),
                                rs.getString("data"), rs.getLong("updated_at"));
                    }
                }
            }
            return null;
        }
    }

    @Override
    public Collection<SyncRecord> loadAll() throws Exception {
        synchronized (lock) {
            Map<String, SyncRecord> map = new LinkedHashMap<>();
            String sql = "SELECT `key`, type, data, updated_at FROM " + recordsTable();
            try (Statement st = connection().createStatement();
                 ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    String key = rs.getString("key");
                    if (key == null) continue;
                    map.put(key, new SyncRecord(key, rs.getString("type"),
                            rs.getString("data"), rs.getLong("updated_at")));
                }
            }
            return new ArrayList<>(map.values());
        }
    }

    @Override
    public int count() throws Exception {
        synchronized (lock) {
            try (Statement st = connection().createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + recordsTable())) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    // ===== 写 =====

    @Override
    public void save(SyncRecord record) throws Exception {
        synchronized (lock) {
            String sql = "REPLACE INTO " + recordsTable()
                    + " (`key`, type, data, updated_at) VALUES (?, ?, ?, ?)";
            try (PreparedStatement ps = connection().prepareStatement(sql)) {
                ps.setString(1, record.getKey());
                ps.setString(2, record.getType());
                ps.setString(3, record.getData());
                ps.setLong(4, record.getUpdatedAt());
                ps.executeUpdate();
            }
        }
    }

    @Override
    public void saveAll(Collection<SyncRecord> records) throws Exception {
        if (records == null || records.isEmpty()) return;
        synchronized (lock) {
            Connection conn = connection();
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                String sql = "REPLACE INTO " + recordsTable()
                        + " (`key`, type, data, updated_at) VALUES (?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    for (SyncRecord r : records) {
                        if (r == null) continue;
                        ps.setString(1, r.getKey());
                        ps.setString(2, r.getType());
                        ps.setString(3, r.getData());
                        ps.setLong(4, r.getUpdatedAt());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                conn.commit();
            } catch (SQLException e) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {
                }
                throw e;
            } finally {
                conn.setAutoCommit(autoCommit);
            }
        }
    }

    @Override
    public void delete(String key) throws Exception {
        synchronized (lock) {
            try (PreparedStatement ps = connection().prepareStatement(
                    "DELETE FROM " + recordsTable() + " WHERE `key` = ?")) {
                ps.setString(1, key);
                ps.executeUpdate();
            }
        }
    }

    @Override
    public void clear() throws Exception {
        synchronized (lock) {
            try (Statement st = connection().createStatement()) {
                st.executeUpdate("DELETE FROM " + recordsTable());
            }
        }
    }

    /** 便捷连接创建（子类复用：url 直连 + 超时）。 */
    protected static Connection connect(String url, String username, String password) throws SQLException {
        if (username == null && password == null) {
            return DriverManager.getConnection(url);
        }
        java.util.Properties props = new java.util.Properties();
        if (username != null) props.setProperty("user", username);
        if (password != null) props.setProperty("password", password);
        props.setProperty("connectTimeout", "5000");
        props.setProperty("socketTimeout", "60000");
        return DriverManager.getConnection(url, props);
    }
}
