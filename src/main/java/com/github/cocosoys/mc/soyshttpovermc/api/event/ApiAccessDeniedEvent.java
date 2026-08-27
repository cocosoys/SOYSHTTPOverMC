package com.github.cocosoys.mc.soyshttpovermc.api.event;

import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.issuer.CredentialPresentation;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Map;

/**
 * API 访问被拒事件：一条注解式 API 请求命中路由、但因<b>权限不足（403）</b>被网关拒绝时触发
 * （worker 线程 → 由网关切回主线程发射）。
 *
 * <p><b>典型用途</b>：离线玩家在网页中操作需要权限的接口 → 当前离线、{@code hasPermission} 无法判定 → 返回 403；
 * 监听本事件可把"接口 + 请求参数 + 请求头"暂存为待补执行任务，待玩家上线后由
 * {@code PendingActionManager} 自动重放该请求以完成业务逻辑（如发放物品、执行指令）。</p>
 *
 * <p><b>字段携带</b>：事件完整携带重放所需的全部信息——请求方法 / 原始路径(含 query) / 权限 / 所属插件 /
 * 玩家名 / 凭证 / 请求头 / 请求体 / offline 标记，监听器可直接据此重放，无需再次解析。</p>
 *
 * <p><b>线程模型</b>：与 {@link ApiAccessEvent} 一致，由网关切回主线程发射，监听器必在主线程收到事件。</p>
 */
public class ApiAccessDeniedEvent extends Event {

    /** 共享 HandlerList（与 ApiAccessEvent 同构，便于统一事件治理）。 */
    private static final HandlerList HANDLERS = new HandlerList();

    private final String httpMethod;
    private final String rawPath;          // 原始路径（含 query 串，便于 @RequestParam 重放）
    private final String apiName;
    private final String permission;       // 被拒端点要求的权限
    private final String ownerPlugin;
    private final boolean authenticated;   // 请求是否携带有效凭证
    private final String playerName;       // 经 token/cookie 解析的玩家名（无凭证/非玩家令牌=null）
    private final boolean offline;         // 玩家是否离线（true=需要待补执行）
    private final CredentialPresentation credential;
    private final int statusCode;          // 拒绝状态码（403）
    private final String reason;           // 拒绝原因（人类可读，用于审计日志）
    private final byte[] body;             // 完整请求体（重放用）
    private final Map<String, String> headers; // 完整请求头（重放用）

    public ApiAccessDeniedEvent(String httpMethod, String rawPath, String apiName,
                                String permission, String ownerPlugin, boolean authenticated,
                                String playerName, boolean offline, CredentialPresentation credential,
                                int statusCode, String reason, byte[] body, Map<String, String> headers) {
        super(true); // 非异步（同步事件，网关已在主线程发射）
        this.httpMethod = httpMethod == null ? "" : httpMethod;
        this.rawPath = rawPath == null ? "/" : rawPath;
        this.apiName = apiName == null ? "" : apiName;
        this.permission = permission == null ? "" : permission;
        this.ownerPlugin = ownerPlugin == null ? "" : ownerPlugin;
        this.authenticated = authenticated;
        this.playerName = playerName;
        this.offline = offline;
        this.credential = credential;
        this.statusCode = statusCode;
        this.reason = reason == null ? "" : reason;
        this.body = body;
        this.headers = headers;
    }

    // ===== getters =====

    /** 实际请求方法（GET/POST/...）。 */
    public String getHttpMethod() {
        return httpMethod;
    }

    /** 原始路径（含 query 串，保证 @RequestParam 重放正确）。 */
    public String getRawPath() {
        return rawPath;
    }

    /** 端点名称（@ApiName）。 */
    public String getApiName() {
        return apiName;
    }

    /** 端点要求的权限（@ApiPermission）。 */
    public String getPermission() {
        return permission;
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

    /** 玩家离线标记（true=离线、需待上线补执行）。 */
    public boolean isOffline() {
        return offline;
    }

    /** 请求解析出的凭证（可为 null）。 */
    public CredentialPresentation getCredential() {
        return credential;
    }

    /** 拒绝状态码（403）。 */
    public int getStatusCode() {
        return statusCode;
    }

    /** 拒绝原因（人类可读）。 */
    public String getReason() {
        return reason;
    }

    /** 完整请求体（重放用）。 */
    public byte[] getBody() {
        return body;
    }

    /** 完整请求头（重放用）。 */
    public Map<String, String> getHeaders() {
        return headers;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
