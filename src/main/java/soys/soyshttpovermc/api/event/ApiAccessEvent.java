package soys.soyshttpovermc.api.event;

import soys.soyshttpovermc.gateway.policy.auth.issuer.CredentialPresentation;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

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
 * {@link #getPlayerName()} / {@link #getPlayer()} 直接拿到<b>经 token/cookie 解析出的玩家</b>
 * （离线时玩家实体为 null），无需再手动解析凭证。</p>
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
    private final Player player;           // 在线玩家实体（离线=null）
    private final CredentialPresentation credential;

    protected ApiAccessEvent(String httpMethod, String path, String apiName, String permission,
                             String ownerPlugin, boolean authenticated, String playerName,
                             Player player, CredentialPresentation credential) {
        super(true); // 非异步（同步事件，网关已在主线程发射）
        this.httpMethod = httpMethod == null ? "" : httpMethod;
        this.path = path == null ? "/" : path;
        this.apiName = apiName == null ? "" : apiName;
        this.permission = permission == null ? "" : permission;
        this.ownerPlugin = ownerPlugin == null ? "" : ownerPlugin;
        this.authenticated = authenticated;
        this.playerName = playerName;
        this.player = player;
        this.credential = credential;
    }

    /** 按实际请求方法构造对应子类事件（未识别方法 → ApiOtherEvent）。 */
    public static ApiAccessEvent forMethod(String httpMethod, String path, String apiName, String permission,
                                           String ownerPlugin, boolean authenticated, String playerName,
                                           Player player, CredentialPresentation credential) {
        String m = httpMethod == null ? "" : httpMethod.toUpperCase();
        switch (m) {
            case "GET":
                return new ApiGetEvent(path, apiName, permission, ownerPlugin, authenticated, playerName, player, credential);
            case "POST":
                return new ApiPostEvent(path, apiName, permission, ownerPlugin, authenticated, playerName, player, credential);
            case "PUT":
                return new ApiPutEvent(path, apiName, permission, ownerPlugin, authenticated, playerName, player, credential);
            case "DELETE":
                return new ApiDeleteEvent(path, apiName, permission, ownerPlugin, authenticated, playerName, player, credential);
            case "PATCH":
                return new ApiPatchEvent(path, apiName, permission, ownerPlugin, authenticated, playerName, player, credential);
            default:
                return new ApiOtherEvent(m, path, apiName, permission, ownerPlugin, authenticated, playerName, player, credential);
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

    /** 在线玩家实体（玩家离线 = null；配合 getPlayerName 使用）。 */
    public Player getPlayer() {
        return player;
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
}
