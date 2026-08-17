package soys.soyshttpovermc.storage;

import soys.soyshttpovermc.log.LogKit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SQL 存储后端公共实现（参考 SOYSMyLoot 的 SqlStorage）：
 * 子类只需提供驱动类名、JDBC URL、连接创建方式与建表语句方言。
 *
 * <p>连接策略：维持单个长连接 + 使用前有效性探测 + 对象锁串行化访问
 * （跨服同步数据量小、低频，无需连接池）；keepAlive 定时探测防 MySQL wait_timeout 断连。</p>
 *
 * <p>黑名单查询带命中缓存（5s），避免鉴权热点路径每请求查库。</p>
 */
public abstract class SqlSyncStorage implements SyncStorage {

    protected final Object lock = new Object();
    protected String tablePrefix = "mc_shttp_";
    protected volatile boolean available = false;

    /** 黑名单命中缓存：jti -> 缓存到期时间（仅缓存「已注销」的肯定结果；未命中不缓存）。 */
    private final Map<String, Long> revokedCache = new ConcurrentHashMap<>();
    private static final long REVOKED_CACHE_TTL_MS = 5_000;

    // ===== 子类需实现的方言部分 =====

    /** JDBC 驱动类名 */
    protected abstract String getDriverClass();

    /** 创建一个全新的数据库连接 */
    protected abstract Connection createConnection() throws SQLException;

    /** 建表语句（按顺序执行；含表前缀占位，子类用 tablePrefix 拼好） */
    protected abstract String[] getSchemaStatements();

    // ===== 表名 =====

    protected String blacklistTable() {
        return tablePrefix + "token_blacklist";
    }

    protected String auditTable() {
        return tablePrefix + "token_audit";
    }

    protected String instancesTable() {
        return tablePrefix + "instances";
    }

    protected String metaTable() {
        return tablePrefix + "meta";
    }

    // ===== 统一跨服 JWT 密钥（集中下发）=====

    @Override
    public byte[] loadOrCreateJwtSecret(byte[] localSecret) {
        final String KEY = "jwt_secret";
        synchronized (lock) {
            try {
                String v = selectMeta(KEY);
                if (v != null) {
                    byte[] b = decodeB64(v);
                    if (b != null && b.length >= 16) return b;
                }
                // 共享存储中尚无全局密钥：以本地文件密钥为种子，INSERT IGNORE 先到先得写回
                if (localSecret == null || localSecret.length == 0) return null;
                String b64 = java.util.Base64.getEncoder().encodeToString(localSecret);
                try (PreparedStatement ps = connection().prepareStatement(
                        "INSERT IGNORE INTO " + metaTable() + " (meta_key, meta_value, updated_at) VALUES (?, ?, ?)")) {
                    ps.setString(1, KEY);
                    ps.setString(2, b64);
                    ps.setLong(3, System.currentTimeMillis());
                    ps.executeUpdate();
                }
                // 读回（可能被并发首启的其它服抢先写入）
                String v2 = selectMeta(KEY);
                byte[] b2 = v2 == null ? null : decodeB64(v2);
                if (b2 != null && b2.length >= 16) return b2;
                return localSecret;
            } catch (SQLException e) {
                LogKit.warn("[HTTP-Over-MC] 全局 JWT 密钥读写失败，回退本地密钥: " + e.getMessage());
                return null;
            }
        }
    }

    private String selectMeta(String key) throws SQLException {
        try (PreparedStatement ps = connection().prepareStatement(
                "SELECT meta_value FROM " + metaTable() + " WHERE meta_key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private static byte[] decodeB64(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return java.util.Base64.getDecoder().decode(s.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ===== 生命周期 =====

    private Connection connection;

    @Override
    public void initialize() throws Exception {
        try {
            Class.forName(getDriverClass());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("未找到 JDBC 驱动 " + getDriverClass()
                    + "，请确认服务端已提供该驱动或手动放入 libraries 目录");
        }
        synchronized (lock) {
            connection = createConnection();
            try (Statement statement = connection.createStatement()) {
                for (String sql : getSchemaStatements()) {
                    statement.execute(sql);
                }
            }
        }
        available = true;
    }

    @Override
    public void shutdown() {
        synchronized (lock) {
            available = false;
            revokedCache.clear();
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

    @Override
    public void keepAlive() {
        synchronized (lock) {
            try {
                connection().isValid(3);
            } catch (SQLException e) {
                LogKit.warn("[HTTP-Over-MC] 存储保活探测失败: " + e.getMessage());
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

    // ===== 令牌注销黑名单 =====

    @Override
    public boolean isTokenRevoked(String jti) {
        if (jti == null || jti.isEmpty()) return false;
        Long until = revokedCache.get(jti);
        if (until != null && System.currentTimeMillis() < until) {
            return true; // 命中缓存（肯定结果）
        }
        synchronized (lock) {
            try {
                String sql = "SELECT 1 FROM " + blacklistTable() + " WHERE jti = ?";
                try (PreparedStatement ps = connection().prepareStatement(sql)) {
                    ps.setString(1, jti);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            revokedCache.put(jti, System.currentTimeMillis() + REVOKED_CACHE_TTL_MS);
                            return true;
                        }
                    }
                }
            } catch (SQLException e) {
                LogKit.warn("[HTTP-Over-MC] 黑名单查询失败: " + e.getMessage());
            }
            return false;
        }
    }

    @Override
    public void revokeToken(String jti, String serverId) {
        if (jti == null || jti.isEmpty()) return;
        synchronized (lock) {
            try {
                String sql = "INSERT INTO " + blacklistTable()
                        + " (jti, server_id, revoked_at) VALUES (?, ?, ?)";
                try (PreparedStatement ps = connection().prepareStatement(sql)) {
                    ps.setString(1, jti);
                    ps.setString(2, serverId == null ? "" : serverId);
                    ps.setLong(3, System.currentTimeMillis());
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                // 唯一键冲突 = 已注销（幂等）；其余失败降级（本地黑名单仍生效）
                if (e.getErrorCode() != 1062 && e.getSQLState() == null
                        || e.getSQLState() != null && !e.getSQLState().startsWith("23")) {
                    LogKit.warn("[HTTP-Over-MC] 黑名单写入失败: " + e.getMessage());
                }
            }
        }
    }

    // ===== 令牌签发审计 =====

    @Override
    public void recordIssued(String serverId, String subject, String mode, boolean admin,
                             String jti, long issuedAt, long expiresAt) {
        synchronized (lock) {
            try {
                String sql = "INSERT INTO " + auditTable()
                        + " (server_id, subject, mode, admin, jti, issued_at, expires_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = connection().prepareStatement(sql)) {
                    ps.setString(1, serverId == null ? "" : serverId);
                    ps.setString(2, subject);
                    ps.setString(3, mode);
                    ps.setInt(4, admin ? 1 : 0);
                    ps.setString(5, jti);
                    ps.setLong(6, issuedAt);
                    ps.setLong(7, expiresAt);
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                LogKit.warn("[HTTP-Over-MC] 令牌审计写入失败: " + e.getMessage());
            }
        }
    }

    // ===== 实例心跳 =====

    @Override
    public void heartbeat(String serverId, String name, String host, int port) {
        synchronized (lock) {
            try {
                String sql = "INSERT INTO " + instancesTable()
                        + " (server_id, name, host, port, last_heartbeat) VALUES (?, ?, ?, ?, ?)"
                        + " ON DUPLICATE KEY UPDATE name = VALUES(name), host = VALUES(host),"
                        + " port = VALUES(port), last_heartbeat = VALUES(last_heartbeat)";
                try (PreparedStatement ps = connection().prepareStatement(sql)) {
                    ps.setString(1, serverId);
                    ps.setString(2, name);
                    ps.setString(3, host == null ? "" : host);
                    ps.setInt(4, port);
                    ps.setLong(5, System.currentTimeMillis());
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                LogKit.warn("[HTTP-Over-MC] 实例心跳写入失败: " + e.getMessage());
            }
        }
    }
}
