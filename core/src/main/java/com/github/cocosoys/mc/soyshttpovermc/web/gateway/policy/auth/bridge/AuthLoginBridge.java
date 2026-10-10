package com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.bridge;

import com.github.cocosoys.mc.soyshttpovermc.enums.LoginMode;
import com.github.cocosoys.mc.soyshttpovermc.orm.DATA;
import com.github.cocosoys.mc.soyshttpovermc.spring.entity.SoysSsoTicket;
import com.github.cocosoys.mc.soyshttpovermc.util.AjaxResult;
import com.github.cocosoys.mc.soyshttpovermc.util.ApiResponse;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.bridge.spi.LoginProvider;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.issuer.CredentialPresentation;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.issuer.JwtCodec;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.issuer.SessionTokenIssuer;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.login.DefaultLoginModePolicy;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.login.LoginModePolicy;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.util.AuthUtils;
import lombok.CustomLog;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 网页登录桥（登录插件无关，密码校验由 {@link LoginProvider} SPI 提供，如 AuthMe）：
 * <ul>
 *   <li>玩家游戏内登录成功时，登录插件提供者（AuthMeLoginProvider 等）调用 {@link #storeToken}
 *       登记 players→token，并 {@link #mintTicket} 生成一次性登录票据；</li>
 *   <li>浏览器访问 {@link #serveLoginPage}（/api/auth/login?ticket=...）→ 302 到前端登录页；</li>
 *   <li>浏览器提交密码（POST /api/auth/issue），{@link #issue} 经登录插件提供者验证密码，
 *       验证通过后将已登记的会话令牌以 {@code Set-Cookie} 交给浏览器（仅此一次暴露令牌）。</li>
 * </ul>
 * 设计：即使登录链接被截获，攻击者也必须知道该玩家的登录密码才能换取 Cookie；令牌仅在密码
 * 验证通过后才下发。票据一次性（使用即销毁）。
 *
 * <p>本类已下沉为 spring 层内部组件：{@code AuthServiceImpl} 持有并调用，三个票据登录方法
 * （serveLoginPage / issue / issueByUsername）返回 {@link ApiResponse}（注解式 API 响应控制），
 * 经 {@code AuthController} 端点透传给网关组装为真实 HTTP 帧；不再被 Web 前端处理器直接路由。</p>
 *
 * <p>登录模式（{@link LoginModePolicy}）：玩家在线 → 签发 ONLINE 令牌（完整镜像权限）；
 * 玩家不在线 → 默认允许 OFFLINE 离线模式登录（离线专属 cookie），玩家进游戏登录后自动升级为 ONLINE。</p>
 */
@CustomLog
public class AuthLoginBridge {

    private final SessionTokenIssuer issuer;
    /**
     * 登录插件提供者（AuthMe 等，负责纯账号密码校验）；null=未接入登录插件。
     */
    private volatile LoginProvider loginProvider;
    /**
     * 登录模式策略（决定在线/离线签发）；默认允许离线登录。
     */
    private volatile LoginModePolicy loginModePolicy = new DefaultLoginModePolicy();

    /**
     * 本服标识（群组服=server-name；独立服=standalone-&lt;host&gt;:&lt;port&gt;），票据签发审计；
     * 由 {@link HttpOverMcPluginProxy} 装配后注入。
     */
    private volatile String serverId = "unknown";
    /**
     * 玩家名 → 已签发的会话令牌（LoginEvent 时登记）。
     */
    private final Map<String, String> playerTokens = new ConcurrentHashMap<>();
    /**
     * 玩家名 → 网页端登录 IP（网页登录成功时登记，用于 IP 匹配免登录）。
     */
    private final Map<String, String> webLoginIps = new ConcurrentHashMap<>();
    /**
     * 玩家名 → 游戏端登录 IP（AuthMe LoginEvent 时登记，用于 IP 匹配免登录）。
     */
    private final Map<String, String> gameLoginIps = new ConcurrentHashMap<>();

    // ===== 自动登录（记住我 / IP 匹配）配置 =====

    /**
     * 记住我（设备免登录）总开关（config.yml auto.login.ttl.enable，默认 true）。
     */
    private volatile boolean rememberEnabled = true;
    /**
     * 记住我凭证有效期（毫秒，config.yml auto.login.ttl.activetime 天，默认 7 天）。
     */
    private volatile long rememberTtlMillis = 7L * 24 * 3600 * 1000;
    /**
     * 旧“IP 匹配免登录”开关（config.yml auto.login.ip.enabled，默认 false）。
     * true=保留：游戏在线且网页 IP == 游戏 IP 时自动登录（共享出口 IP 下不精准，默认关闭）。
     */
    private volatile boolean ipEnabled = false;
    /**
     * 设备指纹双因子开关（auth.yml auto.login.fp.enable，默认 false）：
     * true=自动登录（记住我）时校验请求 X-Device-Fingerprint 与绑定表一致。
     */
    private volatile boolean fpEnabled = false;
    /**
     * 指纹严格模式（auth.yml auto.login.fp.strict，默认 true）：
     * true=指纹不一致/未携带 → 拒绝自动登录；false=不一致仅告警放行（宽松）。
     */
    private volatile boolean fpStrict = true;
    /**
     * 游戏端→网页端绑定票据有效期（毫秒，auth.yml auto.login.ticket.ttl 秒，默认 60s）。
     */
    private volatile long ticketTtlMillis = 60_000L;
    /**
     * 游戏内登录后是否发送可点击票据链接（auth.yml auto.login.ticket.in-game-link，默认 true）。
     */
    private volatile boolean ticketLinkEnabled = true;
    /**
     * 记住我 Cookie 名称（HttpOnly；与 session Cookie 独立）。
     */
    private static final String REMEMBER_COOKIE = "soys_remember";
    /**
     * 设备指纹请求头名称（前端采集后随请求携带）。
     */
    public static final String FP_HEADER = "X-Device-Fingerprint";
    /**
     * 指纹哈希服务端盐（指纹非秘密凭据，仅一致性弱校验；固定盐避免引入额外配置）。
     */
    private static final String FP_SALT = "soys-device-fp-v1";

    public AuthLoginBridge(SessionTokenIssuer issuer) {
        this.issuer = issuer;
    }

    /**
     * 注入本服标识（群组服=server-name；独立服=standalone-&lt;host&gt;:&lt;port&gt;），
     * 票据签发审计字段；由 {@link HttpOverMcPluginProxy} 装配后调用。
     */
    public void setServerId(String serverId) {
        if (serverId != null && !serverId.isEmpty()) {
            this.serverId = serverId;
        }
    }

    /**
     * 绑定登录插件提供者（bridge 创建/重建后由网关调用；幂等）。
     */
    public void setLoginProvider(LoginProvider provider) {
        this.loginProvider = provider;
    }

    /**
     * 当前登录插件提供者（null=未接入）。
     */
    public LoginProvider getLoginProvider() {
        return loginProvider;
    }

    /**
     * 替换登录模式策略（服务端可定制离线登录许可 / 模式判定）。
     */
    public void setLoginModePolicy(LoginModePolicy policy) {
        this.loginModePolicy = policy == null ? new DefaultLoginModePolicy() : policy;
    }

    /**
     * 当前登录模式策略。
     */
    public LoginModePolicy getLoginModePolicy() {
        return loginModePolicy;
    }

    /**
     * 生成一次性登录票据（返回给玩家点开的链接）：登记玩家 + 记录过期时刻（TTL=auto.login.ticket.ttl，
     * 默认 60s），落 ORM 实体 {@link SoysSsoTicket}（SQL/YAML 双后端；群组服接同一 MySQL 时
     * 票据全局可消费，跨域名 SSO 回跳链复用本池）。消费/查看时校验，超时自动失效。
     */
    public String mintTicket(String player) {
        return mintTicket(player, null, null);
    }

    /**
     * 生成一次性登录票据（跨域名 SSO 回跳版）：除 {@link #mintTicket(String)} 语义外，
     * 额外记录目标回跳地址与客户端 IP（可选绑定校验）。
     *
     * @param redirectUrl 目标回跳地址（null=游戏内链接场景）
     * @param clientIp    客户端 IP（null=不绑定）
     */
    public String mintTicket(String player, String redirectUrl, String clientIp) {
        String ticket = AuthUtils.generateToken("tk_", 16);
        long now = System.currentTimeMillis();
        SoysSsoTicket row = new SoysSsoTicket();
        row.setTicket(ticket);
        row.setSubject(player);
        row.setRedirectUrl(redirectUrl);
        row.setIssuedServer(serverId);
        row.setClientIp(clientIp);
        row.setConsumedAt(null);
        row.setExpiresAt(now + ticketTtlMillis);
        row.setCreateTime(new Date(now));
        row.setUpdateTime(new Date(now));
        try {
            DATA.insert(row);
        } catch (Exception ex) {
            // 存储不可用（双后端均异常）时退化为无效票据——不静默成功，调用方按 null 处理
            log.warnT("log.auth.ticket-mint-fail", "SSO 票据签发失败: {0}", ex.getMessage());
            return null;
        }
        lazyCleanExpiredTickets(now);
        return ticket;
    }

    /**
     * 查看票据对应玩家（不消费；校验存在、未消费且未过期）。无效/已过期 → null。
     * 供 {@link #serveLoginPage} 二次验证页判定票据有效性（登录页打开不销毁，保留一次性语义）。
     */
    public String peekTicket(String ticket) {
        SoysSsoTicket row = findTicket(ticket);
        return row == null ? null : row.getSubject();
    }

    /**
     * 消费一次性票据（置 consumedAt，保留审计行；校验存在、未消费且未过期，防重放）。
     * 无效/已过期/已消费 → null。
     * 供 {@link #issue}（票据+密码）与设备绑定（ticket+fingerprint）共用同一票据池——任一成功即失效。
     */
    public String consumeTicket(String ticket) {
        SoysSsoTicket row = consumeTicketRow(ticket);
        return row == null ? null : row.getSubject();
    }

    /**
     * 消费一次性票据并返回完整行（含 redirectUrl，跨域名 SSO callback 用）。
     * 校验存在/未消费/未过期后置 consumedAt；无效 → null。
     */
    public SoysSsoTicket consumeTicketRow(String ticket) {
        SoysSsoTicket row = findTicket(ticket);
        if (row == null) {
            return null;
        }
        row.setConsumedAt(System.currentTimeMillis());
        row.setUpdateTime(new Date());
        try {
            DATA.updateById(row);
        } catch (Exception ex) {
            log.warnT("log.auth.ticket-consume-fail", "SSO 票据消费落库失败: {0}", ex.getMessage());
            return null;
        }
        return row;
    }

    /**
     * 按 ticket 串查实体，校验：存在 + 未消费 + 未过期。任一不满足返回 null。
     */
    private SoysSsoTicket findTicket(String ticket) {
        if (ticket == null || ticket.isEmpty()) {
            return null;
        }
        try {
            java.util.List<SoysSsoTicket> list = DATA.select(SoysSsoTicket.class,
                    q -> q.eq(SoysSsoTicket::getTicket, ticket));
            if (list == null || list.isEmpty()) {
                return null;
            }
            SoysSsoTicket row = list.get(0);
            long now = System.currentTimeMillis();
            if (row.getConsumedAt() != null) {
                return null; // 已消费（防重放）
            }
            if (row.getExpiresAt() == null || row.getExpiresAt() <= now) {
                return null; // 已过期
            }
            return row;
        } catch (Exception ex) {
            log.warnT("log.auth.ticket-lookup-fail", "SSO 票据查询失败: {0}", ex.getMessage());
            return null;
        }
    }

    /**
     * 惰性清理过期票据行（短命票，不启定时任务；任一签发时顺带清理）。
     */
    private void lazyCleanExpiredTickets(long now) {
        try {
            java.util.List<SoysSsoTicket> all = DATA.select(SoysSsoTicket.class);
            int removed = 0;
            for (SoysSsoTicket row : all) {
                Long exp = row.getExpiresAt();
                boolean expired = exp == null || exp <= now;
                boolean staleConsumed = row.getConsumedAt() != null && (row.getConsumedAt() + 30_000L) <= now;
                if (expired || staleConsumed) {
                    DATA.deleteById(SoysSsoTicket.class, row.getId());
                    removed++;
                }
            }
            if (removed > 0) {
                log.infoT("log.auth.ticket-cleanup", "SSO 票据惰性清理: {0} 行", removed);
            }
        } catch (Exception ignored) {
            // 清理失败不影响主流程
        }
    }

    /**
     * 登记玩家已签发的会话令牌（LoginEvent 时调用）。
     */
    public void storeToken(String player, String token) {
        playerTokens.put(player, token);
    }

    /**
     * 网页登录窗口入口：用玩家名 + AuthMe 密码直接登录（与 ticket 流程互补，无需游戏内链接）。
     * 校验通过 → 按 {@link LoginModePolicy} 决定登录模式（在线→ONLINE；不在线且允许→OFFLINE 离线专属 cookie）
     * → 签发新会话令牌并登记 → 返回令牌（cookieValue，同 token 可作 Bearer / X-API-Key / Cookie）。
     * 校验失败 / AuthMe 未安装 / 离线登录被策略禁止 → 返回 null。
     */
    public String login(String username, String password) {
        if (!verifyPassword(username, password)) return null;
        return issueByUsername0(username);
    }

    /**
     * 网页登录是否需要密码（有登录插件=需要；无登录插件=免密码，仅凭用户名）。
     */
    public boolean loginRequiresPassword() {
        return loginProvider != null;
    }

    /**
     * 免密码登录：未接入登录插件（loginProvider==null）时，仅凭用户名签发会话令牌。
     * 校验用户名合法性 → 按 {@link LoginModePolicy} 决定模式 → 签发并登记 → 返回令牌；失败返回 null。
     */
    public String loginByUsername(String username) {
        if (username == null || username.trim().isEmpty()) return null;
        String name = username.trim();
        if (!name.matches("[A-Za-z0-9_]{1,16}")) return null;
        return issueByUsername0(name);
    }

    /**
     * 内部：按登录模式策略签发令牌（校验通过后调用）。
     */
    private String issueByUsername0(String name) {
        LoginModePolicy policy = loginModePolicy;
        LoginMode mode = policy.decideLogin(name);
        if (mode == LoginMode.OFFLINE && !policy.allowOfflineLogin(name)) {
            return null; // 策略禁止离线登录
        }
        return issueToken(name, mode);
    }

    /**
     * 纯签发（在线默认）：为玩家签发新会话令牌并登记（LoginEvent / 密码已校验时调用），返回令牌。
     */
    public String issueToken(String player) {
        return issueToken(player, LoginMode.ONLINE);
    }

    /**
     * 按指定登录模式签发并登记令牌（返回 JWT 令牌字符串，同 token 可作 Bearer / X-API-Key / Cookie）。
     */
    public String issueToken(String player, LoginMode mode) {
        String token = issuer.issueToken(player, mode);
        playerTokens.put(player, token);
        return token;
    }

    // ===== 记住我（设备免登录）：签发 / 验证 / 撤销 =====

    /**
     * 注入自动登录配置（记住我 / IP 匹配 / 设备指纹双因子 / 票据）。
     *
     * @param rememberEnabled     记住我总开关
     * @param rememberTtlMillis   记住我凭证有效期（毫秒）
     * @param ipEnabled           旧 IP 匹配免登录开关
     * @param fpEnabled           设备指纹双因子开关
     * @param fpStrict            指纹严格模式
     * @param ticketTtlSeconds    游戏端→网页端绑定票据有效期（秒）
     * @param ticketLinkEnabled   游戏内登录后是否发送可点击票据链接
     */
    public void setAutoLoginConfig(boolean rememberEnabled, long rememberTtlMillis, boolean ipEnabled,
                                   boolean fpEnabled, boolean fpStrict, int ticketTtlSeconds,
                                   boolean ticketLinkEnabled) {
        this.rememberEnabled = rememberEnabled;
        this.rememberTtlMillis = Math.max(1000L, rememberTtlMillis);
        this.ipEnabled = ipEnabled;
        this.fpEnabled = fpEnabled;
        this.fpStrict = fpStrict;
        this.ticketTtlMillis = Math.max(1000L, ticketTtlSeconds * 1000L);
        this.ticketLinkEnabled = ticketLinkEnabled;
    }

    /**
     * 设备指纹双因子开关是否启用。
     */
    public boolean isFpEnabled() {
        return fpEnabled;
    }

    /**
     * 指纹严格模式（true=不一致拒绝；false=宽松告警放行）。
     */
    public boolean isFpStrict() {
        return fpStrict;
    }

    /**
     * 游戏端→网页端绑定票据有效期（毫秒）。
     */
    public long getTicketTtlMillis() {
        return ticketTtlMillis;
    }

    /**
     * 游戏内登录后是否发送可点击票据链接。
     */
    public boolean isTicketLinkEnabled() {
        return ticketLinkEnabled;
    }

    /**
     * 记住我功能是否启用。
     */
    public boolean isRememberEnabled() {
        return rememberEnabled;
    }

    /**
     * 记住我凭证有效期（毫秒）。
     */
    public long getRememberTtlMillis() {
        return rememberTtlMillis;
    }

    /**
     * 记住我凭证有效期（秒，供 Set-Cookie Max-Age 使用）。
     */
    public long getRememberTtlSeconds() {
        return rememberTtlMillis / 1000L;
    }

    /**
     * 旧 IP 匹配免登录开关是否启用。
     */
    public boolean isIpEnabled() {
        return ipEnabled;
    }

    /**
     * 记住我 Cookie 名称。
     */
    public String getRememberCookieName() {
        return REMEMBER_COOKIE;
    }

    // ===== Cookie 属性（auth.cookie.* 配置；跨子域名共享 / HTTPS 加固） =====

    /**
     * Cookie Domain（空 = 精确 host，同域名多端口自动共享；
     * 配 {@code .example.com} = 所有子域共享，多子域名 SSO 用）。
     */
    private volatile String cookieDomain = "";
    /**
     * Cookie Secure 标记（HTTPS 部署开启；跨域名回跳必须 HTTPS）。
     */
    private volatile boolean cookieSecure = false;
    /**
     * Cookie SameSite（默认 Lax；跨域顶层 GET 导航/302 回跳 Lax 即足够）。
     */
    private volatile String cookieSameSite = "Lax";

    /**
     * 注入 Cookie 属性（由 {@link HttpOverMcPluginProxy} 从 auth.cookie.* 装配后调用）。
     */
    public void setCookieAttributes(String domain, boolean secure, String sameSite) {
        this.cookieDomain = domain == null ? "" : domain.trim();
        this.cookieSecure = secure;
        this.cookieSameSite = sameSite == null || sameSite.trim().isEmpty() ? "Lax" : sameSite.trim();
    }

    /**
     * 统一拼装 Set-Cookie 行（会话 / 记住我 / 升级换发全部走此一处，domain/secure/same-site 可配）。
     *
     * @param name           cookie 名
     * @param value          cookie 值（删除 cookie 传空串）
     * @param maxAgeSeconds  Max-Age（秒；删除 cookie 传 0）
     */
    public String buildSetCookie(String name, String value, long maxAgeSeconds) {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append('=').append(value).append("; Path=/");
        if (maxAgeSeconds >= 0) {
            sb.append("; Max-Age=").append(maxAgeSeconds);
        }
        if (!cookieDomain.isEmpty()) {
            sb.append("; Domain=").append(cookieDomain);
        }
        if (cookieSecure) {
            sb.append("; Secure");
        }
        sb.append("; SameSite=").append(cookieSameSite).append("; HttpOnly");
        return sb.toString();
    }

    /**
     * SSO 允许回跳的来源白名单（origin = scheme://host[:port]；由 AuthPolicy sso.allowed-origins 装配）。
     */
    private volatile java.util.List<String> ssoAllowedOrigins = new java.util.ArrayList<>();

    /**
     * 注入 SSO 回跳来源白名单（由 {@link HttpOverMcPluginProxy} 从 auth.yml sso.allowed-origins 装配）。
     */
    public void setSsoAllowedOrigins(java.util.List<String> origins) {
        this.ssoAllowedOrigins = origins == null ? new java.util.ArrayList<String>() : origins;
    }

    /**
     * 校验 SSO 回跳目标 URL 的 origin 是否在白名单内（防开放跳转）。
     * 同源相对路径（不以 scheme:// 开头）视为本服内部跳转，放行。
     */
    public boolean isSsoTargetAllowed(String targetUrl) {
        if (targetUrl == null || targetUrl.isEmpty()) return false;
        // 相对路径（站内回跳）直接放行
        if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) return true;
        int slash = targetUrl.indexOf('/', targetUrl.indexOf("://") + 3);
        String origin = slash < 0 ? targetUrl : targetUrl.substring(0, slash);
        String o = origin.endsWith("/") ? origin.substring(0, origin.length() - 1) : origin;
        for (String allow : ssoAllowedOrigins) {
            String a = allow.endsWith("/") ? allow.substring(0, allow.length() - 1) : allow;
            if (a.equalsIgnoreCase(o)) return true;
        }
        return false;
    }

    /**
     * 签发“记住我”设备凭证（登录成功后调用）：生成 remember JWT（TTL=配置天数）
     * 并持久化 ORM 实体（jti→player），返回 JWT 令牌；未启用/签发失败返回 null。
     */
    public String issueRemember(String player) {
        if (!rememberEnabled || player == null) return null;
        try {
            String token = issuer.issueRememberToken(player, rememberTtlMillis);
            if (token == null) return null;
            JwtCodec.Payload p = issuer.parseRememberToken(token);
            if (p == null || p.jti == null) return null;
            long now = System.currentTimeMillis();
            persistRemember(new RememberCredential(p.jti, player,
                    new java.util.Date(now), new java.util.Date(now + rememberTtlMillis)));
            return token;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 校验“记住我”凭证：JWT 验签/过期/黑名单 + ORM 实体存在且未过期 → 返回绑定玩家；无效返回 null。
     */
    public String resolveRemember(String rememberToken) {
        if (!rememberEnabled || rememberToken == null) return null;
        try {
            JwtCodec.Payload p = issuer.parseRememberToken(rememberToken);
            if (p == null) return null;
            RememberCredential rec = loadRemember(p.jti);
            if (rec == null) return null;
            java.util.Date exp = rec.getExpiresAt();
            if (exp == null || exp.before(new java.util.Date())) return null;
            return rec.getPlayer();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * “记住我”设备免登录：按登录模式策略为玩家签发会话令牌（玩家在线→ONLINE，否则→OFFLINE）。
     * 策略禁止离线登录且玩家不在线 → 返回 null（不自动登录，保持与正常登录一致）。
     */
    public String issueForRemember(String player) {
        if (player == null) return null;
        LoginModePolicy policy = loginModePolicy;
        LoginMode mode = policy.decideLogin(player);
        if (mode == LoginMode.OFFLINE && !policy.allowOfflineLogin(player)) {
            return null;
        }
        return issueToken(player, mode);
    }

    /**
     * 撤销某玩家全部“记住我”凭证（退出登录时调用）：jti 进黑名单 + 删除实体。
     */
    public void revokeRemember(String player) {
        if (player == null) return;
        try {
            for (RememberCredential rec : listRemember(player)) {
                if (rec.getJti() != null && !rec.getJti().isEmpty()) {
                    try {
                        issuer.revokeJti(rec.getJti());
                    } catch (Throwable ignored) {
                    }
                    deleteRemember(rec.getJti());
                }
            }
        } catch (Throwable ignored) {
        }
    }

    // ===== 记住我凭证持久化（ORM：优先 SQL 后端，否则 YAML 后端） =====

    private void persistRemember(RememberCredential rec) {
        if (rec == null) return;
        try {
            DATA.insert(rec);
        } catch (Throwable ignored) {
        }
    }

    private RememberCredential loadRemember(String jti) {
        if (jti == null) return null;
        try {
            return DATA.get(RememberCredential.class, jti);
        } catch (Throwable ignored) {
        }
        return null;
    }

    private List<RememberCredential> listRemember(String player) {
        try {
            List<RememberCredential> r = DATA.select(RememberCredential.class,
                    q -> q.eq(RememberCredential::getPlayer, player));
            return r != null ? r : new ArrayList<>();
        } catch (Throwable ignored) {
        }
        return new ArrayList<>();
    }

    private void deleteRemember(String jti) {
        if (jti == null) return;
        try {
            DATA.deleteById(RememberCredential.class, jti);
        } catch (Throwable ignored) {
        }
    }

    // ===== 设备指纹双因子（soys_device_binding）：登记 / 校验 / 撤销 =====

    /**
     * 计算设备指纹哈希：SHA-256(玩家名 + "|" + 原始指纹 + 服务端盐)，16 进制小写。
     * 指纹原始串不落库、不落日志（指纹非秘密凭据，仅一致性弱校验；原始串采集见前端
     * {@code dist/soys-auth.js} 的 collectFingerprint，包含 UA/屏幕/时区/语言/canvas/webgl/并发核数）。
     */
    public static String fingerprintHash(String player, String rawFingerprint) {
        if (player == null || rawFingerprint == null || rawFingerprint.isEmpty()) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest((player + "|" + rawFingerprint + "|" + FP_SALT).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(h.length * 2);
            for (byte b : h) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 登记/刷新设备绑定（登录成功或票据绑定成功后调用）：
     * 同 (player, fingerprintHash) 已有记录 → 更新 lastIp/lastBindAt/deviceLabel/revoked=0/回填 UUID；
     * 无记录 → 新增（YAML/SQL 双端经 ORM）。返回是否成功。
     */
    public boolean registerDevice(String player, String rawFingerprint, String ip, String label) {
        if (player == null || rawFingerprint == null || rawFingerprint.isEmpty()) return false;
        String hash = fingerprintHash(player, rawFingerprint);
        if (hash == null) return false;
        try {
            List<SoysDeviceBinding> existing = DATA.select(SoysDeviceBinding.class,
                    q -> q.eq(SoysDeviceBinding::getPlayer, player)
                            .eq(SoysDeviceBinding::getFingerprintHash, hash));
            SoysDeviceBinding b;
            if (existing != null && !existing.isEmpty()) {
                b = existing.get(0);
                b.setDeviceLabel(label);
                if (ip != null) b.setLastIp(ip);
                b.setLastBindAt(new java.util.Date());
                b.setRevoked(0);
                fillUuid(b, player);
                return DATA.updateById(b);
            }
            b = new SoysDeviceBinding();
            b.setPlayer(player);
            b.setFingerprintHash(hash);
            b.setDeviceLabel(label);
            b.setLastIp(ip);
            b.setLastBindAt(new java.util.Date());
            b.setRevoked(0);
            fillUuid(b, player);
            return DATA.insert(b);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 该玩家是否已存在任一有效绑定（迁移策略判定：无绑定记录 → 本次自动登录放行并要求前端立即补绑）。
     */
    public boolean isDeviceBound(String player) {
        if (player == null) return false;
        try {
            List<SoysDeviceBinding> list = DATA.select(SoysDeviceBinding.class,
                    q -> q.eq(SoysDeviceBinding::getPlayer, player));
            if (list == null) return false;
            for (SoysDeviceBinding b : list) {
                if (b.getRevoked() == null || b.getRevoked() == 0) return true;
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 请求指纹与该玩家任一有效绑定是否一致（自动登录双因子校验；命中且未弃用 → true）。
     */
    public boolean deviceMatches(String player, String rawFingerprint) {
        if (player == null || rawFingerprint == null || rawFingerprint.isEmpty()) return false;
        String hash = fingerprintHash(player, rawFingerprint);
        if (hash == null) return false;
        try {
            List<SoysDeviceBinding> list = DATA.select(SoysDeviceBinding.class,
                    q -> q.eq(SoysDeviceBinding::getPlayer, player)
                            .eq(SoysDeviceBinding::getFingerprintHash, hash));
            if (list == null || list.isEmpty()) return false;
            for (SoysDeviceBinding b : list) {
                if (b.getRevoked() == null || b.getRevoked() == 0) return true;
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 撤销指定设备绑定（revoked=1，不再参与判定）。找不到对应绑定 → 返回 false。
     */
    public boolean revokeDevice(String player, String rawFingerprint) {
        if (player == null || rawFingerprint == null || rawFingerprint.isEmpty()) return false;
        String hash = fingerprintHash(player, rawFingerprint);
        if (hash == null) return false;
        try {
            List<SoysDeviceBinding> list = DATA.select(SoysDeviceBinding.class,
                    q -> q.eq(SoysDeviceBinding::getPlayer, player)
                            .eq(SoysDeviceBinding::getFingerprintHash, hash));
            if (list == null || list.isEmpty()) return false;
            for (SoysDeviceBinding b : list) {
                b.setRevoked(1);
                DATA.updateById(b);
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 回填绑定记录的玩家 UUID（Bukkit 在线时取在线实体；不在线时按离线 UUID 算法，与
     * UuidUtil 语义一致——见 {@link com.github.cocosoys.mc.soyshttpovermc.util.UuidUtil}）。
     */
    private static void fillUuid(SoysDeviceBinding b, String player) {
        if (b == null || b.getUuid() != null) return;
        try {
            Player online = Bukkit.getPlayerExact(player);
            if (online != null) {
                b.setUuid(online.getUniqueId().toString());
            } else {
                b.setUuid(com.github.cocosoys.mc.soyshttpovermc.util.UuidUtil.uuidOf(player).toString());
            }
        } catch (Throwable ignored) {
        }
    }

    // ===== 登录 IP 记录与双向免登录 =====

    /**
     * 记录网页端登录 IP（网页登录成功时调用，用于后续游戏端 IP 匹配免登录）。
     */
    public void recordWebLogin(String player, String ip) {
        if (player == null || ip == null) return;
        webLoginIps.put(player, ip);
    }

    /**
     * 记录游戏端登录 IP（AuthMe LoginEvent 时调用，用于后续网页端 IP 匹配自动登录）。
     */
    public void recordGameLogin(String player, String ip) {
        if (player == null || ip == null) return;
        gameLoginIps.put(player, ip);
    }

    /**
     * 获取网页端登录 IP（未登录返回 null）。
     */
    public String getWebLoginIp(String player) {
        return player == null ? null : webLoginIps.get(player);
    }

    /**
     * 获取游戏端登录 IP（未登录返回 null）。
     */
    public String getGameLoginIp(String player) {
        return player == null ? null : gameLoginIps.get(player);
    }

    /**
     * 网页端是否已登录（有令牌记录 + 有登录 IP）。
     */
    public boolean isWebLoggedIn(String player) {
        if (player == null) return false;
        return playerTokens.containsKey(player) && webLoginIps.containsKey(player);
    }

    /**
     * 游戏端是否已登录（玩家当前在线 + 有登录 IP 记录）。
     */
    public boolean isGameLoggedIn(String player) {
        if (player == null) return false;
        if (!gameLoginIps.containsKey(player)) return false;
        return Bukkit.getPlayerExact(player) != null;
    }

    /**
     * 网页端登录 IP 与给定 IP 是否匹配（用于游戏端免登录判定）。
     * 回环地址（127.0.0.1 / ::1 / localhost）等价匹配。
     */
    public boolean webIpMatches(String player, String ip) {
        if (player == null || ip == null) return false;
        String recorded = webLoginIps.get(player);
        if (recorded == null) return false;
        return ipEquals(recorded, ip);
    }

    /**
     * 游戏端登录 IP 与给定 IP 是否匹配（用于网页端自动登录判定）。
     * 回环地址（127.0.0.1 / ::1 / localhost）等价匹配。
     */
    public boolean gameIpMatches(String player, String ip) {
        if (player == null || ip == null) return false;
        String recorded = gameLoginIps.get(player);
        if (recorded == null) return false;
        return ipEquals(recorded, ip);
    }

    /**
     * 清除网页端登录记录（退出登录时调用）。
     */
    public void clearWebLogin(String player) {
        if (player == null) return;
        webLoginIps.remove(player);
    }

    /**
     * 清除游戏端登录记录（玩家退出时调用）。
     */
    public void clearGameLogin(String player) {
        if (player == null) return;
        gameLoginIps.remove(player);
    }

    /**
     * IP 比较：回环地址等价（127.0.0.1 / ::1 / localhost），其余精确匹配。
     */
    private static boolean ipEquals(String a, String b) {
        if (a == null || b == null) return false;
        if (a.equals(b)) return true;
        boolean aLoop = a.equals("127.0.0.1") || a.equals("::1") || a.equalsIgnoreCase("localhost") || a.equals("0:0:0:0:0:0:0:1");
        boolean bLoop = b.equals("127.0.0.1") || b.equals("::1") || b.equalsIgnoreCase("localhost") || b.equals("0:0:0:0:0:0:0:1");
        return aLoop && bLoop;
    }

    /**
     * 玩家进游戏登录成功：把其名下现存离线令牌升级为在线模式（JWT 无状态无法原地改 payload，
     * 故黑名单旧离线令牌 + 签发新在线令牌，playerTokens 同步换新）。返回被换发的令牌数。
     */
    public int upgradePlayerToOnline(String player) {
        if (player == null) return 0;
        String old = playerTokens.get(player);
        if (old == null) return 0;
        if (issuer.modeOfToken(old) == LoginMode.ONLINE) return 0;
        issuer.revoke(old); // 旧离线令牌 jti 进黑名单
        String token = issuer.issueToken(player, LoginMode.ONLINE);
        playerTokens.put(player, token);
        return 1;
    }

    /**
     * 校验玩家登录插件密码（未接入提供者 → false）。纯账号密码校验，不要求玩家在线。
     */
    public boolean verifyPassword(String player, String password) {
        LoginProvider provider = loginProvider;
        if (provider == null) return false;
        try {
            return provider.verifyPassword(player, password);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 由请求凭证解析绑定主体（玩家名），供登录信息接口查询；无效凭证返回 null。
     */
    public String subjectOf(CredentialPresentation p) {
        return p == null ? null : issuer.subjectOf(p);
    }

    /**
     * 由请求凭证解析登录模式（ONLINE/OFFLINE），供登录信息接口返回"离线模式登录"标签；无效凭证返回 null。
     */
    public LoginMode modeOf(CredentialPresentation p) {
        return p == null ? null : issuer.modeOf(p);
    }

    /**
     * Cookie 名称（默认 soys_session），供登录接口返回给前端保存。
     */
    public String getCookieName() {
        return issuer.getCookieName();
    }

    /**
     * 会话令牌 TTL（秒），供登录接口返回给前端（Max-Age / 过期提示）。
     */
    public long getTtlSeconds() {
        return issuer.getTtlSeconds();
    }

    /**
     * 退出登录：按请求携带的凭证撤销会话令牌。
     */
    public boolean logout(CredentialPresentation p) {
        return p != null && issuer.revoke(p);
    }

    /**
     * <b>离线 cookie 自动升级</b>：请求凭证为离线模式令牌（mode=OFFLINE）且其绑定玩家<b>此刻已在游戏内在线</b>时，
     * 黑名单旧离线令牌 + 签发新的在线令牌（playerTokens 同步换新）并返回新令牌；
     * 调用方应将新令牌以 {@code Set-Cookie} + {@code X-Soys-New-Token} 附加到当前响应，
     * 使浏览器无需再次输入密码即可无缝升级为在线会话（在线令牌完整镜像玩家权限）。
     * 非离线令牌 / 玩家不在线 / 无效凭证 → 返回 null（不升级）。
     */
    public String upgradeOfflineIfOnline(CredentialPresentation p) {
        if (p == null || !p.hasAnyCredential()) return null;
        String player = issuer.subjectOf(p);
        if (player == null) return null;
        if (issuer.modeOf(p) != LoginMode.OFFLINE) return null;
        Player online = Bukkit.getPlayerExact(player);
        if (online == null) return null;
        // 玩家已在线：旧离线令牌进黑名单 + 签发在线令牌（无状态 JWT 只能换发）
        issuer.revoke(p);
        String fresh = issuer.issueToken(player, LoginMode.ONLINE);
        playerTokens.put(player, fresh);
        return fresh;
    }

    /**
     * 供 ApiRegistry 注入的升级器适配方法：调用 {@link #upgradeOfflineIfOnline}，成功换发时返回
     * 待附加到当前响应的头（{@code Set-Cookie} 新在线令牌 + {@code X-Soys-New-Token}），否则返回 null。
     */
    public Map<String, String> upgradeHeadersIfOnline(CredentialPresentation p) {
        String fresh = upgradeOfflineIfOnline(p);
        if (fresh == null) return null;
        Map<String, String> h = new HashMap<>();
        h.put("Set-Cookie", buildSetCookie(issuer.getCookieName(), fresh, issuer.getTtlSeconds()));
        h.put("X-Soys-New-Token", fresh);
        return h;
    }

    /**
     * GET /api/auth/login?ticket=...：重定向到前端登录页（/login.html?ticket=...，浏览器原生 302）。
     * 有登录插件=票据+密码二次验证（票据无效 → 400 JSON）；无登录插件=免密码，直接跳到登录页
     * （前端按 /api/auth/mode 切换「票据+密码」/「免密用户名」表单）。票据不在此消费（保留一次性语义）。
     * <p>返回 {@link ApiResponse}（注解式 API 响应控制），由 AuthController 端点透传给网关组装帧。</p>
     */
    public ApiResponse serveLoginPage(String ticket) {
        if (loginProvider != null) {
            String player = peekTicket(ticket);
            if (player == null) {
                return ApiResponse.jsonErrorT(400, "ajax.auth.login-ticket-invalid", "登录票据无效或已失效，请重新登录游戏以获取新的网页登录链接");
            }
        }
        String loc = "/login.html" + (ticket == null ? "" : "?ticket=" + urlEncode(ticket));
        return ApiResponse.redirect(loc);
    }

    /**
     * POST /api/auth/issue（免密码模式）：仅凭用户名直接签发会话 Cookie（JSON 成功体 + Set-Cookie）。
     */
    public ApiResponse issueByUsername(String username) {
        String token = loginByUsername(username);
        if (token == null) {
            return ApiResponse.jsonErrorT(400, "ajax.auth.username-invalid", "用户名不合法（仅字母/数字/下划线，≤16 字符）或离线登录被策略禁止");
        }
        return jsonWithCookie(username.trim(), token);
    }

    /**
     * POST /api/auth/issue：校验 AuthMe 密码，验证通过下发会话 Cookie（JSON 成功体 + Set-Cookie）。
     */
    public ApiResponse issue(String ticket, String password) {
        String player = consumeTicket(ticket);
        if (player == null) {
            return ApiResponse.jsonErrorT(400, "ajax.auth.login-ticket-used", "登录票据无效或已使用，请重新登录游戏以获取新的网页登录链接");
        }
        if (loginProvider == null) {
            return ApiResponse.jsonErrorT(503, "ajax.auth.no-login-plugin", "未接入登录插件，无法验证密码（请确认服务器已加载 AuthMe 等登录插件）");
        }
        boolean ok;
        try {
            ok = loginProvider.verifyPassword(player, password);
        } catch (Throwable t) {
            ok = false;
        }
        if (!ok) {
            return ApiResponse.jsonErrorT(401, "ajax.auth.bad-credentials", "账号或密码错误（AuthMe 校验失败，或服务器未安装 AuthMe，或禁止离线登录）");
        }
        String token = playerTokens.get(player);
        if (token == null) {
            // 兜底：理论上 LoginEvent 已签发；此处重新签发以确保可用
            token = issuer.issueToken(player, LoginMode.ONLINE);
            playerTokens.put(player, token);
        }
        return jsonWithCookie(player, token);
    }

    /**
     * 登录成功统一响应：JSON 成功体 + Set-Cookie（令牌仅在密码验证通过后下发一次）。
     * data 携带玩家名供前端展示；cookie 语义保留（HttpOnly，浏览器自动携带）。
     */
    private ApiResponse jsonWithCookie(String player, String token) {
        String cookie = buildSetCookie(issuer.getCookieName(), token, issuer.getTtlSeconds());
        Map<String, String> extra = new HashMap<>();
        extra.put("Set-Cookie", cookie);
        return ApiResponse.status(200, AjaxResult.success("ok", player), extra);
    }

    private static String urlEncode(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }
}
