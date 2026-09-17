package com.github.cocosoys.mc.soyshttpovermc.api.event;

import com.github.cocosoys.mc.soyshttpovermc.web.ApiInfo;
import lombok.Getter;
import org.bukkit.event.HandlerList;
import java.util.Collections;
import java.util.List;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.issuer.CredentialPresentation;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.Map;

/**
 * API 事件抽象基类：SOYS 注解式 API 全生命周期事件的总入口。
 *
 * <ul>
 *   <li>{@link ApiEvent.ApiRegisteredEvent} —— API 注册完成（同步，主线程 onEnable 触发）</li>
 *   <li>{@link ApiEvent.ApiUnregisteredEvent} —— API 卸载完成（同步，主线程 onDisable / 显式卸载触发）</li>
 *   <li>{@link ApiEvent.ApiAccessEvent} —— API 访问（前）：权限判定通过后、处理器调用前（异步，HTTP worker 线程）</li>
 *   <li>{@link ApiEvent.ApiAccessCompletedEvent} —— API 处理完成（后）：含被拒 / 异常 / 成功（异步，HTTP worker 线程）</li>
 * </ul>
 *
 * <p>其他插件用 Bukkit 标准方式监听（事件类型为对应嵌套类）：</p>
 * <pre>{@code
 * getServer().getPluginManager().registerEvents(new Listener() {
 *     @EventHandler
 *     public void onAccess(ApiEvent.ApiAccessEvent e) {
 *         // ...
 *     }
 * }, yourPlugin);
 * }</pre>
 */
public abstract class ApiEvent extends Event {

    /**
     * 默认异步构造（HTTP 处理线程触发；兼容无参调用）。
     */
    protected ApiEvent() {
        super(true);
    }

    /**
     * @param async true=异步（HTTP worker 线程），false=同步（主线程）
     */
    protected ApiEvent(boolean async) {
        super(async);
    }

    // ================================================================
    // 嵌套事件：API 注册 / 卸载 / 访问（前）/ 处理完成（后）
    // ================================================================





    /**
     * API 注册事件：某插件通过 {@code ApiRegistry.register(...)} 注册一批注解式 API 端点后触发。
     * 携带<b>注册插件名</b>与该批端点的清单（方法 / 路径 / 端点名 / 权限 / 处理器类）。
     *
     * <p>其他插件可监听此事件做路由审计、自动生成 OpenAPI 文档、权限联动、灰度开关等。
     * 网关在注册时<b>自动标记</b>注册 API 的插件名（按处理器实例的 ClassLoader 归属），无需调用方手动传入。</p>
     *
     * <p>本事件为<b>同步事件</b>：通常在插件 {@code onEnable}（主线程）触发，
     * 1.12.2 不允许从主线程触发异步事件（会抛 IllegalStateException），故强制同步。</p>
     */
    @Getter
        public static class ApiRegisteredEvent extends ApiEvent {

        private static final HandlerList HANDLERS = new HandlerList();

        /**
         * 注册这批 API 的插件名（由网关自动标记）
         */
        private final String ownerPlugin;
        /**
         * 本次注册的端点清单
         */
        private final List<ApiInfo> apis;

        public ApiRegisteredEvent(String ownerPlugin, List<ApiInfo> apis) {
            super(false); // 同步：注册通常在主线程（onEnable）触发
            this.ownerPlugin = ownerPlugin == null ? "" : ownerPlugin;
            this.apis = apis == null ? Collections.<ApiInfo>emptyList() : apis;
        }

        public static HandlerList getHandlerList() {
            return HANDLERS;
        }

        @Override
        public HandlerList getHandlers() {
            return HANDLERS;
        }
    }





    /**
     * API 卸载事件：某插件注册的注解式 API 端点被移除后触发——两种来源：
     * <ul>
     *   <li>插件卸载：监听 {@code PluginDisableEvent}，网关<b>自动卸载</b>该插件名下全部 API；</li>
     *   <li>显式调用：插件在 {@code onDisable} 中调用 {@code ApiRegistry.unregister(instance) / unregisterPlugin(name)}。</li>
     * </ul>
     * 携带<b>注册插件名</b>与被移除端点的清单。其他插件可监听此事件清理关联资源、撤销缓存等。
     *
     * <p>本事件为<b>同步事件</b>：通常在插件 {@code onDisable}（主线程）触发，
     * 1.12.2 不允许从主线程触发异步事件（会抛 IllegalStateException），故强制同步。</p>
     */
    @Getter
        public static class ApiUnregisteredEvent extends ApiEvent {

        private static final HandlerList HANDLERS = new HandlerList();

        /**
         * 卸载这批 API 的插件名（由网关自动标记）
         */
        private final String ownerPlugin;
        /**
         * 本次卸载的端点清单
         */
        private final List<ApiInfo> apis;

        public ApiUnregisteredEvent(String ownerPlugin, List<ApiInfo> apis) {
            super(false); // 同步：卸载通常在主线程（onDisable）触发
            this.ownerPlugin = ownerPlugin == null ? "" : ownerPlugin;
            this.apis = apis == null ? Collections.<ApiInfo>emptyList() : apis;
        }

        public static HandlerList getHandlerList() {
            return HANDLERS;
        }

        @Override
        public HandlerList getHandlers() {
            return HANDLERS;
        }
    }






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
    @Getter
        public static class ApiAccessEvent extends ApiEvent {

        /**
         * 共享 HandlerList：子类（GET/POST/...）与基类同用一个，保证"监听基类收全部、监听子类收对应方法"。
         */
        private static final HandlerList HANDLERS = new HandlerList();

        /**
         * 实际请求方法（GET/POST/...）。
         */
        private final String httpMethod;
        /**
         * 完整路径（含 api-prefix，如 /api/status）。
         */
        private final String path;
        /**
         * 端点名称（@ApiName）。
         */
        private final String apiName;
        /**
         * 端点权限（@ApiPermission）；空 = 公开端点。
         */
        private final String permission;
        /**
         * 注册该 API 的插件名。
         */
        private final String ownerPlugin;
        /**
         * 请求是否携带有效凭证。
         */
        private final boolean authenticated;
        /**
         * 经 token/cookie 解析出的玩家名（无凭证 / 非玩家令牌 = null）。
         */
        private final String playerName;
        private final Plugin plugin;           // 宿主插件（用于非主线程下实时取玩家的 runTask 退路）
        /**
         * 派发时刻<b>快照</b>：请求进入 worker 线程时一次性解析出的玩家实体，不会随 {@link #getPlayer()} 调用刷新。
         * 若玩家在请求处理期间离线，该引用可能已悬空（实体被世界移除），再调用其多数方法会抛 {@code IllegalStateException}。
         * 仅用于与实时玩家比对；<b>请勿用于调用会随玩家状态变化的业务方法</b>。
         */
        private final Player asyncPlayer;
        /**
         * 请求解析出的凭证（可为 null）。
         */
        private final CredentialPresentation credential;
    /**
     * 请求参数（query 解析结果：@RequestParam 来源）。
     * <p>仅解析 <b>URL query string</b>（? 之后按 &amp; 分割、URL 解码）：
     * 如 /api/status?page=1&amp;size=10 → {page=1, size=10}。</p>
     * <p>三种请求参数形态的行为：</p>
     * <ul>
     *   <li><b>query string</b>（/api/x?a=1&amp;b=2）→ 解析进本字段（URL 解码）；</li>
     *   <li><b>JSON body</b>（{"a":1}）→ <b>不进入</b>本字段：JSON 在请求体中，由 @RequestBody 实体参数
     *       经 JsonReader 绑定，与 URL query 无关；</li>
     *   <li><b>www-form-urlencoded body</b>（a=1&amp;b=2）→ <b>不进入</b>本字段：body 由 @RequestBody
     *       String 参数原样接收；仅当 URL 同时带 query 时本字段才有值。</li>
     * </ul>
     */
        private final Map<String, String> requestParams;

        /**
         * 完整请求体（原始字节：JSON / www-form-urlencoded / 二进制均可；URL query 不在此）。
         */
        private final byte[] body;

        protected ApiAccessEvent(Plugin plugin, String httpMethod, String path, String apiName, String permission,
                                 String ownerPlugin, boolean authenticated, String playerName,
                                 Player asyncPlayer, CredentialPresentation credential, Map<String, String> requestParams, byte[] body) {
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
            this.requestParams = requestParams;
            this.body = body;
        }

        /**
         * 按实际请求方法构造对应子类事件（未识别方法 → ApiOtherEvent）。
         */
        public static ApiAccessEvent forMethod(Plugin hostPlugin, String httpMethod, String path, String apiName, String permission,
                                               String ownerPlugin, boolean authenticated, String playerName,
                                               Player asyncPlayer, CredentialPresentation credential, Map<String, String> requestParams, byte[] body) {
            String m = httpMethod == null ? "" : httpMethod.toUpperCase();
            switch (m) {
                case "GET":
                    return new ApiGetEvent(hostPlugin, path, apiName, permission, ownerPlugin, authenticated, playerName, asyncPlayer, credential, requestParams, body);
                case "POST":
                    return new ApiPostEvent(hostPlugin, path, apiName, permission, ownerPlugin, authenticated, playerName, asyncPlayer, credential, requestParams, body);
                case "PUT":
                    return new ApiPutEvent(hostPlugin, path, apiName, permission, ownerPlugin, authenticated, playerName, asyncPlayer, credential, requestParams, body);
                case "DELETE":
                    return new ApiDeleteEvent(hostPlugin, path, apiName, permission, ownerPlugin, authenticated, playerName, asyncPlayer, credential, requestParams, body);
                case "PATCH":
                    return new ApiPatchEvent(hostPlugin, path, apiName, permission, ownerPlugin, authenticated, playerName, asyncPlayer, credential, requestParams, body);
                default:
                    return new ApiOtherEvent(hostPlugin, m, path, apiName, permission, ownerPlugin, authenticated, playerName, asyncPlayer, credential, requestParams, body);
            }
        }

        /**
         * 是否为公开端点（无权限要求）。
         */
        public boolean isPublic() {
            return permission.isEmpty();
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

        public static HandlerList getHandlerList() {
            return HANDLERS;
        }

        @Override
        public HandlerList getHandlers() {
            return HANDLERS;
        }

        /**
         * API 访问监听事件：DELETE 请求类型（监听本类只收到 DELETE 访问；监听基类收全部）。
         */
        public static class ApiDeleteEvent extends ApiAccessEvent {

            public ApiDeleteEvent(Plugin hostPlugin, String path, String apiName, String permission, String ownerPlugin,
                                  boolean authenticated, String playerName, Player asyncPlayer, CredentialPresentation credential, Map<String, String> requestParams, byte[] body) {
                super(hostPlugin, "DELETE", path, apiName, permission, ownerPlugin, authenticated, playerName, asyncPlayer, credential, requestParams, body);
            }

            public static HandlerList getHandlerList() {
                return ApiAccessEvent.getHandlerList();
            }

            @Override
            public HandlerList getHandlers() {
                return ApiAccessEvent.getHandlerList();
            }
        }

        /**
         * API 访问监听事件：PUT 请求类型（监听本类只收到 PUT 访问；监听基类收全部）。
         */
        public static class ApiPutEvent extends ApiAccessEvent {

            public ApiPutEvent(Plugin hostPlugin, String path, String apiName, String permission, String ownerPlugin,
                               boolean authenticated, String playerName, Player asyncPlayer, CredentialPresentation credential, Map<String, String> requestParams, byte[] body) {
                super(hostPlugin, "PUT", path, apiName, permission, ownerPlugin, authenticated, playerName, asyncPlayer, credential, requestParams, body);
            }

            public static HandlerList getHandlerList() {
                return ApiAccessEvent.getHandlerList();
            }

            @Override
            public HandlerList getHandlers() {
                return ApiAccessEvent.getHandlerList();
            }
        }

        /**
         * API 访问监听事件：POST 请求类型（监听本类只收到 POST 访问；监听基类收全部）。
         */
        public static class ApiPostEvent extends ApiAccessEvent {

            public ApiPostEvent(Plugin hostPlugin, String path, String apiName, String permission, String ownerPlugin,
                                boolean authenticated, String playerName, Player asyncPlayer, CredentialPresentation credential, Map<String, String> requestParams, byte[] body) {
                super(hostPlugin, "POST", path, apiName, permission, ownerPlugin, authenticated, playerName, asyncPlayer, credential, requestParams, body);
            }

            public static HandlerList getHandlerList() {
                return ApiAccessEvent.getHandlerList();
            }

            @Override
            public HandlerList getHandlers() {
                return ApiAccessEvent.getHandlerList();
            }
        }

        /**
         * API 访问监听事件：PATCH 请求类型（监听本类只收到 PATCH 访问；监听基类收全部）。
         */
        public static class ApiPatchEvent extends ApiAccessEvent {

            public ApiPatchEvent(Plugin hostPlugin, String path, String apiName, String permission, String ownerPlugin,
                                 boolean authenticated, String playerName, Player asyncPlayer, CredentialPresentation credential, Map<String, String> requestParams, byte[] body) {
                super(hostPlugin, "PATCH", path, apiName, permission, ownerPlugin, authenticated, playerName, asyncPlayer, credential, requestParams, body);
            }

            public static HandlerList getHandlerList() {
                return ApiAccessEvent.getHandlerList();
            }

            @Override
            public HandlerList getHandlers() {
                return ApiAccessEvent.getHandlerList();
            }
        }

        /**
         * API 访问监听事件：OTHER(未识别) 请求类型（监听本类只收到 OTHER(未识别) 访问；监听基类收全部）。
         */
        public static class ApiOtherEvent extends ApiAccessEvent {

            public ApiOtherEvent(Plugin hostPlugin, String httpMethod, String path, String apiName, String permission, String ownerPlugin,
                                 boolean authenticated, String playerName, Player asyncPlayer, CredentialPresentation credential, Map<String, String> requestParams, byte[] body) {
                super(hostPlugin, httpMethod, path, apiName, permission, ownerPlugin, authenticated, playerName, asyncPlayer, credential, requestParams, body);
            }

            public static HandlerList getHandlerList() {
                return ApiAccessEvent.getHandlerList();
            }

            @Override
            public HandlerList getHandlers() {
                return ApiAccessEvent.getHandlerList();
            }
        }

        /**
         * API 访问监听事件：GET 请求类型（监听本类只收到 GET 访问；监听基类收全部）。
         */
        public static class ApiGetEvent extends ApiAccessEvent {

            public ApiGetEvent(Plugin hostPlugin, String path, String apiName, String permission, String ownerPlugin,
                               boolean authenticated, String playerName, Player asyncPlayer, CredentialPresentation credential, Map<String, String> requestParams, byte[] body) {
                super(hostPlugin, "GET", path, apiName, permission, ownerPlugin, authenticated, playerName, asyncPlayer, credential, requestParams, body);
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






    /**
     * API 请求<b>处理完成</b>事件：一条注解式 API 请求命中路由并处理完成后触发
     * （<b>包含访问被拒 / 异常 / 成功</b>等全部结果），在处理器执行完毕、响应返回前发射
     * （worker 线程 → 由网关切回主线程发射，监听器必在主线程收到）。
     *
     * <p><b>与 {@link ApiAccessEvent} 的区别</b>：{@link ApiAccessEvent} 在权限判定通过后、处理器调用<b>前</b>触发
     * （可提前介入）；本事件在处理器执行<b>完成后</b>触发，携带<b>请求参数</b>与<b>返回体</b>，
     * 适合日志记录、埋点统计、请求审计、失败补偿等"事后"场景。</p>
     *
     * <p><b>字段携带</b>：请求方法 / 原始路径(含 query) / 权限 / 所属插件 / 玩家名 / 凭证 / 请求头 /
     * 请求体 / <b>请求参数（query 解析结果）</b> / <b>返回体（处理结果对象，通常为 AjaxResult）</b> /
     * 状态码 / 原因 / offline 标记。</p>
     *
     * <p><b>线程模型</b>：与 {@link ApiAccessEvent} 一致，由网关切回主线程发射，监听器必在主线程收到事件。</p>
     */
    @Getter
        public static class ApiAccessCompletedEvent extends ApiEvent {

        /**
         * 共享 HandlerList（与 ApiAccessEvent 同构，便于统一事件治理）。
         */
        private static final HandlerList HANDLERS = new HandlerList();

        /**
         * 实际请求方法（GET/POST/...）。
         */
        private final String httpMethod;
        /**
         * 原始路径（含 query 串，保证 @RequestParam 重放正确）。
         */
        private final String rawPath;
        /**
         * 端点名称（@ApiName）。
         */
        private final String apiName;
        /**
         * 端点要求的权限（@ApiPermission）。
         */
        private final String permission;
        /**
         * 注册该 API 的插件名。
         */
        private final String ownerPlugin;
        /**
         * 请求是否携带有效凭证。
         */
        private final boolean authenticated;
        /**
         * 经 token/cookie 解析出的玩家名（无凭证 / 非玩家令牌 = null）。
         */
        private final String playerName;
        /**
         * 玩家离线标记（true=离线）。
         */
        private final boolean offline;
        /**
         * 请求解析出的凭证（可为 null）。
         */
        private final CredentialPresentation credential;
    /**
     * 请求参数（query 解析结果：@RequestParam 来源）。
     * <p>仅解析 <b>URL query string</b>（? 之后按 &amp; 分割、URL 解码）：
     * 如 /api/status?page=1&amp;size=10 → {page=1, size=10}。</p>
     * <p>三种请求参数形态的行为：</p>
     * <ul>
     *   <li><b>query string</b>（/api/x?a=1&amp;b=2）→ 解析进本字段（URL 解码）；</li>
     *   <li><b>JSON body</b>（{"a":1}）→ <b>不进入</b>本字段：JSON 在请求体中，由 @RequestBody 实体参数
     *       经 JsonReader 绑定，与 URL query 无关；</li>
     *   <li><b>www-form-urlencoded body</b>（a=1&amp;b=2）→ <b>不进入</b>本字段：body 由 @RequestBody
     *       String 参数原样接收；仅当 URL 同时带 query 时本字段才有值。</li>
     * </ul>
     * <p>需要完整请求体（原始字节）请用 {@link #getBody()}。</p>
     */
        private final Map<String, String> requestParams;
        /**
         * 返回体（处理器处理结果对象：成功/业务错误通常为 {@link com.github.cocosoys.mc.soyshttpovermc.util.AjaxResult}，
         * 特殊响应为 {@link com.github.cocosoys.mc.soyshttpovermc.util.ApiResponse}；监听器可读取 code/msg/data）。
         */
        private final Object responseBody;
        /**
         * 处理结果状态码（成功 200；被拒 403 / 429 / 409 / 400 / 500 等）。
         */
        private final int statusCode;
        /**
         * 处理结果原因（人类可读；成功为 "OK"）。
         */
        private final String reason;
        /**
         * 完整请求体（重放用）。
         */
        private final byte[] body;
        /**
         * 完整请求头（重放用）。
         */
        private final Map<String, String> headers;

        /**
         * @param requestParams 请求参数（query 解析结果）
         * @param responseBody  返回体（处理结果对象，通常为 AjaxResult）
         */
        public ApiAccessCompletedEvent(String httpMethod, String rawPath, String apiName,
                                       String permission, String ownerPlugin, boolean authenticated,
                                       String playerName, boolean offline, CredentialPresentation credential,
                                       int statusCode, String reason, Map<String, String> requestParams,
                                       Object responseBody, byte[] body, Map<String, String> headers) {
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
            this.requestParams = requestParams;
            this.responseBody = responseBody;
            this.statusCode = statusCode;
            this.reason = reason == null ? "" : reason;
            this.body = body;
            this.headers = headers;
        }

        public static HandlerList getHandlerList() {
            return HANDLERS;
        }

        @Override
        public HandlerList getHandlers() {
            return HANDLERS;
    }

        /**
     * 按实际请求方法构造对应子类事件（未识别方法 → ApiOtherCompletedEvent）；
     * 监听基类 ApiAccessCompletedEvent 收全部，监听 ApiGetCompletedEvent 等只收对应方法。
     */
        public static ApiAccessCompletedEvent forMethod(String httpMethod, String rawPath, String apiName,
                                                        String permission, String ownerPlugin, boolean authenticated,
                                                        String playerName, boolean offline, CredentialPresentation credential,
                                                        int statusCode, String reason, Map<String, String> requestParams,
                                                        Object responseBody, byte[] body, Map<String, String> headers) {
            String m = httpMethod == null ? "" : httpMethod.toUpperCase();
            switch (m) {
                case "GET":
                    return new ApiGetCompletedEvent(rawPath, apiName, permission, ownerPlugin, authenticated, playerName, offline, credential, statusCode, reason, requestParams, responseBody, body, headers);
                case "POST":
                    return new ApiPostCompletedEvent(rawPath, apiName, permission, ownerPlugin, authenticated, playerName, offline, credential, statusCode, reason, requestParams, responseBody, body, headers);
                case "PUT":
                    return new ApiPutCompletedEvent(rawPath, apiName, permission, ownerPlugin, authenticated, playerName, offline, credential, statusCode, reason, requestParams, responseBody, body, headers);
                case "DELETE":
                    return new ApiDeleteCompletedEvent(rawPath, apiName, permission, ownerPlugin, authenticated, playerName, offline, credential, statusCode, reason, requestParams, responseBody, body, headers);
                case "PATCH":
                    return new ApiPatchCompletedEvent(rawPath, apiName, permission, ownerPlugin, authenticated, playerName, offline, credential, statusCode, reason, requestParams, responseBody, body, headers);
                default:
                    return new ApiOtherCompletedEvent(m, rawPath, apiName, permission, ownerPlugin, authenticated, playerName, offline, credential, statusCode, reason, requestParams, responseBody, body, headers);
            }
        }

        /**
         * API 处理完成事件：GET 请求类型（监听本类只收到 GET 完成；监听基类收全部）。
         */
        public static class ApiGetCompletedEvent extends ApiAccessCompletedEvent {

            public ApiGetCompletedEvent(String rawPath, String apiName, String permission, String ownerPlugin,
                                        boolean authenticated, String playerName, boolean offline, CredentialPresentation credential,
                                        int statusCode, String reason, Map<String, String> requestParams,
                                        Object responseBody, byte[] body, Map<String, String> headers) {
                super("GET", rawPath, apiName, permission, ownerPlugin, authenticated, playerName, offline, credential,
                        statusCode, reason, requestParams, responseBody, body, headers);
            }

            public static HandlerList getHandlerList() {
                return ApiAccessCompletedEvent.getHandlerList();
            }

            @Override
            public HandlerList getHandlers() {
                return ApiAccessCompletedEvent.getHandlerList();
            }
        }

        /**
         * API 处理完成事件：POST 请求类型（监听本类只收到 POST 完成；监听基类收全部）。
         */
        public static class ApiPostCompletedEvent extends ApiAccessCompletedEvent {

            public ApiPostCompletedEvent(String rawPath, String apiName, String permission, String ownerPlugin,
                                         boolean authenticated, String playerName, boolean offline, CredentialPresentation credential,
                                         int statusCode, String reason, Map<String, String> requestParams,
                                         Object responseBody, byte[] body, Map<String, String> headers) {
                super("POST", rawPath, apiName, permission, ownerPlugin, authenticated, playerName, offline, credential,
                        statusCode, reason, requestParams, responseBody, body, headers);
            }

            public static HandlerList getHandlerList() {
                return ApiAccessCompletedEvent.getHandlerList();
            }

            @Override
            public HandlerList getHandlers() {
                return ApiAccessCompletedEvent.getHandlerList();
            }
        }

        /**
         * API 处理完成事件：PUT 请求类型（监听本类只收到 PUT 完成；监听基类收全部）。
         */
        public static class ApiPutCompletedEvent extends ApiAccessCompletedEvent {

            public ApiPutCompletedEvent(String rawPath, String apiName, String permission, String ownerPlugin,
                                        boolean authenticated, String playerName, boolean offline, CredentialPresentation credential,
                                        int statusCode, String reason, Map<String, String> requestParams,
                                        Object responseBody, byte[] body, Map<String, String> headers) {
                super("PUT", rawPath, apiName, permission, ownerPlugin, authenticated, playerName, offline, credential,
                        statusCode, reason, requestParams, responseBody, body, headers);
            }

            public static HandlerList getHandlerList() {
                return ApiAccessCompletedEvent.getHandlerList();
            }

            @Override
            public HandlerList getHandlers() {
                return ApiAccessCompletedEvent.getHandlerList();
            }
        }

        /**
         * API 处理完成事件：DELETE 请求类型（监听本类只收到 DELETE 完成；监听基类收全部）。
         */
        public static class ApiDeleteCompletedEvent extends ApiAccessCompletedEvent {

            public ApiDeleteCompletedEvent(String rawPath, String apiName, String permission, String ownerPlugin,
                                           boolean authenticated, String playerName, boolean offline, CredentialPresentation credential,
                                           int statusCode, String reason, Map<String, String> requestParams,
                                           Object responseBody, byte[] body, Map<String, String> headers) {
                super("DELETE", rawPath, apiName, permission, ownerPlugin, authenticated, playerName, offline, credential,
                        statusCode, reason, requestParams, responseBody, body, headers);
            }

            public static HandlerList getHandlerList() {
                return ApiAccessCompletedEvent.getHandlerList();
            }

            @Override
            public HandlerList getHandlers() {
                return ApiAccessCompletedEvent.getHandlerList();
            }
        }

        /**
         * API 处理完成事件：PATCH 请求类型（监听本类只收到 PATCH 完成；监听基类收全部）。
         */
        public static class ApiPatchCompletedEvent extends ApiAccessCompletedEvent {

            public ApiPatchCompletedEvent(String rawPath, String apiName, String permission, String ownerPlugin,
                                          boolean authenticated, String playerName, boolean offline, CredentialPresentation credential,
                                          int statusCode, String reason, Map<String, String> requestParams,
                                          Object responseBody, byte[] body, Map<String, String> headers) {
                super("PATCH", rawPath, apiName, permission, ownerPlugin, authenticated, playerName, offline, credential,
                        statusCode, reason, requestParams, responseBody, body, headers);
            }

            public static HandlerList getHandlerList() {
                return ApiAccessCompletedEvent.getHandlerList();
            }

            @Override
            public HandlerList getHandlers() {
                return ApiAccessCompletedEvent.getHandlerList();
            }
        }

        /**
         * API 处理完成事件：OTHER(未识别) 请求类型（监听本类只收到 OTHER(未识别) 完成；监听基类收全部）。
         */
        public static class ApiOtherCompletedEvent extends ApiAccessCompletedEvent {

            public ApiOtherCompletedEvent(String httpMethod, String rawPath, String apiName, String permission, String ownerPlugin,
                                          boolean authenticated, String playerName, boolean offline, CredentialPresentation credential,
                                          int statusCode, String reason, Map<String, String> requestParams,
                                          Object responseBody, byte[] body, Map<String, String> headers) {
                super(httpMethod, rawPath, apiName, permission, ownerPlugin, authenticated, playerName, offline, credential,
                        statusCode, reason, requestParams, responseBody, body, headers);
            }

            public static HandlerList getHandlerList() {
                return ApiAccessCompletedEvent.getHandlerList();
            }

            @Override
            public HandlerList getHandlers() {
                return ApiAccessCompletedEvent.getHandlerList();
            }
        }
        }

}
