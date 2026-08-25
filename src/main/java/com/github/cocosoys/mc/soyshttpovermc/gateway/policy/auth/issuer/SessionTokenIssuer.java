package com.github.cocosoys.mc.soyshttpovermc.gateway.policy.auth.issuer;
import com.github.cocosoys.mc.soyshttpovermc.storage.SyncStorage;
import com.github.cocosoys.mc.soyshttpovermc.enums.LoginMode;

import org.bukkit.configuration.ConfigurationSection;
import com.github.cocosoys.mc.soyshttpovermc.gateway.policy.auth.util.AuthUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 会话令牌颁发器（{@link CredentialIssuer} 参考实现）。
 *
 * <p>令牌以 <b>JWT（HS256）</b> 生成（{@link JwtCodec}），payload 携带：
 * <ul>
 *   <li>{@code sub} —— 绑定主体（玩家名）；</li>
 *   <li>{@code mode} —— 登录模式（ONLINE / OFFLINE，离线专属 cookie 标签与升级判定）；</li>
 *   <li>{@code exp / iat} —— 过期 / 签发时间；</li>
 *   <li>{@code jti} —— 令牌唯一 ID（退出登录黑名单）。</li>
 * </ul>
 * 同一 token 可作 X-API-Key / Authorization Bearer / Cookie(soys_session) 三种形态下发。
 *
 * <p><b>无状态 + 黑名单</b>：校验/解析不查内存表（reload 后旧令牌仍有效）；
 * {@link #revoke}（退出登录）把 jti 加入内存黑名单。密钥由外部注入并持久化
 * （见 {@code ConfigManager.loadOrCreateTokenSecret}，reload 复用同一密钥）。
 *
 * <p><b>颁发登记（审计）</b>：每次签发（登录 / 服主 key / 离线升级换发）都会登记一条
 * {@link IssuedRecord}（jti/mode/admin/签发时间/过期时间），供 {@code /soyshttp tokens}
 * 查询与审计；JWT 本身仍无状态，登记表仅作展示，不参与校验。
 */
public class SessionTokenIssuer extends CredentialIssuer {

    private String cookieName = "soys_session";
    private long ttlMillis = 24L * 3600 * 1000;
    /** JWT HMAC 密钥（外部注入，持久化于 data/token-secret.key）。 */
    private volatile byte[] secret = new byte[0];
    /** 已注销令牌的 jti 黑名单（退出登录后即使 JWT 未过期也不可用；reload/重启清空）。 */
    private final Set<String> revoked = ConcurrentHashMap.newKeySet();
    /** 颁发登记表（subject → 签发记录；仅审计展示用，惰性清理过期记录）。 */
    private final Map<String, List<IssuedRecord>> issued = new ConcurrentHashMap<>();
    /** 跨服同步存储（MySQL 等；null=纯内存模式）：黑名单/审计双写+查询。 */
    private volatile SyncStorage syncStorage = null;
    /** 本服标识（写库记录来源服，防双服冲突；由宿主注入）。 */
    private volatile String serverId = "";
    /** 时钟容差（毫秒）：跨服校验容忍各服时钟偏移（gateway/issuers/session-token.yml clock-skew-seconds，默认 30s）。 */
    private volatile long clockSkewMillis = 30_000;

    /** 注入跨服同步存储（宿主装配；null=内存模式）。 */
    public void setSyncStorage(SyncStorage storage) {
        this.syncStorage = storage;
    }

    /** 注入本服标识（群组服=server-name，独立服=standalone-&lt;host&gt;:&lt;port&gt;）。 */
    public void setServerId(String id) {
        this.serverId = id == null ? "" : id;
    }

    @Override
    public String name() {
        return "session-token";
    }

    @Override
    public void reload(ConfigurationSection cfg) {
        super.reload(cfg);
        if (cfg == null) return;
        cookieName = cfg.getString("cookie-name", "soys_session");
        ttlMillis = Math.max(1000L, cfg.getLong("ttl-seconds", 86400) * 1000L);
        clockSkewMillis = Math.max(0, cfg.getLong("clock-skew-seconds", 30) * 1000L);
        revoked.clear(); // JWT 无状态：reload 不清空有效令牌；黑名单随重建清空（可接受）
        issued.clear(); // 颁发登记随重建清空（新颁发器实例从头登记）
    }

    /** 注入 JWT 签名密钥（由主类从持久化文件加载；reload 复用同一密钥 → 旧令牌不失效）。 */
    public void setSecret(byte[] secret) {
        this.secret = secret == null ? new byte[0] : secret;
    }

    @Override
    public IssuedCredential issue(String subject) {
        return issue(subject, LoginMode.ONLINE);
    }

    /** 按指定登录模式签发会话令牌（offline=离线模式专属 cookie，online=正常在线令牌）。 */
    public IssuedCredential issue(String subject, LoginMode mode) {
        return IssuedCredential.ofToken(issueToken(subject, mode), cookieName);
    }

    /**
     * 签发携带自定义 claims 的会话令牌（权限范围/标签等业务声明，写入 JWT payload）。
     * 键限 [a-zA-Z0-9_-]{1,32}，值限 256 字符；保留键 sub/mode/exp/iat/jti/adm 不可用。
     * claims 供业务层经 {@code JwtCodec.Payload#claims} 读取，不参与权限判定。
     */
    public IssuedCredential issue(String subject, LoginMode mode, java.util.Map<String, String> claims) {
        return IssuedCredential.ofToken(issueToken(subject, mode, claims), cookieName);
    }

    /** 签发并返回 JWT 令牌字符串（供登录桥登记/换发使用）。 */
    public String issueToken(String subject, LoginMode mode) {
        return issueToken(subject, mode, null);
    }

    /** 签发并返回 JWT 令牌字符串（支持自定义 claims）。 */
    public String issueToken(String subject, LoginMode mode, java.util.Map<String, String> claims) {
        String jti = AuthUtils.generateToken("", 10);
        String modeName = mode == null ? LoginMode.ONLINE.name() : mode.name();
        record(subject, modeName, false, jti);
        return JwtCodec.create(secret, subject, modeName, ttlMillis, jti, "st_", false, claims);
    }

    /**
     * 签发<b>服主最高权限 key</b>（仅 /soyshttp key 命令调用）：adm 标记 + ak_ 前缀，
     * 权限判定层（PlayerPermissionService）对其直接放行（免权限访问全部 API）。
     * 其它入口（登录签发 / 门面 issueCredential）一律签发普通 st_ 令牌（玩家权限镜像），
     * 无法获得最高权限——最高权限 key 只能由服主经命令手动颁发。
     */
    public IssuedCredential issueAdminKey(String subject) {
        String jti = AuthUtils.generateToken("", 10);
        record(subject, LoginMode.ONLINE.name(), true, jti);
        String token = JwtCodec.create(secret, subject, LoginMode.ONLINE.name(),
                ttlMillis, jti, "ak_", true);
        return IssuedCredential.ofToken(token, cookieName);
    }

    @Override
    public boolean validate(CredentialPresentation p) {
        return parse(p) != null;
    }

    /** 解析请求凭证为 JWT payload（验签 + 过期 + 黑名单）；无效返回 null。 */
    private JwtCodec.Payload parse(CredentialPresentation p) {
        String token = tokenOf(p);
        return token == null ? null : parseToken(token);
    }

    /** 解析令牌字符串（验签 + 过期[含时钟容差] + 黑名单）；无效返回 null。支持 st_（玩家）与 ak_（服主 admin）前缀。 */
    private JwtCodec.Payload parseToken(String token) {
        if (token == null) return null;
        JwtCodec.Payload payload = token.startsWith("ak_")
                ? JwtCodec.parse(secret, token, "ak_", clockSkewMillis)
                : JwtCodec.parse(secret, token, "st_", clockSkewMillis);
        if (payload == null) return null;
        if (payload.jti != null && isRevoked(payload.jti)) return null;
        return payload;
    }

    /** 本服内存黑名单 + 跨服共享黑名单（存储后端）联合判定；存储查询带命中缓存。 */
    private boolean isRevoked(String jti) {
        if (revoked.contains(jti)) return true;
        SyncStorage s = syncStorage;
        return s != null && s.isAvailable() && s.isTokenRevoked(jti);
    }

    /** 是否为服主最高权限 key（adm 标记；无效/过期/已注销返回 false）。 */
    public boolean isAdminToken(String token) {
        JwtCodec.Payload payload = parseToken(token);
        return payload != null && payload.adm;
    }

    /** 请求凭证是否为服主最高权限 key（无效/过期/已注销返回 false）。 */
    public boolean isAdmin(CredentialPresentation p) {
        String token = tokenOf(p);
        return token != null && isAdminToken(token);
    }

    /** 从凭证表示提取本颁发器可识别的令牌（Bearer / X-API-Key / Cookie(soys_session) 任一），供校验与权限映射复用。 */
    protected String tokenOf(CredentialPresentation p) {
        if (p == null) return null;
        String token = p.getBearer();
        if (token == null || token.isEmpty()) token = p.getApiKey();
        if (token == null || token.isEmpty()) token = p.getCookie(cookieName);
        return (token == null || token.isEmpty()) ? null : token;
    }

    @Override
    public String subjectOf(CredentialPresentation p) {
        JwtCodec.Payload payload = parse(p);
        return payload == null ? null : payload.subject;
    }

    /** 查询指定令牌对应的登录模式（无效/过期/已注销返回 null）。 */
    public LoginMode modeOfToken(String token) {
        JwtCodec.Payload payload = parseToken(token);
        if (payload == null || payload.mode == null) return null;
        try {
            return LoginMode.valueOf(payload.mode);
        } catch (Exception e) {
            return null;
        }
    }

    /** 查询请求凭证对应令牌的登录模式（无效/过期返回 null）。 */
    public LoginMode modeOf(CredentialPresentation p) {
        String token = tokenOf(p);
        return token == null ? null : modeOfToken(token);
    }

    /**
     * 玩家进游戏登录成功后，把其名下现存令牌升级为在线模式（JWT 无状态无法原地改 payload，
     * 由登录桥负责：黑名单旧离线令牌 + 签发新在线令牌；离线 cookie 换发后语义自动补全为在线）。
     * 返回被换发的令牌数（0=该玩家尚无离线令牌，此时由调用方正常签发新令牌）。
     */
    public int upgradePlayerToOnline(String player) {
        if (player == null) return 0;
        return 0; // 无状态 JWT 无法枚举玩家令牌；换发逻辑在 AuthLoginBridge.upgradePlayerToOnline
    }

    /** 会话令牌 TTL（秒），供 Set-Cookie 的 Max-Age 使用。 */
    public long getTtlSeconds() {
        return ttlMillis / 1000L;
    }

    /** Cookie 名称（默认 soys_session），供 Set-Cookie 头构造使用。 */
    public String getCookieName() {
        return cookieName;
    }

    /** 按令牌字符串撤销会话（退出登录用）：jti 加入黑名单（本地 + 跨服存储）；撤销成功返回 true。 */
    public boolean revoke(String token) {
        JwtCodec.Payload payload = parseToken(token);
        if (payload == null || payload.jti == null) return false;
        revoked.add(payload.jti);
        markRevoked(payload.jti);
        SyncStorage s = syncStorage;
        if (s != null && s.isAvailable()) {
            try {
                s.revokeToken(payload.jti, serverId);
            } catch (Throwable ignored) {
                // 存储失败降级：本地黑名单仍生效（本服拒绝），跨服同步延迟
            }
        }
        return true;
    }

    /** 按请求携带的凭证撤销会话（退出登录用）；提取不到令牌或不存在返回 false。 */
    public boolean revoke(CredentialPresentation p) {
        String token = tokenOf(p);
        return token != null && revoke(token);
    }

    /** 查询令牌是否仍有效（供调试/门面展示）。 */
    public boolean isValidToken(String token) {
        return parseToken(token) != null;
    }

    // ===== 颁发登记（审计，供 /soyshttp tokens 查询） =====

    /** 一次签发的登记记录（不含令牌本体，仅审计元数据；JWT 校验不依赖它）。 */
    public static final class IssuedRecord {
        public final String subject;
        public final String mode;
        public final boolean admin;
        public final long issuedAt;
        public final long expiresAt;
        public final String jti;
        public volatile boolean revoked;

        IssuedRecord(String subject, String mode, boolean admin, long issuedAt, long expiresAt, String jti) {
            this.subject = subject;
            this.mode = mode;
            this.admin = admin;
            this.issuedAt = issuedAt;
            this.expiresAt = expiresAt;
            this.jti = jti;
        }

        /** 展示用状态：已注销 / 已过期 / 有效。 */
        public String status() {
            if (revoked) return "已注销";
            if (System.currentTimeMillis() > expiresAt) return "已过期";
            return "有效";
        }
    }

    private void record(String subject, String mode, boolean admin, String jti) {
        if (subject == null) subject = "?";
        long now = System.currentTimeMillis();
        issued.computeIfAbsent(subject, k -> new CopyOnWriteArrayList<>())
                .add(new IssuedRecord(subject, mode, admin, now, now + ttlMillis, jti));
        // 跨服审计：同步写库（append-only；失败降级不影响签发）
        SyncStorage s = syncStorage;
        if (s != null && s.isAvailable()) {
            try {
                s.recordIssued(serverId, subject, mode, admin, jti, now, now + ttlMillis);
            } catch (Throwable ignored) {
            }
        }
    }

    private void markRevoked(String jti) {
        if (jti == null) return;
        for (List<IssuedRecord> list : issued.values()) {
            for (IssuedRecord r : list) {
                if (jti.equals(r.jti)) r.revoked = true;
            }
        }
    }

    /** 全部签发记录快照（按签发时间倒序；惰性清理过期记录）。 */
    public List<IssuedRecord> listIssued() {
        List<IssuedRecord> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, List<IssuedRecord>>> it = issued.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, List<IssuedRecord>> e = it.next();
            List<IssuedRecord> list = e.getValue();
            list.removeIf(r -> !r.revoked && r.expiresAt < now); // 惰性清理已过期未注销记录
            out.addAll(list);
            if (list.isEmpty()) it.remove();
        }
        out.sort((a, b) -> Long.compare(b.issuedAt, a.issuedAt));
        return out;
    }
}
