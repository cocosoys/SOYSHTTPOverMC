package com.github.cocosoys.mc.soyshttpovermc.storage.impl;

import com.github.cocosoys.mc.soyshttpovermc.enums.StorageType;

import com.github.cocosoys.mc.soyshttpovermc.i18n.I18n;

import com.github.cocosoys.mc.soyshttpovermc.spi.ConfigSection;
import com.github.cocosoys.mc.soyshttpovermc.spi.Platform;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * SQLite 存储后端：
 * 单文件数据库，无需额外服务，适合需要 SQL 查询能力但不想部署 MySQL 的场景。
 * Spigot 1.12.2 内置 org.sqlite.JDBC（provided，不打进 jar）。
 */
public class SqliteStorage extends SqlStorage {

    private File databaseFile;

    public SqliteStorage(Platform platform) {
        super(platform);
    }

    @Override
    public StorageType getType() {
        return StorageType.SQLITE;
    }

    @Override
    public void initialize() throws Exception {
        ConfigSection section = platform.getConfig()
                .getSection("storage.backends.sqlite");
        String path = section == null ? "data/records.db" : section.getString("file", "data/records.db");
        this.tablePrefix = section == null ? "mc_shttp_" : section.getString("table-prefix", "mc_shttp_");
        this.databaseFile = new File(platform.getDataFolder(), path);
        File parent = databaseFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException(I18n.t("exception.storage.mkdir-data-dir", "无法创建数据目录: {0}", parent.getAbsolutePath()));
        }
        super.initialize();
    }

    @Override
    public String describe() {
        return databaseFile == null ? "未初始化" : databaseFile.getPath().replace('\\', '/');
    }

    @Override
    protected String getDriverClass() {
        return "org.sqlite.JDBC";
    }

    @Override
    protected Connection createConnection() throws SQLException {
        Connection connection = java.sql.DriverManager.getConnection(
                "jdbc:sqlite:" + databaseFile.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA journal_mode = WAL");
        }
        return connection;
    }

    @Override
    protected String[] getSchemaStatements() {
        return new String[]{
                "CREATE TABLE IF NOT EXISTS " + recordsTable() + " ("
                        + "key TEXT NOT NULL PRIMARY KEY,"
                        + "type TEXT NOT NULL DEFAULT '',"
                        + "data TEXT,"
                        + "updated_at INTEGER NOT NULL DEFAULT 0"
                        + ")",
                "CREATE INDEX IF NOT EXISTS idx_" + tablePrefix + "records_type"
                        + " ON " + recordsTable() + " (type)"
        };
    }

    @Override
    protected String[] getMigrationStatements() {
        return new String[0];
    }
}
