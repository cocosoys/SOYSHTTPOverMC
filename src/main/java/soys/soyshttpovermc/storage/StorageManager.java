package soys.soyshttpovermc.storage;
import lombok.CustomLog;

import soys.soyshttpovermc.i18n.I18n;
import soys.soyshttpovermc.log.LogKit;
import soys.soyshttpovermc.storage.impl.MysqlStorage;
import soys.soyshttpovermc.storage.impl.SqlStorage;
import soys.soyshttpovermc.storage.impl.SqliteStorage;
import soys.soyshttpovermc.storage.impl.YamlStorage;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 存储协调器（照抄 SOYSOceanBox 的 StorageManager）。
 *
 * <p><b>主辅模型</b>：所有已启用后端中优先级最高者（MYSQL &gt; SQLITE &gt; YAML）成为主存储，
 * 承担全部读操作；其余后端作为辅助存储，写入时被镜像同步（热备份 / 降级方案）。
 * 多后端可同时开启。</p>
 *
 * <p><b>写入顺序</b>：所有异步写入收敛到单线程执行器，保证写操作严格有序，避免并发写错乱。</p>
 *
 * <p><b>互转与覆盖</b>：{@link #migrate} 在任意两端之间转换数据；{@link #syncToSecondaries}
 * 将主存储全量覆盖同步到所有辅助存储（{@code /soyshttp migrate|sync} 命令）。</p>
 */
@CustomLog
public class StorageManager {

    private final JavaPlugin plugin;

    /** 全部已成功初始化的后端 */
    private final Map<StorageType, DataStorage> storages = new EnumMap<>(StorageType.class);

    private DataStorage primary;
    private final List<DataStorage> secondaries = new ArrayList<>();

    /** 串行写入线程，保证写顺序 */
    private ExecutorService writeExecutor;

    public StorageManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // ===== 生命周期 =====

    /** 构建并初始化所有启用的后端，选出主存储；无任何可用后端时抛 IllegalStateException。 */
    public void initialize() {
        shutdownInternal(false);

        this.writeExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "HTTP-Over-MC-Storage");
            t.setDaemon(true);
            return t;
        });

        for (StorageType type : StorageType.values()) {
            if (!isBackendEnabled(type.getId())) {
                continue;
            }
            DataStorage storage = buildStorage(type);
            if (storage == null) {
                continue;
            }
            try {
                storage.initialize();
                storages.put(type, storage);
                log.infoT("log.storage.backend-enabled",
                    "已启用存储后端: {0} ({1})", type.getDisplayName(), storage.describe());
            } catch (Exception e) {
                log.warnT("log.storage.backend-init-failed",
                    "存储后端 {0} 初始化失败，已跳过: {1}", type.getDisplayName(), e.getMessage());
            }
        }

        if (storages.isEmpty()) {
            throw new IllegalStateException(I18n.t("exception.storage.no-backend-available", "没有任何可用的存储后端，请检查 config.yml 中 storage.backends 的配置"));
        }

        // 按优先级降序，最高者为主存储
        List<DataStorage> sorted = new ArrayList<>(storages.values());
        sorted.sort(Comparator.comparingInt((DataStorage s) -> s.getType().getPriority()).reversed());
        this.primary = sorted.get(0);
        this.secondaries.clear();
        for (int i = 1; i < sorted.size(); i++) {
            secondaries.add(sorted.get(i));
        }

        // 跨服数据同步前置校验：开启跨服但主存储并非 MySQL → 多实例无法共享
        if (isCrossServer() && primary.getType() != StorageType.MYSQL) {
            log.warnT("log.storage.cross-server-not-mysql",
                    "已启用跨服数据同步（storage.cross-server: true），"
                    + "但主存储并非 MySQL，多实例将无法共享数据！请在所有实例的 config.yml 中"
                    + "将 storage.backends.mysql.enabled 设为 true 并指向同一数据库。");
        }

        log.infoT("log.storage.primary", "主存储: {0}{1}",
                primary.getType().getDisplayName(),
                (secondaries.isEmpty() ? "，无辅助存储" : "，辅助存储: " + describeSecondaries()));

        startKeepAliveTask();

        if (isSyncOnStartup() && !secondaries.isEmpty()) {
            log.infoT("log.storage.startup-sync-starting",
                    "正在执行启动时同步...");
            submit(() -> {
                try {
                    int count = syncToSecondaries();
                    log.infoT("log.storage.startup-sync-complete",
                            "启动同步完成，已写入 {0} 条记录", count);
                } catch (Exception e) {
                    log.warnT("log.storage.startup-sync-failed",
                            "启动同步失败: {0}", e.getMessage());
                }
            });
        }
    }

    /** 关闭所有后端，等待写队列排空。 */
    public void shutdown() {
        shutdownInternal(true);
    }

    private void shutdownInternal(boolean awaitWrites) {
        if (writeExecutor != null) {
            writeExecutor.shutdown();
            if (awaitWrites) {
                try {
                    if (!writeExecutor.awaitTermination(15, TimeUnit.SECONDS)) {
                        log.warnT("log.storage.writer-drain-timeout",
                        "存储写入队列未能在 15 秒内排空，部分数据可能丢失");
                        writeExecutor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    writeExecutor.shutdownNow();
                }
            }
            writeExecutor = null;
        }
        for (DataStorage storage : storages.values()) {
            try {
                storage.shutdown();
            } catch (Exception e) {
                log.warnT("log.storage.shutdown-failed",
                "关闭存储后端 {0} 时出错: {1}", storage.getType().getId(), e.getMessage());
            }
        }
        storages.clear();
        secondaries.clear();
        primary = null;
    }

    /** 构建后端实例。新增后端类型时在此注册即可。 */
    private DataStorage buildStorage(StorageType type) {
        switch (type) {
            case YAML:
                return new YamlStorage(plugin);
            case SQLITE:
                return new SqliteStorage(plugin);
            case MYSQL:
                return new MysqlStorage(plugin);
            default:
                return null;
        }
    }

    private void startKeepAliveTask() {
        DataStorage mysql = storages.get(StorageType.MYSQL);
        if (!(mysql instanceof MysqlStorage)) {
            return;
        }
        int seconds = ((MysqlStorage) mysql).getKeepAliveSeconds();
        if (seconds <= 0) {
            return;
        }
        long ticks = seconds * 20L;
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin,
                () -> ((SqlStorage) mysql).keepAlive(), ticks, ticks);
    }

    // ===== 访问器 =====

    public DataStorage getPrimary() {
        return primary;
    }

    public List<DataStorage> getSecondaries() {
        return Collections.unmodifiableList(secondaries);
    }

    public DataStorage getStorage(StorageType type) {
        return storages.get(type);
    }

    public Collection<DataStorage> getAllStorages() {
        return Collections.unmodifiableCollection(storages.values());
    }

    public boolean isEnabled(StorageType type) {
        return storages.containsKey(type);
    }

    private String describeSecondaries() {
        StringBuilder sb = new StringBuilder();
        for (DataStorage storage : secondaries) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(storage.getType().getDisplayName());
        }
        return sb.toString();
    }

    // ===== 配置读取（config.yml storage 段，照抄 SOYSOceanBox ConfigManager） =====

    public boolean isBackendEnabled(String backendId) {
        return plugin.getConfig().getBoolean("storage.backends." + backendId + ".enabled", false);
    }

    public boolean isCrossServer() {
        return plugin.getConfig().getBoolean("storage.cross-server", false);
    }

    public boolean isMirrorEnabled() {
        return plugin.getConfig().getBoolean("storage.mirror.enabled", true);
    }

    public boolean isMirrorAsync() {
        return plugin.getConfig().getBoolean("storage.mirror.async", true);
    }

    public boolean isSyncOnStartup() {
        return plugin.getConfig().getBoolean("storage.mirror.sync-on-startup", false);
    }

    // ===== 异步调度 =====

    /** 提交任务到串行写入线程；关服阶段直接当前线程执行（保证不丢）。 */
    public void submit(Runnable task) {
        ExecutorService executor = this.writeExecutor;
        if (executor == null || executor.isShutdown()) {
            task.run();
            return;
        }
        executor.submit(() -> {
            try {
                task.run();
            } catch (Throwable t) {
                log.warnT("log.storage.task-error", "存储任务执行异常: {0}", t.getMessage());
            }
        });
    }

    // ===== 读（仅主存储） =====

    /** 从主存储同步读取单条记录（可能阻塞，勿在主线程调用 SQL 后端）。 */
    public SyncRecord load(String key) throws Exception {
        return primary.load(key);
    }

    public Collection<SyncRecord> loadAll() throws Exception {
        return primary.loadAll();
    }

    /** 与 loadAll 相同但吞异常返回空集合（管理指令安全遍历）。 */
    public Collection<SyncRecord> loadAllSafe() {
        try {
            return primary.loadAll();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public int count() {
        try {
            return primary.count();
        } catch (Exception e) {
            return 0;
        }
    }

    // ===== 写（主存储 + 镜像） =====

    /** 异步保存：主存储写入后镜像到辅助存储。 */
    public void saveAsync(SyncRecord record) {
        submit(() -> saveBlocking(record));
    }

    /** 同步保存（阻塞当前线程，关服流程使用）。 */
    public void saveBlocking(SyncRecord record) {
        if (record == null) return;
        try {
            primary.save(record);
            debug("已保存记录 " + record.getKey() + " 到 " + primary.getType().getId());
        } catch (Exception e) {
            log.warnT("log.storage.save-failed",
                "保存记录 {0} 到主存储失败: {1}", record.getKey(), e.getMessage());
            return;
        }
        mirror(storage -> storage.save(record), "保存记录 " + record.getKey());
    }

    /** 批量保存（关服 / 同步）。 */
    public void saveAllBlocking(Collection<SyncRecord> records) {
        if (records == null || records.isEmpty()) return;
        try {
            primary.saveAll(records);
            debug("已批量保存 " + records.size() + " 条记录到 " + primary.getType().getId());
        } catch (Exception e) {
            log.warnT("log.storage.save-all-failed", "批量保存到主存储失败: {0}", e.getMessage());
            return;
        }
        mirror(storage -> storage.saveAll(records), "批量保存 " + records.size() + " 条记录");
    }

    /** 异步删除记录。 */
    public void deleteAsync(String key) {
        submit(() -> {
            try {
                primary.delete(key);
                debug("已从 " + primary.getType().getId() + " 删除记录 " + key);
            } catch (Exception e) {
                log.warnT("log.storage.delete-failed",
                "从主存储删除记录 {0} 失败: {1}", key, e.getMessage());
                return;
            }
            mirror(storage -> storage.delete(key), "删除记录 " + key);
        });
    }

    /** 把一次写操作镜像到所有辅助存储。 */
    private void mirror(StorageAction action, String description) {
        if (secondaries.isEmpty() || !isMirrorEnabled()) {
            return;
        }
        Runnable task = () -> {
            for (DataStorage storage : secondaries) {
                if (!storage.isAvailable()) continue;
                try {
                    action.execute(storage);
                } catch (Exception e) {
                    log.warnT("log.storage.mirror-write-failed",
                    "[镜像] {0} 写入 {1} 失败: {2}", description, storage.getType().getId(), e.getMessage());
                }
            }
        };
        if (isMirrorAsync()) {
            submit(task);
        } else {
            task.run();
        }
    }

    // ===== 迁移与同步 =====

    /** 在两个后端之间迁移数据（overwrite=true 先清空目标）。返回迁移记录数。 */
    public int migrate(StorageType from, StorageType to, boolean overwrite) throws Exception {
        DataStorage source = storages.get(from);
        DataStorage target = storages.get(to);
        if (source == null) throw new IllegalStateException(I18n.t("exception.storage.source-not-enabled", "来源后端 {0} 未启用", from.getId()));
        if (target == null) throw new IllegalStateException(I18n.t("exception.storage.target-not-enabled", "目标后端 {0} 未启用", to.getId()));
        Collection<SyncRecord> records = source.loadAll();
        if (overwrite) {
            target.clear();
        }
        target.saveAll(records);
        return records.size();
    }

    /** 把主存储全量数据覆盖同步到所有辅助存储。返回同步记录数。 */
    public int syncToSecondaries() throws Exception {
        if (secondaries.isEmpty()) {
            return 0;
        }
        Collection<SyncRecord> records = primary.loadAll();
        for (DataStorage storage : secondaries) {
            if (!storage.isAvailable()) continue;
            try {
                storage.clear();
                storage.saveAll(records);
            } catch (Exception e) {
                log.warnT("log.storage.sync-failed",
                "同步到 {0} 失败: {1}", storage.getType().getId(), e.getMessage());
            }
        }
        return records.size();
    }

    private void debug(String message) {
        if (LogKit.isDebugEnabled()) {
            log.debug("[存储] " + message);
        }
    }

    /** 可抛异常的存储操作，用于镜像写入。 */
    @FunctionalInterface
    private interface StorageAction {
        void execute(DataStorage storage) throws Exception;
    }
}
