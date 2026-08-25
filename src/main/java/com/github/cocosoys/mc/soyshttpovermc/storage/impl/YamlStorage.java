package com.github.cocosoys.mc.soyshttpovermc.storage.impl;
import com.github.cocosoys.mc.soyshttpovermc.enums.StorageType;
import lombok.CustomLog;

import com.github.cocosoys.mc.soyshttpovermc.i18n.I18n;
import com.github.cocosoys.mc.soyshttpovermc.storage.DataStorage;

import com.github.cocosoys.mc.soyshttpovermc.storage.SyncRecord;

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
 * YAML 文件存储后端：
 * 零外部依赖，默认启用；所有记录写在 {@code storage.backends.yaml.file} 指定<b>文件夹</b>下的
 * {@code records.yml}（默认 data/records.yml）的 {@code records: {key: {type, data, updated_at}}}
 * 节点下，对象锁保证并发安全。
 * <p>{@code file} 现在表示<b>存放各类 yml 表的文件夹</b>（KV 记录文件 records.yml + ORM 各实体表
 * 同处一目录），兼容旧式 {@code file: data/records.yml} 写法（取其父目录作为存储文件夹）。</p>
 */
@CustomLog
public class YamlStorage implements DataStorage {

    private static final String ROOT = "records";

    /** KV 记录固定文件名（位于 file 指定文件夹内）。 */
    private static final String RECORDS_FILE = "records.yml";

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

    /**
     * 解析 YAML 存储<b>文件夹</b>：{@code storage.backends.yaml.file} 现在应配置为一个文件夹路径，
     * 用于集中存放各类 yml 表（KV 记录文件 records.yml + ORM 各实体 &lt;表名&gt;.yml）。
     * <ul>
     *   <li>配置以 {@code .yml}/{@code .yaml} 结尾 → 视为旧式「单文件」写法，取其<b>父目录</b>作为存储文件夹（向后兼容）；</li>
     *   <li>配置以分隔符结尾或不含扩展名 → 视为文件夹，原样作为存储文件夹（去尾随分隔符）；</li>
     *   <li>配置为空 → 退回插件 dataFolder。</li>
     * </ul>
     */
    public static File resolveDir(JavaPlugin plugin, String fileCfg) {
        File dataFolder = plugin.getDataFolder();
        if (fileCfg == null || fileCfg.trim().isEmpty()) {
            return dataFolder;
        }
        String p = fileCfg.trim();
        File target = new File(dataFolder, p);
        String lower = p.toLowerCase();
        if (lower.endsWith(".yml") || lower.endsWith(".yaml")) {
            File parent = target.getParentFile();
            return parent == null ? dataFolder : parent;
        }
        // 作为文件夹：去尾随分隔符后原样使用
        return new File(target.getPath());
    }

    @Override
    public void initialize() throws Exception {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("storage.backends.yaml");
        String fileCfg = section == null ? null : section.getString("file", "data");
        this.backupOnSave = section != null && section.getBoolean("backup-on-save", false);

        // file 现在表示【文件夹】，记录文件固定为该目录下的 records.yml；
        // 兼容旧式 file: data/records.yml（取其父目录）。
        File dir = resolveDir(plugin, fileCfg);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException(I18n.t("exception.storage.mkdir-data-dir", "无法创建数据目录: {0}", dir.getAbsolutePath()));
        }
        this.file = new File(dir, RECORDS_FILE);
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
