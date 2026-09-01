package com.github.cocosoys.mc.soyshttpovermc.storage.impl;

import com.github.cocosoys.mc.soyshttpovermc.enums.StorageType;
import com.github.cocosoys.mc.soyshttpovermc.i18n.I18n;
import lombok.CustomLog;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * MySQL 存储后端：
 * 启用后自动成为主存储（优先级最高），可跨服共享数据。
 * 配置仅需 url / username / password / keepalive-interval，url 直连。
 * DDL 使用 MySQL 5.6 兼容语法；驱动 8.x 优先、5.x 回退。
 */
@CustomLog
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
            throw new IllegalStateException(I18n.t("exception.storage.mysql.missing-config", "config.yml 中缺少 storage.backends.mysql 配置节"));
        }
        this.jdbcUrl = section.getString("url");
        if (jdbcUrl == null || jdbcUrl.trim().isEmpty()) {
            throw new IllegalStateException(I18n.t("exception.storage.mysql.url-not-configured", "storage.backends.mysql.url 未配置，无法建立 MySQL 连接"));
        }
        if (!jdbcUrl.toLowerCase().startsWith("jdbc:mysql:")) {
            throw new IllegalStateException(I18n.t("exception.storage.mysql.url-invalid", "storage.backends.mysql.url 不是合法的 MySQL JDBC 连接串"));
        }
        this.username = section.getString("username", "root");
        this.password = section.getString("password", "");
        this.tablePrefix = section.getString("table-prefix", "mc_shttp_");
        this.keepAliveSeconds = section.getInt("keepalive-interval", 1800);

        String driver = getDriverClass();
        try {
            Class.forName(driver);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(I18n.t("exception.storage.mysql.driver-not-found", "未找到 MySQL JDBC 驱动，请确认服务端已提供该驱动"), e);
        }
        // 兼容处理：服务端自带 5.x 驱动（com.mysql.jdbc.Driver）不识别 serverTimezone 等
        // 8.x 专属连接参数，会抛 "Unsupported connection property"；自动剥离后再测试连接。
        this.jdbcUrl = sanitizeJdbcUrl(driver, jdbcUrl);
        // 通过 url 直接测试连接
        try (Connection test = connect(jdbcUrl, username, password)) {
            log.infoT("log.storage.mysql-connect-success",
                    "MySQL 连接测试成功: {0}", maskUrl(jdbcUrl));
        } catch (SQLException e) {
            throw new IllegalStateException(I18n.t("exception.storage.mysql.connect-test-failed", "MySQL 连接测试失败: {0}", e.getMessage()), e);
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

    /**
     * 兼容处理：MySQL 5.x 驱动（com.mysql.jdbc.Driver，服务端自带）不识别 8.x 专属连接参数
     * （如 serverTimezone），会抛 "Unsupported connection property"。加载 5.x 驱动时自动剥离
     * 这些 8.x 专属参数；8.x 驱动（com.mysql.cj.jdbc.Driver）原样保留。
     */
    private static String sanitizeJdbcUrl(String driverClass, String url) {
        if (!"com.mysql.jdbc.Driver".equals(driverClass)) {
            return url; // 8.x 驱动保留全部参数
        }
        int q = url.indexOf('?');
        if (q < 0) {
            return url;
        }
        String base = url.substring(0, q);
        String query = url.substring(q + 1);
        StringBuilder sb = new StringBuilder();
        for (String pair : query.split("&")) {
            if (pair.isEmpty()) continue;
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            if ("serverTimezone".equalsIgnoreCase(key)) {
                continue; // 5.x 不识别，剥离
            }
            if (sb.length() > 0) sb.append('&');
            sb.append(pair);
        }
        return sb.length() == 0 ? base : base + "?" + sb;
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
}
