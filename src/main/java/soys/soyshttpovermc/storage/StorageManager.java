package soys.soyshttpovermc.storage;

import soys.soyshttpovermc.log.LogKit;

import org.bukkit.configuration.ConfigurationSection;

/**
 * 存储后端装配器：按 config.yml 的 mysql 段选择并初始化后端。
 * <ul>
 *   <li>{@code mysql.enabled=false}（默认）→ 返回 null（纯内存模式）；</li>
 *   <li>{@code mysql.enabled=true} → 创建 {@link MysqlSyncStorage} 并初始化（建表），
 *       失败时记录明确错误并返回 null（自动降级内存模式，不影响插件运行）。</li>
 * </ul>
 * 新增数据库后端 = 实现 {@link SyncStorage} 后在本类注册一行。
 */
public final class StorageManager {

    private StorageManager() {
    }

    /**
     * 装配存储后端。
     *
     * @param mysqlCfg config.yml 的 mysql 段（可为 null）
     * @return 可用后端；未启用 / 初始化失败返回 null（内存模式）
     */
    public static SyncStorage build(ConfigurationSection mysqlCfg) {
        if (mysqlCfg == null || !mysqlCfg.getBoolean("enabled", false)) {
            return null;
        }
        String url = mysqlCfg.getString("url", "");
        if (url == null || url.trim().isEmpty()) {
            LogKit.warn("[HTTP-Over-MC] mysql.enabled=true 但未配置 url，已降级为内存模式");
            return null;
        }
        String username = mysqlCfg.getString("username", "");
        String password = mysqlCfg.getString("password", "");
        String prefix = mysqlCfg.getString("table-prefix", "mc_shttp_");
        long keepaliveSeconds = mysqlCfg.getLong("keepalive-interval", 1800);

        SyncStorage storage = new MysqlSyncStorage(url.trim(), username, password, prefix);
        try {
            storage.initialize();
            LogKit.info("[HTTP-Over-MC] 存储后端已启用: " + storage.getType().getId()
                    + " 地址=" + storage.describe()
                    + " 表前缀=" + prefix + " keepalive=" + keepaliveSeconds + "s");
            return storage;
        } catch (Throwable t) {
            LogKit.warn("[HTTP-Over-MC] 存储后端初始化失败，已降级为内存模式: " + t);
            try {
                storage.shutdown();
            } catch (Throwable ignored) {
            }
            return null;
        }
    }
}
