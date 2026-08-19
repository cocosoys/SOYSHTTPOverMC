package soys.soyshttpovermc.api;

import soys.soyshttpovermc.gateway.policy.auth.issuer.CredentialPresentation;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

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
 *           "online", ctx.getPlayer() != null, // 实时玩家实体（离线=null）
 *           "path", ctx.getPath()));
 *   }
 * </pre>
 *
 * <p>玩家字段说明（异步线程模型下的关键注意点）：
 * <ul>
 *   <li>{@link #getPlayerName()} —— 经 token/cookie 解析的玩家名（String，永不悬空、永不过期，是稳定的实时锚点）；未登录/非玩家令牌为 null。</li>
 *   <li>{@link #getPlayer()} —— <b>实时</b>玩家实体：每次调用都在主线程重新按玩家名解析，反映<b>调用时刻</b>的真实状态；
 *       在 worker 线程调用会通过 {@code runTask} 阻塞切回主线程取值（占用当前 worker 直至下一 tick），玩家离线返回 null。
 *       这是 handler 层获取"此刻真实玩家"的统一辅助 API。</li>
 *   <li>{@link #getAsyncPlayer()} —— 派发时刻<b>快照</b>：请求进入 worker 线程时一次性解析的玩家实体，
 *       不会随调用刷新；若玩家在请求处理期间离线，该引用可能已悬空（实体被移除），仅用于与实时玩家比对或向后兼容，
 *       <b>请勿用于调用会随玩家状态变化的业务方法</b>。</li>
 * </ul>
 * 区别：{@code getPlayer()} 永远"最新"，{@code getAsyncPlayer()} 是"定格"。若 handler 内 sleep/耗时后想用最新状态，用 {@code getPlayer()}。</p>
 *
 * <p>字段说明：
 * <ul>
 *   <li>{@link #getIp()} —— 客户端 IP（网关在 SocketSniffer 端注入内部头 X-Soys-Remote-Ip 传递）；</li>
 *   <li>{@link #getCredential()} —— 请求解析出的凭证（可为 null）；</li>
 *   <li>{@link #getHeaders()} / {@link #getHttpMethod()} / {@link #getPath()} —— 请求原信息；</li>
 *   <li>{@link #getSourceServer()} / {@link #getTraceId()} —— 群组服跨服关联：请求来源服名与链路追踪 ID
 *       （经 X-Soys-Source-Server / X-Soys-Trace-Id 头传递；独立服为 null）。</li>
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
    private final Plugin plugin;
    private final String playerName;
    /** 派发时刻快照：worker 线程在请求派发开始时一次性解析的玩家实体，可能已离线/悬空（仅供比对与向后兼容）。 */
    private final Player asyncPlayer;
    private final boolean authenticated;
    /** 跨服请求来源服名（独立服 / 本服直连为 null） */
    private final String sourceServer;
    /** 跨服链路追踪 ID（无关联为 null） */
    private final String traceId;

    public ApiRequestContext(Plugin plugin, String httpMethod, String path, String ip, Map<String, String> headers,
                             CredentialPresentation credential, String playerName, Player asyncPlayer,
                             boolean authenticated) {
        this(plugin, httpMethod, path, ip, headers, credential, playerName, asyncPlayer, authenticated, null, null);
    }

    public ApiRequestContext(Plugin plugin, String httpMethod, String path, String ip, Map<String, String> headers,
                             CredentialPresentation credential, String playerName, Player player,
                             boolean authenticated, String sourceServer, String traceId) {
        this.plugin = plugin;
        this.httpMethod = httpMethod == null ? "" : httpMethod;
        this.path = path == null ? "/" : path;
        this.ip = ip == null ? "0.0.0.0" : ip;
        this.headers = headers == null ? Collections.<String, String>emptyMap() : headers;
        this.credential = credential;
        this.playerName = playerName;
        this.asyncPlayer = player;
        this.authenticated = authenticated;
        this.sourceServer = sourceServer;
        this.traceId = traceId;
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

    /**
     * <b>实时</b>在线玩家实体：每次调用都在主线程按 {@link #getPlayerName()} 重新解析，反映<b>调用时刻</b>的真实状态。
     * 这是 handler 层获取"此刻真实玩家"的统一辅助 API。
     *
     * <p>线程模型：本上下文在 worker 线程（非主线程）构造与注入。若在 worker 线程调用本方法，
     * 会通过 {@code Bukkit.getScheduler().runTask} 阻塞切回主线程取值，<b>占用当前 worker 线程直至下一 tick</b>；
     * 高并发 handler 内频繁调用会放大 worker 占用、甚至引发队列背压（回 503），请按需取用、勿在循环内滥用。
     * 若玩家已离线或 {@link #getPlayerName()} 为 null，返回 null（不会抛异常）。</p>
     */
    public Player getSyncPlayer() {
        if (playerName == null) return null;
        if (Bukkit.isPrimaryThread()) {
            return Bukkit.getPlayerExact(playerName);
        }
        if (plugin == null) {
            // 退路：无宿主插件引用时直接取（非主线程下可能线程不安全，仅兜底）
            return Bukkit.getPlayerExact(playerName);
        }
        FutureTask<Player> task = new FutureTask<>(() -> Bukkit.getPlayerExact(playerName));
        plugin.getServer().getScheduler().runTask(plugin, task);
        try {
            return task.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException e) {
            return null;
        }
    }

    public Player getPlayer() {
        return asyncPlayer;
    }

    /**
     * 派发时刻<b>快照</b>：请求进入 worker 线程时一次性解析出的玩家实体，不会随 {@link #getPlayer()} 调用刷新。
     * 若玩家在请求处理期间离线，该引用可能已悬空（实体被世界移除），再调用其多数方法会抛 {@code IllegalStateException}。
     * 仅用于与实时玩家比对或向后兼容；<b>请勿用于调用会随玩家状态变化的业务方法</b>。
     */
    public Player getAsyncPlayer() {
        return asyncPlayer;
    }

    /** 请求是否携带有效凭证。 */
    public boolean isAuthenticated() {
        return authenticated;
    }

    /** 跨服请求来源服名（独立服 / 本服直连为 null）。 */
    public String getSourceServer() {
        return sourceServer;
    }

    /** 跨服链路追踪 ID（无关联为 null）。 */
    public String getTraceId() {
        return traceId;
    }
}
