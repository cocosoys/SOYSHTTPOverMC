package soys.soyshttpovermc.gateway.policy.auth.bridge;

import soys.soyshttpovermc.gateway.policy.auth.util.AuthUtils;
import soys.soyshttpovermc.gateway.policy.auth.issuer.CredentialPresentation;
import soys.soyshttpovermc.gateway.policy.auth.issuer.SessionTokenIssuer;
import soys.soyshttpovermc.gateway.policy.auth.login.DefaultLoginModePolicy;
import soys.soyshttpovermc.gateway.policy.auth.login.LoginMode;
import soys.soyshttpovermc.gateway.policy.auth.login.LoginModePolicy;
import soys.soyshttpovermc.gateway.policy.auth.bridge.spi.LoginProvider;
import soys.soyshttpovermc.util.AjaxResult;
import soys.soyshttpovermc.util.ApiResponse;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.net.URLEncoder;
import java.util.HashMap;
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
public class AuthLoginBridge {

    private final SessionTokenIssuer issuer;
    /** 登录插件提供者（AuthMe 等，负责纯账号密码校验）；null=未接入登录插件。 */
    private volatile LoginProvider loginProvider;
    /** 登录模式策略（决定在线/离线签发）；默认允许离线登录。 */
    private volatile LoginModePolicy loginModePolicy = new DefaultLoginModePolicy();

    /** 一次性登录票据 → 玩家名（使用即删除）。 */
    private final Map<String, String> loginTickets = new ConcurrentHashMap<>();
    /** 玩家名 → 已签发的会话令牌（LoginEvent 时登记）。 */
    private final Map<String, String> playerTokens = new ConcurrentHashMap<>();

    public AuthLoginBridge(SessionTokenIssuer issuer) {
        this.issuer = issuer;
    }

    /** 绑定登录插件提供者（bridge 创建/重建后由网关调用；幂等）。 */
    public void setLoginProvider(LoginProvider provider) {
        this.loginProvider = provider;
    }

    /** 当前登录插件提供者（null=未接入）。 */
    public LoginProvider getLoginProvider() {
        return loginProvider;
    }

    /** 替换登录模式策略（服务端可定制离线登录许可 / 模式判定）。 */
    public void setLoginModePolicy(LoginModePolicy policy) {
        this.loginModePolicy = policy == null ? new DefaultLoginModePolicy() : policy;
    }

    /** 当前登录模式策略。 */
    public LoginModePolicy getLoginModePolicy() {
        return loginModePolicy;
    }

    /** 生成一次性登录票据（返回给玩家点开的链接）。 */
    public String mintTicket(String player) {
        String ticket = AuthUtils.generateToken("tk_", 16);
        loginTickets.put(ticket, player);
        return ticket;
    }

    /** 登记玩家已签发的会话令牌（LoginEvent 时调用）。 */
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

    /** 网页登录是否需要密码（有登录插件=需要；无登录插件=免密码，仅凭用户名）。 */
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

    /** 内部：按登录模式策略签发令牌（校验通过后调用）。 */
    private String issueByUsername0(String name) {
        LoginModePolicy policy = loginModePolicy;
        LoginMode mode = policy.decideLogin(name);
        if (mode == LoginMode.OFFLINE && !policy.allowOfflineLogin(name)) {
            return null; // 策略禁止离线登录
        }
        return issueToken(name, mode);
    }

    /** 纯签发（在线默认）：为玩家签发新会话令牌并登记（LoginEvent / 密码已校验时调用），返回令牌。 */
    public String issueToken(String player) {
        return issueToken(player, LoginMode.ONLINE);
    }

    /** 按指定登录模式签发并登记令牌（返回 JWT 令牌字符串，同 token 可作 Bearer / X-API-Key / Cookie）。 */
    public String issueToken(String player, LoginMode mode) {
        String token = issuer.issueToken(player, mode);
        playerTokens.put(player, token);
        return token;
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

    /** 校验玩家登录插件密码（未接入提供者 → false）。纯账号密码校验，不要求玩家在线。 */
    public boolean verifyPassword(String player, String password) {
        LoginProvider provider = loginProvider;
        if (provider == null) return false;
        try {
            return provider.verifyPassword(player, password);
        } catch (Throwable t) {
            return false;
        }
    }

    /** 由请求凭证解析绑定主体（玩家名），供登录信息接口查询；无效凭证返回 null。 */
    public String subjectOf(CredentialPresentation p) {
        return p == null ? null : issuer.subjectOf(p);
    }

    /** 由请求凭证解析登录模式（ONLINE/OFFLINE），供登录信息接口返回"离线模式登录"标签；无效凭证返回 null。 */
    public LoginMode modeOf(CredentialPresentation p) {
        return p == null ? null : issuer.modeOf(p);
    }

    /** Cookie 名称（默认 soys_session），供登录接口返回给前端保存。 */
    public String getCookieName() {
        return issuer.getCookieName();
    }

    /** 会话令牌 TTL（秒），供登录接口返回给前端（Max-Age / 过期提示）。 */
    public long getTtlSeconds() {
        return issuer.getTtlSeconds();
    }

    /** 退出登录：按请求携带的凭证撤销会话令牌。 */
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
        h.put("Set-Cookie", issuer.getCookieName() + "=" + fresh
                + "; Path=/; Max-Age=" + issuer.getTtlSeconds() + "; HttpOnly; SameSite=Lax");
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
            String player = (ticket == null) ? null : loginTickets.get(ticket);
            if (player == null) {
                return ApiResponse.jsonError(400, "登录票据无效或已失效，请重新登录游戏以获取新的网页登录链接");
            }
        }
        String loc = "/login.html" + (ticket == null ? "" : "?ticket=" + urlEncode(ticket));
        return ApiResponse.redirect(loc);
    }

    /** POST /api/auth/issue（免密码模式）：仅凭用户名直接签发会话 Cookie（JSON 成功体 + Set-Cookie）。 */
    public ApiResponse issueByUsername(String username) {
        String token = loginByUsername(username);
        if (token == null) {
            return ApiResponse.jsonError(400, "用户名不合法（仅字母/数字/下划线，≤16 字符）或离线登录被策略禁止");
        }
        return jsonWithCookie(username.trim(), token);
    }

    /** POST /api/auth/issue：校验 AuthMe 密码，验证通过下发会话 Cookie（JSON 成功体 + Set-Cookie）。 */
    public ApiResponse issue(String ticket, String password) {
        String player = (ticket == null) ? null : loginTickets.remove(ticket);
        if (player == null) {
            return ApiResponse.jsonError(400, "登录票据无效或已使用，请重新登录游戏以获取新的网页登录链接");
        }
        if (loginProvider == null) {
            return ApiResponse.jsonError(503, "未接入登录插件，无法验证密码（请确认服务器已加载 AuthMe 等登录插件）");
        }
        boolean ok;
        try {
            ok = loginProvider.verifyPassword(player, password);
        } catch (Throwable t) {
            ok = false;
        }
        if (!ok) {
            return ApiResponse.jsonError(401, "账号或密码错误（AuthMe 校验失败，或服务器未安装 AuthMe，或禁止离线登录）");
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
        String cookie = issuer.getCookieName() + "=" + token
                + "; Path=/; Max-Age=" + issuer.getTtlSeconds()
                + "; HttpOnly; SameSite=Lax";
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
