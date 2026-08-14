package soys.soyshttpovermc.gateway.policy.auth.bridge;

import soys.soyshttpovermc.gateway.policy.auth.util.AuthUtils;
import soys.soyshttpovermc.gateway.policy.auth.issuer.CredentialPresentation;
import soys.soyshttpovermc.gateway.policy.auth.issuer.SessionTokenIssuer;
import soys.soyshttpovermc.gateway.policy.login.DefaultLoginModePolicy;
import soys.soyshttpovermc.gateway.policy.login.LoginMode;
import soys.soyshttpovermc.gateway.policy.login.LoginModePolicy;
import soys.soyshttpovermc.gateway.policy.auth.bridge.spi.LoginProvider;
import soys.soyshttpovermc.proto.FrameProto;

import com.google.protobuf.ByteString;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 网页登录桥（登录插件无关，密码校验由 {@link LoginProvider} SPI 提供，如 AuthMe）：
 * <ul>
 *   <li>玩家游戏内登录成功时，登录插件提供者（AuthMeLoginProvider 等）调用 {@link #storeToken}
 *       登记 players→token，并 {@link #mintTicket} 生成一次性登录票据；</li>
 *   <li>浏览器访问 {@link #serveLoginPage}（/auth/login?ticket=...）渲染账号密码表单；</li>
 *   <li>浏览器提交密码（POST /auth/issue），{@link #issue} 经登录插件提供者验证密码，
 *       验证通过后将已登记的会话令牌以 {@code Set-Cookie} 交给浏览器（仅此一次暴露令牌）。</li>
 * </ul>
 * 设计：即使登录链接被截获，攻击者也必须知道该玩家的登录密码才能换取 Cookie；令牌仅在密码
 * 验证通过后才下发。票据一次性（使用即销毁）。
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
        LoginModePolicy policy = loginModePolicy;
        LoginMode mode = policy.decideLogin(username);
        if (mode == LoginMode.OFFLINE && !policy.allowOfflineLogin(username)) {
            return null; // 策略禁止离线登录
        }
        return issueToken(username, mode);
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

    /** GET /auth/login?ticket=...：渲染 AuthMe 账号密码二次验证表单。 */
    public FrameProto.HttpResponseFrame serveLoginPage(String ticket) {
        String player = (ticket == null) ? null : loginTickets.get(ticket);
        if (player == null) {
            return errorPage(400, "登录票据无效或已失效，请重新登录游戏以获取新的网页登录链接");
        }
        return htmlFrame(200, loginFormHtml(ticket, player));
    }

    /** POST /auth/issue：校验 AuthMe 密码，验证通过下发会话 Cookie。 */
    public FrameProto.HttpResponseFrame issue(String ticket, String password) {
        String player = (ticket == null) ? null : loginTickets.remove(ticket);
        if (player == null) {
            return errorPage(400, "登录票据无效或已使用，请重新登录游戏以获取新的网页登录链接");
        }
        if (loginProvider == null) {
            return errorPage(503, "未接入登录插件，无法验证密码（请确认服务器已加载 AuthMe 等登录插件）");
        }
        boolean ok;
        try {
            ok = loginProvider.verifyPassword(player, password);
        } catch (Throwable t) {
            ok = false;
        }
        if (!ok) {
            return errorPage(401, "账号或密码错误（AuthMe 校验失败，或服务器未安装 AuthMe，或禁止离线登录）");
        }
        String token = playerTokens.get(player);
        if (token == null) {
            // 兜底：理论上 LoginEvent 已签发；此处重新签发以确保可用
            token = issuer.issueToken(player, LoginMode.ONLINE);
            playerTokens.put(player, token);
        }
        String cookie = issuer.getCookieName() + "=" + token
                + "; Path=/; Max-Age=" + issuer.getTtlSeconds()
                + "; HttpOnly; SameSite=Lax";
        return FrameProto.HttpResponseFrame.newBuilder()
                .setStatusCode(200)
                .putHeaders("Content-Type", "text/html; charset=utf-8")
                .putHeaders("Set-Cookie", cookie)
                .setBody(ByteString.copyFrom(successHtml(player).getBytes(StandardCharsets.UTF_8)))
                .setFragmentIndex(0)
                .setTotalFragments(1)
                .build();
    }

    // ===== HTML 页面 =====

    private static String loginFormHtml(String ticket, String player) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html><html lang=zh><head><meta charset=utf-8>");
        sb.append("<meta name=viewport content=\"width=device-width,initial-scale=1\">");
        sb.append("<title>HTTP-Over-MC 网页登录</title>");
        sb.append("<style>body{margin:0;background:#0a0a12;color:#cfe;font-family:system-ui,Segoe UI,Arial,sans-serif;");
        sb.append("display:flex;align-items:center;justify-content:center;min-height:100vh}");
        sb.append(".card{background:#12121f;border:1px solid #1f6feb55;border-radius:12px;padding:28px 32px;width:340px;box-shadow:0 0 24px #1f6feb33}");
        sb.append("h1{margin:0 0 4px;font-size:18px;color:#5cf}small{color:#789}p{color:#9ab}");
        sb.append("input{width:100%;box-sizing:border-box;padding:10px;margin:10px 0;border-radius:8px;border:1px solid #2a2a40;");
        sb.append("background:#0a0a12;color:#cfe;font-size:14px}");
        sb.append("button{width:100%;padding:11px;border:0;border-radius:8px;background:#1f6feb;color:#fff;font-size:15px;cursor:pointer}");
        sb.append("button:hover{background:#3b82f6}.hint{color:#f96;font-size:12px;min-height:16px}</style></head>");
        sb.append("<body><div class=card><h1>HTTP-Over-MC 网页登录</h1>");
        sb.append("<small>账号 ").append(escape(player)).append(" · 请输入 AuthMe 密码完成二次验证</small>");
        sb.append("<form method=POST action=/auth/issue>");
        sb.append("<input type=hidden name=ticket value=\"").append(escape(ticket)).append("\">");
        sb.append("<input type=password name=password placeholder=\"AuthMe 密码\" autofocus required>");
        sb.append("<div class=hint></div><button type=submit>验证并获取访问令牌</button></form>");
        sb.append("<p>验证通过后将把会话令牌写入浏览器 Cookie，之后访问本服 HTTP 接口即自动带鉴权。</p>");
        sb.append("</div></body></html>");
        return sb.toString();
    }

    private static String successHtml(String player) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html><html lang=zh><head><meta charset=utf-8>");
        sb.append("<meta name=viewport content=\"width=device-width,initial-scale=1\"><title>登录成功</title>");
        sb.append("<style>body{margin:0;background:#0a0a12;color:#cfe;font-family:system-ui,Segoe UI,Arial,sans-serif;");
        sb.append("display:flex;align-items:center;justify-content:center;min-height:100vh}");
        sb.append(".card{background:#12121f;border:1px solid #2ec27e55;border-radius:12px;padding:28px 32px;width:340px;text-align:center;box-shadow:0 0 24px #2ec27e33}");
        sb.append("h1{color:#2ec27e;margin:0 0 8px}a{color:#5cf}</style></head>");
        sb.append("<body><div class=card><h1>✓ 登录成功</h1>");
        sb.append("<p>玩家 ").append(escape(player)).append(" 的会话令牌已写入浏览器 Cookie。</p>");
        sb.append("<p><a href=\"/\">前往控制台首页</a></p>");
        sb.append("<p><small>现在可直接访问本服 HTTP 接口（如 /api/status），无需再手动携带令牌。</small></p>");
        sb.append("</div></body></html>");
        return sb.toString();
    }

    private FrameProto.HttpResponseFrame errorPage(int code, String msg) {
        String html = "<!doctype html><html lang=zh><head><meta charset=utf-8><title>错误</title>"
                + "<style>body{margin:0;background:#0a0a12;color:#cfe;font-family:system-ui,Arial,sans-serif;"
                + "display:flex;align-items:center;justify-content:center;min-height:100vh}"
                + ".card{background:#12121f;border:1px solid #f9655555;border-radius:12px;padding:24px 28px;width:340px;text-align:center}"
                + "h1{color:#f96;margin:0 0 8px}small{color:#789}</style></head><body><div class=card>"
                + "<h1>无法登录</h1><p>" + escape(msg) + "</p>"
                + "<p><small>请重新登录游戏以获取新的网页登录链接。</small></p></div></body></html>";
        return htmlFrame(code, html);
    }

    private static FrameProto.HttpResponseFrame htmlFrame(int code, String html) {
        return FrameProto.HttpResponseFrame.newBuilder()
                .setStatusCode(code)
                .putHeaders("Content-Type", "text/html; charset=utf-8")
                .setBody(ByteString.copyFrom(html.getBytes(StandardCharsets.UTF_8)))
                .setFragmentIndex(0)
                .setTotalFragments(1)
                .build();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
