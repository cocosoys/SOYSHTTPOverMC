package com.github.cocosoys.mc.soyshttpovermc.storage;
import com.github.cocosoys.mc.soyshttpovermc.enums.StorageType;
import lombok.CustomLog;

import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 跨服同步语义层（实现原 {@link SyncStorage} 接口，数据落于新的多后端主辅存储）：
 * 把令牌黑名单 / 签发审计 / 实例心跳 / 全局密钥 映射为通用 {@link SyncRecord} 读写，
 * 经 {@link StorageManager} 写主存储 + 镜像辅助存储（热备份）。
 *
 * <p>记录 key 约定：
 * <ul>
 *   <li>黑名单：{@code blacklist:&lt;jti&gt;}（type=BLACKLIST，data=JSON{server_id,revoked_at}）</li>
 *   <li>审计：{@code audit:&lt;jti&gt;:&lt;nonce&gt;}（type=AUDIT，append-only，nonce 保证唯一）</li>
 *   <li>心跳：{@code instance:&lt;serverId&gt;}（type=INSTANCE）</li>
 *   <li>密钥：{@code meta:jwt_secret}（type=META，data=base64）</li>
 * </ul>
 * 读操作一律走主存储；黑名单查询带 5s 命中缓存（避免热点路径反复读库/读文件）。
 */
@CustomLog
public class RecordSyncStorage implements SyncStorage {

    private static final String T_BLACKLIST = "BLACKLIST";
    private static final String T_AUDIT = "AUDIT";
    private static final String T_INSTANCE = "INSTANCE";
    private static final String T_META = "META";

    private final StorageManager manager;
    /** 黑名单命中缓存：jti -> 缓存到期时间（仅缓存「已注销」肯定结果）。 */
    private final Map<String, Long> revokedCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 5_000;

    public RecordSyncStorage(StorageManager manager) {
        this.manager = manager;
    }

    @Override
    public StorageType getType() {
        return manager.getPrimary() == null ? StorageType.YAML : manager.getPrimary().getType();
    }

    @Override
    public void initialize() {
        // 已由 StorageManager.initialize 完成
    }

    @Override
    public void shutdown() {
        revokedCache.clear();
    }

    @Override
    public boolean isAvailable() {
        return manager.getPrimary() != null;
    }

    @Override
    public String describe() {
        return manager.getPrimary() == null ? "无后端" : manager.getPrimary().describe();
    }

    @Override
    public void keepAlive() {
        // keepAlive 由 StorageManager 内部定时任务负责（SQL 后端）
    }

    // ===== 令牌注销黑名单 =====

    @Override
    public boolean isTokenRevoked(String jti) {
        if (jti == null || jti.isEmpty()) return false;
        Long until = revokedCache.get(jti);
        if (until != null && System.currentTimeMillis() < until) {
            return true;
        }
        try {
            SyncRecord r = manager.load("blacklist:" + jti);
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
        if (jti == null || jti.isEmpty()) return;
        String data = "{\"server_id\":\"" + jsonEsc(serverId) + "\",\"revoked_at\":" + System.currentTimeMillis() + "}";
        manager.saveAsync(new SyncRecord("blacklist:" + jti, T_BLACKLIST, data));
        revokedCache.put(jti, System.currentTimeMillis() + CACHE_TTL_MS);
    }

    // ===== 令牌签发审计 =====

    @Override
    public void recordIssued(String serverId, String subject, String mode, boolean admin,
                             String jti, long issuedAt, long expiresAt) {
        String data = "{\"server_id\":\"" + jsonEsc(serverId) + "\",\"subject\":\"" + jsonEsc(subject)
                + "\",\"mode\":\"" + jsonEsc(mode) + "\",\"admin\":" + (admin ? 1 : 0)
                + ",\"jti\":\"" + jsonEsc(jti) + "\",\"issued_at\":" + issuedAt
                + ",\"expires_at\":" + expiresAt + "}";
        // append-only：nonce 保证 key 唯一（同 jti 多次签发/升级不互相覆盖）
        String key = "audit:" + jti + ":" + Long.toHexString(System.nanoTime());
        manager.saveAsync(new SyncRecord(key, T_AUDIT, data));
    }

    // ===== 实例心跳 =====

    @Override
    public void heartbeat(String serverId, String name, String host, int port) {
        String data = "{\"name\":\"" + jsonEsc(name) + "\",\"host\":\"" + jsonEsc(host)
                + "\",\"port\":" + port + ",\"last_heartbeat\":" + System.currentTimeMillis() + "}";
        manager.saveAsync(new SyncRecord("instance:" + serverId, T_INSTANCE, data));
    }

    // ===== 统一跨服 JWT 密钥（集中下发） =====

    @Override
    public byte[] loadOrCreateJwtSecret(byte[] localSecret) {
        try {
            SyncRecord r = manager.load("meta:jwt_secret");
            if (r != null && r.getData() != null) {
                byte[] b = decodeB64(r.getData());
                if (b != null && b.length >= 16) return b;
            }
            if (localSecret == null || localSecret.length == 0) return null;
            String b64 = Base64.getEncoder().encodeToString(localSecret);
            manager.saveBlocking(new SyncRecord("meta:jwt_secret", T_META, b64));
            // 读回（可能被并发首启的其它服抢先）
            SyncRecord r2 = manager.load("meta:jwt_secret");
            byte[] b2 = r2 == null ? null : decodeB64(r2.getData());
            if (b2 != null && b2.length >= 16) return b2;
            return localSecret;
        } catch (Exception e) {
            log.warnT("log.storage.jwt-secret-failed",
                    "全局 JWT 密钥读写失败，回退本地密钥: {0}", e.getMessage());
            return null;
        }
    }

    private static byte[] decodeB64(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return Base64.getDecoder().decode(s.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String jsonEsc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
