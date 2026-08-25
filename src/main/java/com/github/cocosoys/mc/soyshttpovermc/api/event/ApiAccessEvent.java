package com.github.cocosoys.mc.soyshttpovermc.api.event;

import com.github.cocosoys.mc.soyshttpovermc.gateway.policy.auth.issuer.CredentialPresentation;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

/**
 * API 访问监听事件（基类）：一条注解式 API 请求命中路由并<b>通过权限判定</b>后、
 * 处理器调用前触发（worker 线程 → 由网关切回主线程发射）。
 *
 * <p><b>监听方式</b>（基类与子类共享同一 HandlerList）：
 * <ul>
 *   <li>监听 {@link ApiAccessEvent} —— 收到<b>全部</b> API 访问（公开 + 权限，GET/POST/... 所有方法）；</li>
 *   <li>监听 {@link ApiGetEvent} / {@link ApiPostEvent} / {@link ApiPutEvent} / {@link ApiDeleteEvent} /
 *       {@link ApiPatchEvent} / {@link ApiOtherEvent} —— 只收到<b>对应请求类型</b>的访问。</li>
 * </ul></p>
 *
 * <p><b>公开 / 权限区分</b>：{@link #isPublic()}（permission 为空 = 公开端点）；权限端点的事件可通过
 * {@link #getPlayerName()} / {@link #getPlayer()} 直接拿到<b>实时</b>玩家实体，或通过 {@link #getAsyncPlayer()}
 * 拿到派发时刻的玩家快照（经 token/cookie 解析；离线时玩家实体为 null），无需再手动解析凭证。</p>
 *
 * <p><b>玩家字段（异步线程模型）</b>：事件在 worker 线程构造、由网关切回主线程发射，监听器必定在主线程收到事件。
 * 因此 {@link #getPlayer()} 在监听器中始终<b>实时</b>（主线程直接解析），反映监听器执行时刻的真实玩家状态；
 * {@link #getAsyncPlayer()} 则是请求派发时刻的快照，若玩家期间离线可能已悬空，仅用于比对。</p>
 */
public class ApiAccessEvent extends Event {

    /** 共享 HandlerList：子类（GET/POST/...）与基类同用一个，保证"监听基类收全部、监听子类收对应方法"。 */
    private static final HandlerList HANDLERS = new HandlerList();

    private final String httpMethod;       // 实际请求方法（GET/POST/...）
    private final String path;             // 完整路径（含 api-prefix，如 /api/status）
    private final String apiName;
    private final String permission;       // 空 = 公开端点
    private final String ownerPlugin;
    private final boolean authenticated;   // 请求是否携带有效凭证（credential != null）
    private final String playerName;       // 经 token/cookie 解析的玩家名（无凭证/非玩家令牌=null）
    private final Plugin plugin;           // 宿主插件（用于非主线程下实时取玩家的 runTask 退路）
    /** 派发时刻快照：请求进入 worker 线程时一次性解析的玩家实体，可能已离线/悬空（仅供比对与向后兼容）。 */
    private final Player asyncPlayer;      // 在线玩家快照（离线=null）
    private final CredentialPresentation credential;

    protected ApiAccessEvent(Plugin plugin, String httpMethod, String path, String apiName, String permission,
                             String ownerPlugin, boolean authenticated, String playerName,
                             Player asyncPlayer, CredentialPresentation credential) {
        super(true); // 非异步（同步事件，网关已在主线程发射）
        this.plugin = plugin;
        this.httpMethod = httpMethod == null ? "" : httpMethod;
        this.path = path == null ? "/" : path;
        this.apiName = apiName == null ? "" : apiName;
        this.permission = permission == null ? "" : permission;
        this.ownerPlugin = ownerPlugin == null ? "" : ownerPlugin;
        this.authenticated = authenticated;
        this.playerName = playerName;
        this.asyncPlayer = asyncPlayer;
        this.credential = credential;
    }

    /** 按实际请求方法构造对应子类事件（未识别方法 → ApiOtherEvent）。 */
    public static ApiAccessEvent forMethod(Plugin hostPlugin, String httpMethod, String path, String apiName, String permission,
                                           String ownerPlugin, boolean authenticated, String playerName,
                                           Player asyncPlayer, CredentialPresentation credential) {
        String m = httpMethod == null ? "" : httpMethod.toUpperCase();
        switch (m) {
            case "GET":
                return new ApiGetEvent(hostPlugin, path, apiName, permission, ownerPlugin, authenticated, playerName, asyncPlayer, credential);
            case "POST":
                return new ApiPostEvent(hostPlugin, path, apiName, permission, ownerPlugin, authenticated, playerName, asyncPlayer, credential);
            case "PUT":
                return new ApiPutEvent(hostPlugin, path, apiName, permission, ownerPlugin, authenticated, playerName, asyncPlayer, credential);
            case "DELETE":
                return new ApiDeleteEvent(hostPlugin, path, apiName, permission, ownerPlugin, authenticated, playerName, asyncPlayer, credential);
            case "PATCH":
                return new ApiPatchEvent(hostPlugin, path, apiName, permission, ownerPlugin, authenticated, playerName, asyncPlayer, credential);
            default:
                return new ApiOtherEvent(hostPlugin, m, path, apiName, permission, ownerPlugin, authenticated, playerName, asyncPlayer, credential);
        }
    }

    // ===== getters =====

    /** 实际请求方法（GET/POST/...）。 */
    public String getHttpMethod() {
        return httpMethod;
    }

    /** 完整路径（含 api-prefix，如 /api/status）。 */
    public String getPath() {
        return path;
    }

    /** 端点名称（@ApiName）。 */
    public String getApiName() {
        return apiName;
    }

    /** 端点权限（@ApiPermission）；空 = 公开端点。 */
    public String getPermission() {
        return permission;
    }

    /** 是否为公开端点（无权限要求）。 */
    public boolean isPublic() {
        return permission.isEmpty();
    }

    /** 注册该 API 的插件名。 */
    public String getOwnerPlugin() {
        return ownerPlugin;
    }

    /** 请求是否携带有效凭证。 */
    public boolean isAuthenticated() {
        return authenticated;
    }

    /** 经 token/cookie 解析出的玩家名（无凭证 / 非玩家令牌 = null）。 */
    public String getPlayerName() {
        return playerName;
    }

    /**
     * <b>实时</b>在线玩家实体：每次调用都在主线程按 {@link #getPlayerName()} 重新解析，反映<b>监听器执行时刻</b>的真实状态
     * （本事件由网关切回主线程发射，监听器必在主线程收到，故此处取玩家总是实时且线程安全）。玩家离线或
     * {@link #getPlayerName()} 为 null 时返回 null。若需在"派发时刻"的值，请用 {@link #getAsyncPlayer()}。
     */
    public Player getPlayer() {
        if (playerName == null) return null;
        if (Bukkit.isPrimaryThread()) {
            return Bukkit.getPlayerExact(playerName);
        }
        if (plugin == null) {
            return Bukkit.getPlayerExact(playerName); // 退路：非主线程直接取（可能线程不安全，仅兜底）
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

    /**
     * 派发时刻<b>快照</b>：请求进入 worker 线程时一次性解析出的玩家实体，不会随 {@link #getPlayer()} 调用刷新。
     * 若玩家在请求处理期间离线，该引用可能已悬空（实体被世界移除），再调用其多数方法会抛 {@code IllegalStateException}。
     * 仅用于与实时玩家比对；<b>请勿用于调用会随玩家状态变化的业务方法</b>。
     */
    public Player getAsyncPlayer() {
        return asyncPlayer;
    }

    /** 请求解析出的凭证（可为 null）。 */
    public CredentialPresentation getCredential() {
        return credential;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    /** API 访问监听事件：DELETE 请求类型（监听本类只收到 DELETE 访问；监听基类收全部）。 */
    public static class ApiDeleteEvent extends ApiAccessEvent {

        public ApiDeleteEvent(Plugin hostPlugin, String path, String apiName, String permission, String ownerPlugin,
                      boolean authenticated, String playerName, Player asyncPlayer, CredentialPresentation credential) {
            super(hostPlugin, "DELETE", path, apiName, permission, ownerPlugin, authenticated, playerName, asyncPlayer, credential);
        }

        public static HandlerList getHandlerList() {
            return ApiAccessEvent.getHandlerList();
        }

        @Override
        public HandlerList getHandlers() {
            return ApiAccessEvent.getHandlerList();
        }
    }

    /** API 访问监听事件：PUT 请求类型（监听本类只收到 PUT 访问；监听基类收全部）。 */
    public static class ApiPutEvent extends ApiAccessEvent {

        public ApiPutEvent(Plugin hostPlugin, String path, String apiName, String permission, String ownerPlugin,
                      boolean authenticated, String playerName, Player asyncPlayer, CredentialPresentation credential) {
            super(hostPlugin, "PUT", path, apiName, permission, ownerPlugin, authenticated, playerName, asyncPlayer, credential);
        }

        public static HandlerList getHandlerList() {
            return ApiAccessEvent.getHandlerList();
        }

        @Override
        public HandlerList getHandlers() {
            return ApiAccessEvent.getHandlerList();
        }
    }

    /** API 访问监听事件：POST 请求类型（监听本类只收到 POST 访问；监听基类收全部）。 */
    public static class ApiPostEvent extends ApiAccessEvent {

        public ApiPostEvent(Plugin hostPlugin, String path, String apiName, String permission, String ownerPlugin,
                      boolean authenticated, String playerName, Player asyncPlayer, CredentialPresentation credential) {
            super(hostPlugin, "POST", path, apiName, permission, ownerPlugin, authenticated, playerName, asyncPlayer, credential);
        }

        public static HandlerList getHandlerList() {
            return ApiAccessEvent.getHandlerList();
        }

        @Override
        public HandlerList getHandlers() {
            return ApiAccessEvent.getHandlerList();
        }
    }

    /** API 访问监听事件：PATCH 请求类型（监听本类只收到 PATCH 访问；监听基类收全部）。 */
    public static class ApiPatchEvent extends ApiAccessEvent {

        public ApiPatchEvent(Plugin hostPlugin, String path, String apiName, String permission, String ownerPlugin,
                      boolean authenticated, String playerName, Player asyncPlayer, CredentialPresentation credential) {
            super(hostPlugin, "PATCH", path, apiName, permission, ownerPlugin, authenticated, playerName, asyncPlayer, credential);
        }

        public static HandlerList getHandlerList() {
            return ApiAccessEvent.getHandlerList();
        }

        @Override
        public HandlerList getHandlers() {
            return ApiAccessEvent.getHandlerList();
        }
    }

    /** API 访问监听事件：OTHER(未识别) 请求类型（监听本类只收到 OTHER(未识别) 访问；监听基类收全部）。 */
    public static class ApiOtherEvent extends ApiAccessEvent {

        public ApiOtherEvent(Plugin hostPlugin, String httpMethod, String path, String apiName, String permission, String ownerPlugin,
                      boolean authenticated, String playerName, Player asyncPlayer, CredentialPresentation credential) {
            super(hostPlugin, httpMethod, path, apiName, permission, ownerPlugin, authenticated, playerName, asyncPlayer, credential);
        }

        public static HandlerList getHandlerList() {
            return ApiAccessEvent.getHandlerList();
        }

        @Override
        public HandlerList getHandlers() {
            return ApiAccessEvent.getHandlerList();
        }
    }

    /** API 访问监听事件：GET 请求类型（监听本类只收到 GET 访问；监听基类收全部）。 */
    public static class ApiGetEvent extends ApiAccessEvent {

        public ApiGetEvent(Plugin hostPlugin, String path, String apiName, String permission, String ownerPlugin,
                      boolean authenticated, String playerName, Player asyncPlayer, CredentialPresentation credential) {
            super(hostPlugin, "GET", path, apiName, permission, ownerPlugin, authenticated, playerName, asyncPlayer, credential);
        }

        public static HandlerList getHandlerList() {
            return ApiAccessEvent.getHandlerList();
        }

        @Override
        public HandlerList getHandlers() {
            return ApiAccessEvent.getHandlerList();
        }
    }
}
