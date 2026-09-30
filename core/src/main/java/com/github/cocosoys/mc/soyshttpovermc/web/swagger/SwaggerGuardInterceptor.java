package com.github.cocosoys.mc.soyshttpovermc.web.swagger;

import com.github.cocosoys.mc.soyshttpovermc.util.AjaxResult;
import com.github.cocosoys.mc.soyshttpovermc.web.WebInterceptor;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.util.AuthUtils;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.issuer.CredentialPresentation;
import org.bukkit.Bukkit;

import java.nio.charset.StandardCharsets;
import java.util.function.Function;

/**
 * Swagger 自文档访问守卫（请求级拦截器）。
 *
 * <p>仅作用于 {@code /swagger} 命名空间（{@code /swagger} 与 {@code /swagger/**}），其余路径一律放行：
 * <ul>
 *   <li>开关关闭（gateway/config.yml {@code swagger.enabled=false}）→ 404；</li>
 *   <li>未登录（解析不出玩家名）→ 401；</li>
 *   <li>非服务器 OP → 403；</li>
 *   <li>已登录且为 OP → 放行，后续由 WebFrontendHandler 正常路由（UI 静态页 / api-docs JSON）。</li>
 * </ul>
 *
 * <p>位于网关策略链之后、业务路由之前，X-API-Key 等机器凭证因无法解析玩家名同样被拒
 * （Swagger 仅面向人工操作员）。{@code enabled} 为 volatile，{@code /soyshttp reload}
 * 重建网关时经 {@link #setEnabled(boolean)} 同步。
 *
 * <p><b>调试端点</b>（{@code /swagger/debug/**}，同样受登录 + OP 门保护，仅供 Swagger UI
 * 调试栏使用，调试凭证只进页面 JS 作用域、不写浏览器 cookie / localStorage）：
 * <ul>
 *   <li>{@code GET  /swagger/debug/current-user} → {@code {player, op, apiKeyHeader}}：当前会话用户
 *       （{@code apiKeyHeader} 为 auth.yml 可配的 X-API-Key 头名，前端注入时使用）；</li>
 *   <li>{@code POST /swagger/debug/switch-player} → body {@code {player}} → 经
 *       {@code debugTokenIssuer} 签发 st_ Bearer（登录认证签发，模拟目标玩家调试）→ {@code {token, player}}；</li>
 *   <li>{@code POST /swagger/debug/verify-key} → body {@code {key}} → 经 {@code apiKeyVerifier}
 *       校验本地 API Key 表（有效 + 启用 + 未过期），无效 400 拒绝 → {@code {fingerprint, boundPlayer}}。</li>
 * </ul>
 */
public final class SwaggerGuardInterceptor implements WebInterceptor {

    /** 命名空间前缀（独立于 api-prefix，与页面登记一致）。 */
    private static final String PREFIX = "/swagger";

    private final Function<CredentialPresentation, String> playerResolver;
    /**
     * 调试令牌签发器（输入目标玩家名 → st_ Bearer；通常为 {@code AuthLoginBridge#issueToken}）。
     * null=调试栏"切换玩家"不可用（登录桥未装配）。
     */
    private final Function<String, String> debugTokenIssuer;
    /**
     * X-API-Key 校验器（输入请求明文 → {@link ApiKeyInfo}；无效/过期返回 null）。
     * null=调试栏"粘贴 X-API-Key"不可用。
     */
    private final Function<String, ApiKeyInfo> apiKeyVerifier;
    /**
     * X-API-Key 请求头名（auth.yml {@code header}，默认 X-API-Key；随网关配置，reload 重建守卫实例同步）。
     */
    private final String apiKeyHeader;
    private volatile boolean enabled;

    /**
     * 兼容旧签名：仅页面守卫，不启用调试端点（等价于调试签发器/校验器均为 null）。
     *
     * @param playerResolver 凭证 → 玩家名（建议传 {@code ApiRegistry#getPlayerResolver()}）
     * @param enabled        初始开关（gateway/config.yml swagger.enabled）
     */
    public SwaggerGuardInterceptor(Function<CredentialPresentation, String> playerResolver, boolean enabled) {
        this(playerResolver, enabled, null, null, null);
    }

    /**
     * 完整签名：页面守卫 + 调试端点（切换玩家 / 粘贴 X-API-Key）。
     *
     * @param playerResolver   凭证 → 玩家名（建议传 {@code ApiRegistry#getPlayerResolver()}）
     * @param enabled          初始开关（gateway/config.yml swagger.enabled）
     * @param debugTokenIssuer 调试令牌签发器（{@code AuthLoginBridge::issueToken}；null=禁用切换玩家）
     * @param apiKeyVerifier   X-API-Key 校验器（返回 {@link ApiKeyInfo}；null=禁用粘贴 key）
     * @param apiKeyHeader     X-API-Key 请求头名（auth.yml header；null/空回退默认 X-API-Key）
     */
    public SwaggerGuardInterceptor(Function<CredentialPresentation, String> playerResolver, boolean enabled,
                                   Function<String, String> debugTokenIssuer,
                                   Function<String, ApiKeyInfo> apiKeyVerifier,
                                   String apiKeyHeader) {
        this.playerResolver = playerResolver;
        this.enabled = enabled;
        this.debugTokenIssuer = debugTokenIssuer;
        this.apiKeyVerifier = apiKeyVerifier;
        this.apiKeyHeader = apiKeyHeader;
    }

    @Override
    public String name() {
        return "soys-swagger-guard";
    }

    @Override
    public Outcome intercept(WebInterceptContext ctx) throws Exception {
        String path = ctx.path();
        if (!path.equals(PREFIX) && !path.startsWith(PREFIX + "/")) {
            return Outcome.pass();
        }
        if (!enabled) {
            return Outcome.stopJson(404, "Swagger 自文档已关闭（gateway/config.yml swagger.enabled=false）");
        }
        CredentialPresentation credential = AuthUtils.extractPresentation(
                ctx.headers(), "X-API-Key", true, true, true, true);
        String player = playerResolver == null ? null : playerResolver.apply(credential);
        if (player == null || player.isEmpty()) {
            return Outcome.stopJson(401, "需要玩家登录后才能访问 Swagger 文档");
        }
        if (!Bukkit.getOfflinePlayer(player).isOp()) {
            return Outcome.stopJson(403, "需要服务器 OP 权限才能访问 Swagger 文档");
        }
        // 已登录 + OP：调试端点单独分流（其余路径放行由后续路由处理）
        if (path.startsWith(PREFIX + "/debug/")) {
            return handleDebug(ctx, player);
        }
        return Outcome.pass();
    }

    // ===== 调试端点（仅已登录 OP；凭证只进页面 JS 作用域，不写浏览器 cookie） =====

    private Outcome handleDebug(WebInterceptContext ctx, String operator) {
        String path = ctx.path();
        if (path.equals(PREFIX + "/debug/current-user")) {
            if (!"GET".equalsIgnoreCase(ctx.method())) {
                return Outcome.stopJson(405, "仅支持 GET");
            }
            AjaxResult ok = AjaxResult.success();
            ok.put("player", operator);
            ok.put("op", true);
            ok.put("apiKeyHeader", apiKeyHeader == null || apiKeyHeader.isEmpty() ? "X-API-Key" : apiKeyHeader);
            return Outcome.stopJson(200, ok);
        }
        if (path.equals(PREFIX + "/debug/switch-player")) {
            if (!"POST".equalsIgnoreCase(ctx.method())) {
                return Outcome.stopJson(405, "仅支持 POST");
            }
            String target = jsonField(bodyString(ctx), "player");
            if (target == null || target.trim().isEmpty()) {
                return Outcome.stopJson(400, "缺少 player 参数");
            }
            if (debugTokenIssuer == null) {
                return Outcome.stopJson(503, "调试令牌签发器不可用（登录桥未装配）");
            }
            String token = debugTokenIssuer.apply(target.trim());
            if (token == null || token.isEmpty()) {
                return Outcome.stopJson(500, "调试令牌签发失败");
            }
            AjaxResult ok = AjaxResult.success();
            ok.put("token", token);
            ok.put("player", target.trim());
            return Outcome.stopJson(200, ok);
        }
        if (path.equals(PREFIX + "/debug/verify-key")) {
            if (!"POST".equalsIgnoreCase(ctx.method())) {
                return Outcome.stopJson(405, "仅支持 POST");
            }
            String key = jsonField(bodyString(ctx), "key");
            if (key == null || key.trim().isEmpty()) {
                return Outcome.stopJson(400, "缺少 key 参数");
            }
            ApiKeyInfo info = apiKeyVerifier == null ? null : apiKeyVerifier.apply(key.trim());
            if (info == null) {
                return Outcome.stopJson(400, "X-API-Key 无效或已过期");
            }
            AjaxResult ok = AjaxResult.success();
            ok.put("fingerprint", info.getFingerprint());
            ok.put("boundPlayer", info.getBoundPlayer());
            return Outcome.stopJson(200, ok);
        }
        // /swagger/debug 下未识别的路径：放行（由后续路由正常处理/404）
        return Outcome.pass();
    }

    private static String bodyString(WebInterceptContext ctx) {
        byte[] b = ctx.body();
        if (b == null || b.length == 0) return null;
        return new String(b, StandardCharsets.UTF_8);
    }

    /**
     * 最小 JSON 字段提取（调试端点 body 由 Swagger 调试栏固定格式生成：{@code {"player":"..."}} /
     * {@code {"key":"..."}}；仅支持字符串值，不做通用解析）。
     */
    private static String jsonField(String body, String field) {
        if (body == null) return null;
        String key = "\"" + field + "\"";
        int i = body.indexOf(key);
        if (i < 0) return null;
        int j = body.indexOf(':', i + key.length());
        if (j < 0) return null;
        int k = j + 1;
        while (k < body.length() && Character.isWhitespace(body.charAt(k))) k++;
        if (k < body.length() && body.charAt(k) == '"') {
            int end = body.indexOf('"', k + 1);
            if (end < 0) return null;
            return body.substring(k + 1, end);
        }
        return null;
    }

    /**
     * X-API-Key 调试校验结果（verify-key 返回）。
     */
    public static final class ApiKeyInfo {
        private final String fingerprint;
        private final String boundPlayer;

        /**
         * @param fingerprint 8 位短指纹（无玩家绑定时用于展示）
         * @param boundPlayer 绑定的玩家名（可为 null=未绑定玩家）
         */
        public ApiKeyInfo(String fingerprint, String boundPlayer) {
            this.fingerprint = fingerprint;
            this.boundPlayer = boundPlayer;
        }

        public String getFingerprint() {
            return fingerprint;
        }

        public String getBoundPlayer() {
            return boundPlayer;
        }
    }

    /**
     * 同步开关（/soyshttp reload 时由网关配置重读驱动）。
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
