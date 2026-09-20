package com.github.cocosoys.mc.soyshttpovermc.storage;

import com.github.cocosoys.mc.soyshttpovermc.enums.StorageType;
import com.github.cocosoys.mc.soyshttpovermc.enums.SoysRecordType;
import com.github.cocosoys.mc.soyshttpovermc.orm.DATA;
import com.github.cocosoys.mc.soyshttpovermc.orm.SqlPojo;
import com.github.cocosoys.mc.soyshttpovermc.orm.YAML;
import com.github.cocosoys.mc.soyshttpovermc.orm.YamlPojo;
import com.github.cocosoys.mc.soyshttpovermc.orm.executor.SqlBackendExecutor;
import com.github.cocosoys.mc.soyshttpovermc.spi.ConfigSection;
import com.github.cocosoys.mc.soyshttpovermc.spi.Platforms;
import com.github.cocosoys.mc.soyshttpovermc.spring.entity.SoysRecord;
import com.github.cocosoys.mc.soyshttpovermc.util.JsonWriter;
import lombok.CustomLog;

import java.io.File;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 跨服同步语义层（{@link SyncStorage} 的 ORM 实现）：把令牌黑名单 / 签发审计 /
 * 实例心跳 / 全局 JWT 密钥 映射为通用实体 {@link SoysRecord}（表 {@code soys_records}），
 * 经 {@link DATA} 门面路由 SQL（mysql &gt; sqlite）或 YAML 后端持久化。
 *
 * <p>记录 key 约定：
 * <ul>
 *   <li>黑名单：{@code blacklist:&lt;jti&gt;}（type=BLACKLIST，data=JSON{server_id,revoked_at}）</li>
 *   <li>审计：{@code audit:&lt;jti&gt;:&lt;nonce&gt;}（type=AUDIT，append-only，nonce 保证唯一）</li>
 *   <li>心跳：{@code instance:&lt;serverId&gt;}（type=INSTANCE）</li>
 *   <li>密钥：{@code meta:jwt_secret}（type=META，data=base64）</li>
 * </ul>
 * 黑名单查询带 5s 命中缓存（避免热点路径反复查库 / 全量扫描 YAML）。
 *
 * <p>旧数据自动迁移（{@link #initialize()}）：YAML 旧文件 {@code records.yml}（根节点 records）
 * 与 SQL 旧表 {@code mc_shttp_records} / {@code mc_shttp_soys_records} 均为 KV 旧布局，
 * 启动时幂等迁移到 ORM 布局（YAML 键名一致零转换；SQL REPLACE SELECT + DROP）。</p>
 */
@CustomLog
public class RecordSyncStorage implements SyncStorage {






    /**
     * 黑名单命中缓存：jti -&gt; 缓存到期时间（仅缓存「已注销」肯定结果）。
     */
    private final Map<String, Long> revokedCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 5_000;

    public RecordSyncStorage() {
    }

    @Override
    public StorageType getType() {
        return DATA.sqlEnabled() ? StorageType.fromId(SqlBackendExecutor.get().name()) : StorageType.YAML;
    }

    @Override
    public void initialize() {
        migrateLegacyYaml();
        migrateLegacySql();
    }

    @Override
    public void shutdown() {
        revokedCache.clear();
    }

    @Override
    public boolean isAvailable() {
        return DATA.sqlEnabled() || YAML.Pojo.isAvailable();
    }

    @Override
    public String describe() {
        return DATA.sqlEnabled()
                ? "ORM(SQL:" + SqlBackendExecutor.get().name() + ")"
                : "ORM(YAML:" + YAML.Pojo.getDataDir() + ")";
    }

    // ===== 旧数据迁移（幂等；KV 旧布局 → ORM 布局） =====

    /**
     * YAML：旧 {@code records.yml}（根节点 records）→ {@code soys_records.yml}（根节点 soys_records）。
     * 两布局子键（key 下的 type/data/updated_at）一致，仅根节点名变化。
     */
    private void migrateLegacyYaml() {
        File dir = YAML.Pojo.getDataDir();
        if (dir == null) {
            return;
        }
        File oldFile = new File(dir, "records.yml");
        File newFile = new File(dir, "soys_records.yml");
        if (!oldFile.isFile() || newFile.exists()) {
            return;
        }
        try {
            ConfigSection oldCfg = Platforms.getOrNull().loadYaml(oldFile);
            if (oldCfg == null || !oldCfg.isSection("records")) {
                return;
            }
            ConfigSection newCfg = Platforms.getOrNull().createYaml();
            copySection(oldCfg.getSection("records"), newCfg.createSection("soys_records"));
            Platforms.getOrNull().saveYaml(newCfg, newFile);
            if (!oldFile.delete()) {
                oldFile.deleteOnExit();
            }
            log.infoT("log.storage.legacy-migrated", "已自动迁移旧 KV 记录文件 records.yml → soys_records.yml");
        } catch (Exception e) {
            log.warnT("log.storage.legacy-migrate-failed", "旧 KV 记录文件迁移失败: {0}", e.getMessage());
        }
    }

    private static void copySection(ConfigSection src, ConfigSection dst) {
        if (src == null || dst == null) {
            return;
        }
        for (String key : src.getKeys(false)) {
            if (src.isSection(key)) {
                copySection(src.getSection(key), dst.createSection(key));
            } else {
                dst.set(key, src.get(key));
            }
        }
    }

    /**
     * SQL：旧 KV 表 {@code mc_shttp_records} / {@code mc_shttp_soys_records}（带前缀、无审计列）
     * → 新实体表 {@code soys_records}（SqlBackendExecutor 自动建表 + 补列）。
     */
    private void migrateLegacySql() {
        SqlBackendExecutor sql = SqlBackendExecutor.get();
        if (sql == null || !sql.isAvailable()) {
            return;
        }
        try {
            sql.ensureTable(SoysRecord.class);
        } catch (Throwable ignored) {
            // 建表失败由后续 ORM 操作兜底
        }
        for (String old : new String[]{"mc_shttp_soys_records", "mc_shttp_records"}) {
            if (!sql.tableExists(old)) {
                continue;
            }
            try {
                sql.execSql("REPLACE INTO `soys_records` (`key`,`type`,`data`,`updated_at`) "
                        + "SELECT `key`,`type`,`data`,`updated_at` FROM `" + old + "`");
                sql.execSql("DROP TABLE IF EXISTS `" + old + "`");
                log.infoT("log.storage.sql-legacy-migrated", "已自动迁移 SQL 旧表 {0} → soys_records", old);
            } catch (Throwable t) {
                log.warnT("log.storage.sql-legacy-migrate-failed",
                        "SQL 旧表 {0} 迁移失败（跳过，保留旧表）: {1}", old, t.getMessage());
            }
        }
    }

    // ===== 令牌注销黑名单 =====

    @Override
    public boolean isTokenRevoked(String jti) {
        if (jti == null || jti.isEmpty()) {
            return false;
        }
        Long until = revokedCache.get(jti);
        if (until != null && System.currentTimeMillis() < until) {
            return true;
        }
        try {
            SoysRecord r = DATA.get(SoysRecord.class, "blacklist:" + jti);
            if (r != null) {
                revokedCache.put(jti, System.currentTimeMillis() + CACHE_TTL_MS);
                return true;
            }
        } catch (Exception e) {
            log.warnT("log.storage.blacklist-query-failed", "黑名单查询失败: {0}", e.getMessage());
        }
        return false;
    }

    @Override
    public void revokeToken(String jti, String serverId) {
        if (jti == null || jti.isEmpty()) {
            return;
        }
        java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("server_id", serverId == null ? "" : serverId);
        m.put("revoked_at", System.currentTimeMillis());
        String data = JsonWriter.write(m);
        upsert(new SoysRecord("blacklist:" + jti, SoysRecordType.BLACKLIST.code(), data));
        revokedCache.put(jti, System.currentTimeMillis() + CACHE_TTL_MS);
    }

    // ===== 令牌签发审计 =====

    @Override
    public void recordIssued(String serverId, String subject, String mode, boolean admin,
                             String jti, long issuedAt, long expiresAt) {
        java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("server_id", serverId == null ? "" : serverId);
        m.put("subject", subject == null ? "" : subject);
        m.put("mode", mode == null ? "" : mode);
        m.put("admin", admin ? Integer.valueOf(1) : Integer.valueOf(0));
        m.put("jti", jti == null ? "" : jti);
        m.put("issued_at", issuedAt);
        m.put("expires_at", expiresAt);
        String data = JsonWriter.write(m);
        // append-only：nonce 保证 key 唯一（同 jti 多次签发/升级不互相覆盖）
        String key = "audit:" + jti + ":" + Long.toHexString(System.nanoTime());
        DATA.insert(new SoysRecord(key, SoysRecordType.AUDIT.code(), data));
    }

    // ===== 实例心跳 =====

    @Override
    public void heartbeat(String serverId, String name, String host, int port) {
        java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("name", name == null ? "" : name);
        m.put("host", host == null ? "" : host);
        m.put("port", Integer.valueOf(port));
        m.put("last_heartbeat", System.currentTimeMillis());
        String data = JsonWriter.write(m);
        upsert(new SoysRecord("instance:" + serverId, SoysRecordType.INSTANCE.code(), data));
    }

    // ===== 统一跨服 JWT 密钥（集中下发） =====

    @Override
    public byte[] loadOrCreateJwtSecret(byte[] localSecret) {
        try {
            SoysRecord r = DATA.get(SoysRecord.class, "meta:jwt_secret");
            if (r != null && r.getData() != null) {
                byte[] b = decodeB64(r.getData());
                if (b != null && b.length >= 16) {
                    return b;
                }
            }
            if (localSecret == null || localSecret.length == 0) {
                return null;
            }
            String b64 = Base64.getEncoder().encodeToString(localSecret);
            upsert(new SoysRecord("meta:jwt_secret", SoysRecordType.META.code(), b64));
            // 读回（可能被并发首启的其它服抢先）
            SoysRecord r2 = DATA.get(SoysRecord.class, "meta:jwt_secret");
            byte[] b2 = r2 == null ? null : decodeB64(r2.getData());
            if (b2 != null && b2.length >= 16) {
                return b2;
            }
            return localSecret;
        } catch (Exception e) {
            log.warnT("log.storage.jwt-secret-failed",
                    "全局 JWT 密钥读写失败，回退本地密钥: {0}", e.getMessage());
            return null;
        }
    }

    private static byte[] decodeB64(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        try {
            return Base64.getDecoder().decode(s.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * upsert（SQL 端 REPLACE / YAML 端覆盖均为幂等）；insert 失败回退 updateById。
     */
    private static boolean upsert(SoysRecord r) {
        if (DATA.insert(r)) {
            return true;
        }
        return DATA.updateById(r);
    }
}
