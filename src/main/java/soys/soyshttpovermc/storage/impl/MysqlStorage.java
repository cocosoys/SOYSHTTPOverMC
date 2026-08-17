package soys.soyshttpovermc.storage.impl;

import soys.soyshttpovermc.storage.StorageType;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * MySQL 存储后端（照抄 SOYSOceanBox 的 MysqlStorage）：
 * 启用后自动成为主存储（优先级最高），可跨服共享数据。
 * 配置仅需 url / username / password / keepalive-interval，url 直连。
 * DDL 使用 MySQL 5.6 兼容语法；驱动 8.x 优先、5.x 回退。
 */
public class MysqlStorage extends SqlStorage {

    private String jdbcUrl;
    private String username = "root";
    private String password = "";
    private int keepAliveSeconds = 1800;

    public MysqlStorage(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public StorageType getType() {
        return StorageType.MYSQL;
    }

    @Override
    public void initialize() throws Exception {
        ConfigurationSection section = plugin.getConfig()
                .getConfigurationSection("storage.backends.mysql");
        if (section == null) {
            throw new IllegalStateException("config.yml 中缺少 storage.backends.mysql 配置节");
        }
        this.jdbcUrl = section.getString("url");
        if (jdbcUrl == null || jdbcUrl.trim().isEmpty()) {
            throw new IllegalStateException("storage.backends.mysql.url 未配置，无法建立 MySQL 连接");
        }
        if (!jdbcUrl.toLowerCase().startsWith("jdbc:mysql:")) {
            throw new IllegalStateException("storage.backends.mysql.url 不是合法的 MySQL JDBC 连接串");
        }
        this.username = section.getString("username", "root");
        this.password = section.getString("password", "");
        this.tablePrefix = section.getString("table-prefix", "mc_shttp_");
        this.keepAliveSeconds = section.getInt("keepalive-interval", 1800);

        try {
            Class.forName(getDriverClass());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("未找到 MySQL JDBC 驱动，请确认服务端已提供该驱动", e);
        }
        // 通过 url 直接测试连接
        try (Connection test = connect(jdbcUrl, username, password)) {
            LogKitInfo("MySQL 连接测试成功: " + maskUrl(jdbcUrl));
        } catch (SQLException e) {
            throw new IllegalStateException("MySQL 连接测试失败: " + e.getMessage(), e);
        }
        super.initialize();
    }

    @Override
    public String describe() {
        return username + "@" + maskUrl(jdbcUrl);
    }

    public int getKeepAliveSeconds() {
        return keepAliveSeconds;
    }

    @Override
    protected String getDriverClass() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            return "com.mysql.cj.jdbc.Driver";
        } catch (ClassNotFoundException e) {
            return "com.mysql.jdbc.Driver";
        }
    }

    @Override
    protected Connection createConnection() throws SQLException {
        return connect(jdbcUrl, username, password);
    }

    @Override
    protected String[] getSchemaStatements() {
        return new String[]{
                "CREATE TABLE IF NOT EXISTS `" + recordsTable() + "` ("
                        + " `key` VARCHAR(128) NOT NULL,"
                        + " `type` VARCHAR(32) NOT NULL DEFAULT '',"
                        + " `data` TEXT,"
                        + " `updated_at` BIGINT NOT NULL DEFAULT 0,"
                        + " PRIMARY KEY (`key`),"
                        + " KEY `idx_records_type` (`type`)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
        };
    }

    @Override
    protected String[] getMigrationStatements() {
        return new String[0]; // 新表已含全部列；未来加列在此追加（容忍式）
    }

    private static String maskUrl(String url) {
        try {
            int at = url.indexOf('@');
            int slash = url.indexOf("//");
            if (at > 0 && slash >= 0 && at > slash) {
                return url.substring(0, slash + 2) + "***@" + url.substring(at + 1);
            }
        } catch (Exception ignored) {
        }
        return url;
    }

    private static void LogKitInfo(String msg) {
        soys.soyshttpovermc.log.LogKit.info("[HTTP-Over-MC] " + msg);
    }
}
