package com.github.cocosoys.mc.soyshttpovermc.api.event;

import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.issuer.CredentialIssuer;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.issuer.IssuedCredential;
import lombok.Getter;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Collections;
import java.util.Map;

/**
 * 网关事件抽象基类。
 * <ul>
 *   <li>请求类事件（request/denied/served）为<b>异步事件</b>：在 HTTP 处理线程（嗅探器线程池）触发；</li>
 *   <li>凭证下发事件（credential issued）为<b>同步事件</b>：在主线程（命令/登录流程）触发；</li>
 *   <li>API 注册 / 卸载事件（api registered / unregistered）为<b>同步事件</b>：
 *       在插件 onEnable / onDisable（主线程）触发，1.12.2 主线程触发异步事件会抛 IllegalStateException，
 *       故强制同步。可在该事件中获取某插件注册的端点清单（方法 / 路径 / 端点名 / 权限 / 处理器类）。</li>
 * </ul>
 * 其他插件用 Bukkit 标准方式监听：
 * <pre>
 *   getServer().getPluginManager().registerEvents(new Listener() {
 *       &#64;EventHandler
 *       public void onDenied(GatewayAccessDeniedEvent e) {
 *           // ...
 *       }
 *   }, yourPlugin);
 * </pre>
 */
public abstract class GatewayEvent extends Event {

    /**
     * 默认异步（HTTP 处理线程触发；兼容旧字节码/无参构造）
     */
    protected GatewayEvent() {
        super(true);
    }

    protected GatewayEvent(boolean async) {
        super(async);
    }

    /**
     * 网关访问拒绝事件：某条请求被安全策略链拒绝（401/403/426/429/500 等）时触发。
     * 可用来做安全告警、封禁联动、审计日志等。
     */
    @Getter
    public static class GatewayAccessDeniedEvent extends GatewayEvent {

        private static final HandlerList HANDLERS = new HandlerList();

        private final String method;
        private final String path;
        private final String ip;
        private final boolean tls;
        /**
         * 拒绝该请求的策略名（如 tls / auth / rate-limit / ip-allowlist）
         */
        private final String policyName;
        private final int statusCode;
        private final String reason;

        public GatewayAccessDeniedEvent(String method, String path, String ip, boolean tls,
                                        String policyName, int statusCode, String reason) {
            this.method = method == null ? "" : method;
            this.path = path == null ? "/" : path;
            this.ip = ip == null ? "0.0.0.0" : ip;
            this.tls = tls;
            this.policyName = policyName == null ? "" : policyName;
            this.statusCode = statusCode;
            this.reason = reason == null ? "" : reason;
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
     * 凭证下发事件：/soyshttp key 命令或登录插件调用颁发器下发凭证后触发。
     * 其他插件可监听此事件做记录、通知客户端、与自家登录系统联动等。
     *
     * <p>注意：本事件为<b>同步事件</b>（在主线程触发，如命令/登录流程），
     * 不要在异步线程触发；若确需异步触发请自行扩展异步变体。
     */
    @Getter
    public static class GatewayCredentialIssuedEvent extends GatewayEvent {

        private static final HandlerList HANDLERS = new HandlerList();

        /**
         * 凭证所属主体（玩家 UUID/用户名）
         */
        private final String subject;
        private final CredentialIssuer issuer;
        /**
         * 下发的凭证（X-API-Key / Bearer / Cookie 三种形态）
         */
        private final IssuedCredential credential;

        public GatewayCredentialIssuedEvent(String subject, CredentialIssuer issuer, IssuedCredential credential) {
            super(false); // 同步：命令/登录流程在主线程触发
            this.subject = subject == null ? "" : subject;
            this.issuer = issuer;
            this.credential = credential;
        }

        public String getIssuerName() {
            return issuer == null ? "" : issuer.name();
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
     * 网关请求进入事件：一条 HTTP(S) 请求进入网关（安全策略判定之前）触发。
     * 可用来做访问审计、统计、自定义拦截提示等。
     */
    @Getter
    public static class GatewayRequestEvent extends GatewayEvent {

        private static final HandlerList HANDLERS = new HandlerList();

        private final String method;
        private final String path;
        private final String ip;
        private final boolean tls;
        private final Map<String, String> headers;

        public GatewayRequestEvent(String method, String path, String ip, boolean tls, Map<String, String> headers) {
            this.method = method == null ? "" : method;
            this.path = path == null ? "/" : path;
            this.ip = ip == null ? "0.0.0.0" : ip;
            this.tls = tls;
            this.headers = headers == null ? Collections.<String, String>emptyMap() : headers;
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
     * 网关请求处理完成事件：一条请求已处理完毕（含被拒绝与异常）时触发。
     * 携带最终状态码与端到端耗时，可用来做指标统计、监控告警。
     */
    @Getter
    public static class GatewayRequestServedEvent extends GatewayEvent {

        private static final HandlerList HANDLERS = new HandlerList();

        private final String method;
        private final String path;
        private final String ip;
        private final boolean tls;
        private final int statusCode;
        /**
         * 端到端耗时（微秒，含隧道往返）
         */
        private final long latencyUs;

        public GatewayRequestServedEvent(String method, String path, String ip, boolean tls,
                                         int statusCode, long latencyUs) {
            this.method = method == null ? "" : method;
            this.path = path == null ? "/" : path;
            this.ip = ip == null ? "0.0.0.0" : ip;
            this.tls = tls;
            this.statusCode = statusCode;
            this.latencyUs = latencyUs;
        }

        public long getLatencyMs() {
            return latencyUs / 1000;
        }

        public static HandlerList getHandlerList() {
            return HANDLERS;
        }

        @Override
        public HandlerList getHandlers() {
            return HANDLERS;
        }
    }
}
