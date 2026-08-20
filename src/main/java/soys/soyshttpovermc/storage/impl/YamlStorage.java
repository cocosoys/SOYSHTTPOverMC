package soys.soyshttpovermc.storage.impl;
import lombok.CustomLog;

import soys.soyshttpovermc.i18n.I18n;
import soys.soyshttpovermc.storage.DataStorage;
import soys.soyshttpovermc.storage.StorageType;
import soys.soyshttpovermc.storage.SyncRecord;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;

/**
 * YAML 文件存储后端（照抄 SOYSOceanBox 的 YamlStorage）：
 * 零外部依赖，默认启用；所有记录写在同一个文件（默认 data/records.yml）的
 * {@code records: {key: {type, data, updated_at}}} 节点下，对象锁保证并发安全。
 */
@CustomLog
public class YamlStorage implements DataStorage {

    private static final String ROOT = "records";

    private final JavaPlugin plugin;
    private final Object lock = new Object();

    private File file;
    private YamlConfiguration config;
    private boolean available = false;
    private boolean backupOnSave = false;

    public YamlStorage(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public StorageType getType() {
        return StorageType.YAML;
    }

    @Override
    public void initialize() throws Exception {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("storage.backends.yaml");
        String path = section == null ? "data/records.yml" : section.getString("file", "data/records.yml");
        this.backupOnSave = section != null && section.getBoolean("backup-on-save", false);

        this.file = new File(plugin.getDataFolder(), path);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException(I18n.t("exception.storage.mkdir-data-dir", "无法创建数据目录: {0}", parent.getAbsolutePath()));
        }
        if (!file.exists() && !file.createNewFile()) {
            throw new IOException(I18n.t("exception.storage.create-data-file", "无法创建数据文件: {0}", file.getAbsolutePath()));
        }
        synchronized (lock) {
            this.config = YamlConfiguration.loadConfiguration(file);
            if (!config.isConfigurationSection(ROOT)) {
                config.createSection(ROOT);
            }
        }
        this.available = true;
    }

    @Override
    public void shutdown() {
        synchronized (lock) {
            try {
                if (config != null && file != null) {
                    config.save(file);
                }
            } catch (IOException e) {
                log.warnT("log.storage.yaml-shutdown-save-failed",
                "[YAML] 关闭时保存失败: {0}", e.getMessage());
            }
            available = false;
        }
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public String describe() {
        return file == null ? "未初始化" : file.getPath().replace('\\', '/');
    }

    // ===== 读 =====

    @Override
    public SyncRecord load(String key) throws Exception {
        synchronized (lock) {
            ConfigurationSection section = config.getConfigurationSection(ROOT + "." + key);
            return section == null ? null : deserialize(key, section);
        }
    }

    @Override
    public Collection<SyncRecord> loadAll() {
        synchronized (lock) {
            Collection<SyncRecord> records = new ArrayList<>();
            ConfigurationSection root = config.getConfigurationSection(ROOT);
            if (root == null) {
                return records;
            }
            for (String key : root.getKeys(false)) {
                ConfigurationSection section = root.getConfigurationSection(key);
                if (section != null) {
                    SyncRecord record = deserialize(key, section);
                    if (record != null) {
                        records.add(record);
                    }
                }
            }
            return records;
        }
    }

    @Override
    public int count() {
        synchronized (lock) {
            ConfigurationSection root = config.getConfigurationSection(ROOT);
            return root == null ? 0 : root.getKeys(false).size();
        }
    }

    // ===== 写 =====

    @Override
    public void save(SyncRecord record) throws Exception {
        synchronized (lock) {
            serialize(record);
            flush();
        }
    }

    @Override
    public void saveAll(Collection<SyncRecord> records) throws Exception {
        synchronized (lock) {
            for (SyncRecord record : records) {
                serialize(record);
            }
            flush();
        }
    }

    @Override
    public void delete(String key) throws Exception {
        synchronized (lock) {
            config.set(ROOT + "." + key, null);
            flush();
        }
    }

    @Override
    public void clear() throws Exception {
        synchronized (lock) {
            config.set(ROOT, null);
            config.createSection(ROOT);
            flush();
        }
    }

    // ===== 内部 =====

    private void flush() throws IOException {
        if (backupOnSave && file.exists()) {
            File backup = new File(file.getParentFile(), file.getName() + ".bak");
            try {
                Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                log.warnT("log.storage.yaml-backup-failed", "[YAML] 备份失败: {0}", e.getMessage());
            }
        }
        config.save(file);
    }

    private void serialize(SyncRecord record) {
        String base = ROOT + "." + record.getKey();
        config.set(base + ".type", record.getType());
        config.set(base + ".data", record.getData());
        config.set(base + ".updated_at", record.getUpdatedAt());
    }

    private SyncRecord deserialize(String key, ConfigurationSection section) {
        return new SyncRecord(key,
                section.getString("type", ""),
                section.getString("data", ""),
                section.getLong("updated_at", 0L));
    }
}
