package soys.soyshttpovermc.storage;
import soys.soyshttpovermc.enums.StorageType;

/**
 * 跨服同步数据存储抽象（多数据库后端）。
 *
 * <p>当前承载三类跨服一致所需数据（详见「跨服同步数据分析」）：
 * <ul>
 *   <li><b>令牌注销黑名单</b>（{@code token_blacklist}）：jti 全局共享 → 任意子服退出登录，
 *       其它子服立即拒绝该令牌（本服内存 + DB 双查，带命中缓存）；</li>
 *   <li><b>令牌签发审计</b>（{@code token_audit}）：全服审计（/soyshttp tokens 跨服聚合），
 *       append-only，带 server_id 维度防双服写同表冲突；</li>
 *   <li><b>实例心跳</b>（{@code instances}）：各子服注册/心跳，跨服拓扑可见性。</li>
 * </ul>
 *
 * <p>约定（参考 SOYSMyLoot 的 DataStorage）：
 * <ul>
 *   <li>方法可能在异步线程被调用，实现需保证线程安全（内部串行锁）；</li>
 *   <li>失败以异常上报，由调用方/装配方降级（后端不可用 → 内存模式继续运行）；</li>
 *   <li>{@link #isTokenRevoked} 等查询应在热点路径可用（实现带缓存）；</li>
 *   <li>新增数据库只需实现本接口并在 {@link StorageManager#build} 注册。</li>
 * </ul>
 */
public interface SyncStorage {

    /** 后端类型。 */
    StorageType getType();

    /** 初始化连接 / 建表。失败抛异常，调用方标记不可用并降级。 */
    void initialize() throws Exception;

    /** 释放资源（关服/重载时调用）。 */
    void shutdown();

    /** 后端当前是否可用（不可用时调用方走内存模式）。 */
    boolean isAvailable();

    /** 简要描述（数据库地址），供日志/状态展示。 */
    String describe();

    /** 主动保活探测（keepalive-interval 定时任务调用）。 */
    void keepAlive();

    // ===== 令牌注销黑名单（跨服共享）=====

    /** 该 jti 是否已在全局黑名单（本服内存 + DB 联合判定；DB 查询带命中缓存）。 */
    boolean isTokenRevoked(String jti);

    /** 把 jti 加入全局黑名单（幂等；记录来源服）。 */
    void revokeToken(String jti, String serverId);

    // ===== 令牌签发审计（跨服聚合）=====

    /** 登记一次签发（append-only；供 /soyshttp tokens 与审计）。 */
    void recordIssued(String serverId, String subject, String mode, boolean admin,
                      String jti, long issuedAt, long expiresAt);

    // ===== 实例心跳（跨服拓扑）=====

    /** 注册/更新本服心跳（幂等 upsert）。 */
    void heartbeat(String serverId, String name, String host, int port);

    // ===== 统一跨服 JWT 密钥（集中下发）=====

    /**
     * 从共享存储读取全局 JWT 密钥；不存在时用 {@code localSecret}（本地文件生成的密钥）
     * 以「先到先得」写入共享存储并读回——首个接入的服初始化全局密钥，其后各服读同一密钥，
     * 实现集中下发（无需手工复制 token-secret.key）。
     *
     * @param localSecret 本地生成的密钥（本服文件，作为全局密钥的初始种子）
     * @return 全局密钥；存储不可用/失败返回 null（调用方回退本地密钥）
     */
    byte[] loadOrCreateJwtSecret(byte[] localSecret);
}
