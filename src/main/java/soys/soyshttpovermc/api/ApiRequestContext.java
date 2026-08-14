package soys.soyshttpovermc.api;

import soys.soyshttpovermc.gateway.policy.auth.issuer.CredentialPresentation;

import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.Map;

/**
 * API 请求上下文：注解式 API 处理器参数类型为 {@link ApiRequestContext} 时，
 * 网关自动注入当前请求的完整上下文——<b>开发者无需自行解析请求头/凭证/令牌</b>：
 *
 * <pre>
 *   &#64;GetMapping("/whoami")
 *   public AjaxResult whoami(ApiRequestContext ctx) {
 *       return AjaxResult.success(ImmutableMap.of(
 *           "ip", ctx.getIp(),            // 客户端 IP
 *           "player", ctx.getPlayerName(),// 经 token/cookie 解析的玩家名（未登录=null）
 *           "online", ctx.getPlayer() != null, // 玩家实体（离线=null）
 *           "path", ctx.getPath()));
 *   }
 * </pre>
 *
 * <p>字段说明：
 * <ul>
 *   <li>{@link #getIp()} —— 客户端 IP（网关在 SocketSniffer 端注入内部头 X-Soys-Remote-Ip 传递）；</li>
 *   <li>{@link #getPlayerName()} / {@link #getPlayer()} —— 经请求凭证（token/cookie）解析出的玩家；
 *       未登录/非玩家令牌为 null，玩家离线时实体为 null；</li>
 *   <li>{@link #getCredential()} —— 请求解析出的凭证（可为 null）；</li>
 *   <li>{@link #getHeaders()} / {@link #getHttpMethod()} / {@link #getPath()} —— 请求原信息。</li>
 * </ul>
 */
public final class ApiRequestContext {

    /** SocketSniffer 注入的内部客户端 IP 头（仅内部使用，不对外转发）。 */
    public static final String HEADER_REMOTE_IP = "X-Soys-Remote-Ip";

    private final String httpMethod;
    private final String path;
    private final String ip;
    private final Map<String, String> headers;
    private final CredentialPresentation credential;
    private final String playerName;
    private final Player player;
    private final boolean authenticated;

    public ApiRequestContext(String httpMethod, String path, String ip, Map<String, String> headers,
                             CredentialPresentation credential, String playerName, Player player,
                             boolean authenticated) {
        this.httpMethod = httpMethod == null ? "" : httpMethod;
        this.path = path == null ? "/" : path;
        this.ip = ip == null ? "0.0.0.0" : ip;
        this.headers = headers == null ? Collections.<String, String>emptyMap() : headers;
        this.credential = credential;
        this.playerName = playerName;
        this.player = player;
        this.authenticated = authenticated;
    }

    /** 实际请求方法（GET/POST/...）。 */
    public String getHttpMethod() {
        return httpMethod;
    }

    /** 完整路径（含 api-prefix，如 /api/status）。 */
    public String getPath() {
        return path;
    }

    /** 客户端 IP（0.0.0.0=未知）。 */
    public String getIp() {
        return ip;
    }

    /** 请求头（只读视图）。 */
    public Map<String, String> getHeaders() {
        return headers;
    }

    /** 请求凭证（可为 null）。 */
    public CredentialPresentation getCredential() {
        return credential;
    }

    /** 经 token/cookie 解析的玩家名（未登录/非玩家令牌=null）。 */
    public String getPlayerName() {
        return playerName;
    }

    /** 在线玩家实体（玩家离线=null）。 */
    public Player getPlayer() {
        return player;
    }

    /** 请求是否携带有效凭证。 */
    public boolean isAuthenticated() {
        return authenticated;
    }
}
