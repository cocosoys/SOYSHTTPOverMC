package soys.soyshttpovermc.storage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * MySQL 存储后端（URL 直连）。
 *
 * <p>配置（config.yml mysql 段）：
 * <pre>
 *   mysql:
 *     enabled: false
 *     url: 'jdbc:mysql://localhost:3306/minecraft?...'
 *     username: root
 *     password: '123456'
 *     table-prefix: 'mc_shttp_'
 *     keepalive-interval: 1800
 * </pre>
 * 表结构（自动创建，table-prefix 前缀）：token_blacklist / token_audit / instances。
 */
public class MysqlSyncStorage extends SqlSyncStorage {

    private final String url;
    private final String username;
    private final String password;

    public MysqlSyncStorage(String url, String username, String password, String tablePrefix) {
        this.url = url;
        this.username = username;
        this.password = password;
        if (tablePrefix != null && !tablePrefix.isEmpty()) {
            this.tablePrefix = tablePrefix;
        }
    }

    @Override
    public StorageType getType() {
        return StorageType.MYSQL;
    }

    @Override
    protected String getDriverClass() {
        // 优先 8.x（自带 shade 进 jar，连 MySQL 8 认证兼容）；服务端内置 5.x 时回退（com.mysql.jdbc.Driver）
        try {
            Class.forName(DRIVER_CJ);
            return DRIVER_CJ;
        } catch (ClassNotFoundException e) {
            return DRIVER_LEGACY;
        }
    }

    private static final String DRIVER_CJ = "com.mysql.cj.jdbc.Driver";
    private static final String DRIVER_LEGACY = "com.mysql.jdbc.Driver";

    @Override
    protected Connection createConnection() throws SQLException {
        Properties props = new Properties();
        if (username != null) props.setProperty("user", username);
        if (password != null) props.setProperty("password", password);
        props.setProperty("connectTimeout", "5000");
        props.setProperty("socketTimeout", "60000");
        return DriverManager.getConnection(url, props);
    }

    @Override
    protected String[] getSchemaStatements() {
        String bl = blacklistTable();
        String au = auditTable();
        String ins = instancesTable();
        String meta = metaTable();
        // 全部使用 MySQL 5.6 兼容语法（InnoDB / utf8mb4(5.5.3+) / 通用 DDL，无 8.0 专有语法）：
        // 驱动用 8.0.28（官方支持 MySQL 5.6+，与 5.x server 的 mysql_native_password 兼容）。
        return new String[]{
                "CREATE TABLE IF NOT EXISTS `" + bl + "` ("
                        + " `jti` VARCHAR(64) NOT NULL,"
                        + " `server_id` VARCHAR(64) NOT NULL DEFAULT '',"
                        + " `revoked_at` BIGINT NOT NULL,"
                        + " PRIMARY KEY (`jti`)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
                "CREATE TABLE IF NOT EXISTS `" + au + "` ("
                        + " `id` BIGINT NOT NULL AUTO_INCREMENT,"
                        + " `server_id` VARCHAR(64) NOT NULL DEFAULT '',"
                        + " `subject` VARCHAR(64) NOT NULL,"
                        + " `mode` VARCHAR(16) NOT NULL,"
                        + " `admin` TINYINT NOT NULL DEFAULT 0,"
                        + " `jti` VARCHAR(64) NOT NULL,"
                        + " `issued_at` BIGINT NOT NULL,"
                        + " `expires_at` BIGINT NOT NULL,"
                        + " PRIMARY KEY (`id`),"
                        + " KEY `idx_audit_jti` (`jti`),"
                        + " KEY `idx_audit_subject` (`subject`)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
                "CREATE TABLE IF NOT EXISTS `" + ins + "` ("
                        + " `server_id` VARCHAR(64) NOT NULL,"
                        + " `name` VARCHAR(64) NOT NULL,"
                        + " `host` VARCHAR(64) NOT NULL DEFAULT '',"
                        + " `port` INT NOT NULL DEFAULT 0,"
                        + " `last_heartbeat` BIGINT NOT NULL,"
                        + " PRIMARY KEY (`server_id`)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
                "CREATE TABLE IF NOT EXISTS `" + meta + "` ("
                        + " `meta_key` VARCHAR(64) NOT NULL,"
                        + " `meta_value` VARCHAR(512) NOT NULL,"
                        + " `updated_at` BIGINT NOT NULL,"
                        + " PRIMARY KEY (`meta_key`)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
        };
    }

    @Override
    public String describe() {
        return "MySQL " + url;
    }
}
