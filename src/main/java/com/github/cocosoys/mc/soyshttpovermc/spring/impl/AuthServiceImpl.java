package com.github.cocosoys.mc.soyshttpovermc.spring.impl;
import com.github.cocosoys.mc.soyshttpovermc.enums.LoginMode;

import com.github.cocosoys.mc.soyshttpovermc.web.ApiRequestContext;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.bridge.AuthLoginBridge;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.issuer.CredentialPresentation;

import com.github.cocosoys.mc.soyshttpovermc.spring.service.IAuthService;
import com.github.cocosoys.mc.soyshttpovermc.util.AjaxResult;
import com.github.cocosoys.mc.soyshttpovermc.util.ApiResponse;

import org.bukkit.Bukkit;

import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 登录窗口认证 Service 实现（仿 MyBatis-Plus 的 {@code XxxServiceImpl implements XxxService}）：
 * <b>业务逻辑集中于此</b>，控制器只调用接口方法。复用 {@link AuthLoginBridge} 的
 * AuthMe 密码校验与 session-token 签发/撤销链路，与 AuthMe 网页登录（ticket 流程）互补：
 * 登录窗口（web/login.html）用"玩家名 + AuthMe 密码"直接登录，无需游戏内链接。
 */
public class AuthServiceImpl implements IAuthService {

    /** 登录请求体中的字段提取（JSON 与表单双兼容） */
    private static final Pattern JSON_FIELD = Pattern.compile("\"([a-zA-Z0-9_]+)\"\\s*:\\s*\"([^\"]*)\"");

    private volatile AuthLoginBridge bridge;

    public AuthServiceImpl(AuthLoginBridge bridge) {
        this.bridge = bridge;
    }

    /** 热替换登录桥（/soyshttp reload 重建网关后调用，保持与最新 session-token 颁发器一致）。 */
    public void setBridge(AuthLoginBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public AjaxResult login(String body, ApiRequestContext ctx) {
        if (bridge == null) {
            return AjaxResult.errorT(503, "ajax.auth.issuer-not-enabled", "会话令牌颁发器未启用（请在 gateway/issuers/session-token.yml 设 enabled: true）");
        }
        Map<String, String> form = parseBody(body);
        String username = form.get("username");
        String password = form.get("password");
        // 无登录插件 → 免密码登录（仅凭用户名直登）；有登录插件 → 用户名 + 密码校验
        String token;
        if (!bridge.loginRequiresPassword()) {
            if (username == null || username.isEmpty()) {
                return AjaxResult.errorT(400, "ajax.auth.missing-username", "缺少必填参数: username");
            }
            token = bridge.loginByUsername(username.trim());
            if (token == null) {
                return AjaxResult.errorT(400, "ajax.auth.username-invalid", "用户名不合法（仅字母/数字/下划线，≤16 字符）或离线登录被策略禁止");
            }
        } else {
            if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
                return AjaxResult.errorT(400, "ajax.auth.missing-username-password", "缺少必填参数: username / password");
            }
            token = bridge.login(username.trim(), password);
            if (token == null) {
                return AjaxResult.unauthorizedT("ajax.auth.bad-credentials", "账号或密码错误（AuthMe 校验失败，或服务器未安装 AuthMe，或禁止离线登录）");
            }
        }
        // 记录网页端登录 IP（用于后续游戏端 IP 匹配免登录）
        String clientIp = ctx == null ? null : ctx.getIp();
        if (clientIp != null && !clientIp.equals("0.0.0.0")) {
            bridge.recordWebLogin(username.trim(), clientIp);
        }
        // 登录模式（与 bridge.login 内部同一策略）：玩家在线→online；不在线→offline（离线专属 cookie）
        LoginMode mode =
                bridge.getLoginModePolicy().decideLogin(username.trim());
        Map<String, Object> data = new HashMap<>();
        data.put("player", username.trim());
        data.put("token", token);
        data.put("cookieName", bridge.getCookieName());
        data.put("ttlSeconds", bridge.getTtlSeconds());
        data.put("mode", mode.name().toLowerCase()); // online / offline（离线模式登录标签）
        data.put("authenticated", true);
        return AjaxResult.successDataT(data, "ajax.auth.login-success", "登录成功");
    }

    @Override
    public AjaxResult logout(CredentialPresentation credential) {
        if (bridge == null) {
            return AjaxResult.errorT(503, "ajax.auth.issuer-not-enabled-short", "会话令牌颁发器未启用");
        }
        // 退出登录时清除网页端登录 IP 记录
        String player = bridge.subjectOf(credential);
        if (player != null) {
            bridge.clearWebLogin(player);
        }
        if (!bridge.logout(credential)) {
            return AjaxResult.unauthorizedT("ajax.auth.not-logged-in", "未登录或凭证无效");
        }
        return AjaxResult.successT("ajax.auth.logout-success", "已退出登录");
    }

    @Override
    public AjaxResult me(CredentialPresentation credential) {
        if (bridge == null) {
            return AjaxResult.unauthorizedT("ajax.auth.not-logged-in-no-issuer", "未登录或会话令牌颁发器未启用");
        }
        String player = bridge.subjectOf(credential);
        if (player == null) {
            return AjaxResult.unauthorizedT("ajax.auth.not-logged-in", "未登录或凭证无效");
        }
        LoginMode mode = bridge.modeOf(credential);
        Map<String, Object> data = new HashMap<>();
        data.put("player", player);
        data.put("authenticated", true);
        data.put("online", Bukkit.getPlayerExact(player) != null);
        // 登录模式：offline=玩家使用离线模式登录网页（打标签）；online=在线正常登录（含升级后）
        data.put("mode", mode == null ? null : mode.name().toLowerCase());
        return AjaxResult.success(data);
    }

    @Override
    public ApiResponse serveLogin(String ticket) {
        if (bridge == null) {
            return ApiResponse.jsonErrorT(503, "ajax.auth.issuer-not-enabled", "会话令牌颁发器未启用（请在 gateway/issuers/session-token.yml 设 enabled: true）");
        }
        return bridge.serveLoginPage(ticket);
    }

    @Override
    public ApiResponse issue(String body) {
        if (bridge == null) {
            return ApiResponse.jsonErrorT(503, "ajax.auth.issuer-not-enabled", "会话令牌颁发器未启用（请在 gateway/issuers/session-token.yml 设 enabled: true）");
        }
        Map<String, String> form = parseBody(body);
        // 有登录插件=票据+密码校验；无登录插件=免密码，仅凭用户名直登（与弹窗登录 login 同策略）
        if (bridge.loginRequiresPassword()) {
            return bridge.issue(form.get("ticket"), form.get("password"));
        }
        return bridge.issueByUsername(form.get("username"));
    }

    @Override
    public AjaxResult loginMode() {
        if (bridge == null) {
            return AjaxResult.errorT(503, "ajax.auth.issuer-not-enabled", "会话令牌颁发器未启用（请在 gateway/issuers/session-token.yml 设 enabled: true）");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("requiresPassword", bridge.loginRequiresPassword());
        data.put("cookieName", bridge.getCookieName());
        data.put("ttlSeconds", bridge.getTtlSeconds());
        return AjaxResult.success(data);
    }

    @Override
    public ApiResponse checkStatus(ApiRequestContext ctx, String player) {
        if (bridge == null) {
            return ApiResponse.jsonErrorT(503, "ajax.auth.issuer-not-enabled", "会话令牌颁发器未启用（请在 gateway/issuers/session-token.yml 设 enabled: true）");
        }
        String clientIp = ctx == null ? null : ctx.getIp();
        CredentialPresentation credential = ctx == null ? null : ctx.getCredential();

        // 1) 当前请求已登录 → 返回已登录状态
        if (credential != null && credential.hasAnyCredential()) {
            String loggedPlayer = bridge.subjectOf(credential);
            if (loggedPlayer != null) {
                LoginMode mode = bridge.modeOf(credential);
                Map<String, Object> data = new HashMap<>();
                data.put("player", loggedPlayer);
                data.put("authenticated", true);
                data.put("online", Bukkit.getPlayerExact(loggedPlayer) != null);
                data.put("mode", mode == null ? null : mode.name().toLowerCase());
                data.put("ip", clientIp);
                return ApiResponse.status(200, AjaxResult.success(data), null);
            }
        }

        // 2) 未登录：使用传入的 player 参数，检查游戏端登录状态 + IP 匹配
        if (player == null || player.isEmpty()) {
            // 无 player 参数 → 返回未登录状态（供前端判断是否需要显示登录表单）
            Map<String, Object> data = new HashMap<>();
            data.put("authenticated", false);
            data.put("ip", clientIp);
            return ApiResponse.status(200, AjaxResult.success(data), null);
        }

        // 检查该玩家是否在游戏端已登录且 IP 匹配
        boolean gameLoggedIn = bridge.isGameLoggedIn(player);
        boolean ipMatched = gameLoggedIn && clientIp != null && !clientIp.equals("0.0.0.0")
                && bridge.gameIpMatches(player, clientIp);

        if (!gameLoggedIn || !ipMatched) {
            Map<String, Object> data = new HashMap<>();
            data.put("authenticated", false);
            data.put("player", player);
            data.put("gameLoggedIn", gameLoggedIn);
            data.put("ipMatched", ipMatched);
            data.put("ip", clientIp);
            return ApiResponse.status(200, AjaxResult.success(data), null);
        }

        // 3) 游戏端已登录 + IP 匹配 → 自动签发在线令牌（游戏→网页自动登录）
        String token = bridge.issueToken(player, LoginMode.ONLINE);
        // 记录网页端登录 IP（后续游戏端重连时可用于免登录）
        if (clientIp != null && !clientIp.equals("0.0.0.0")) {
            bridge.recordWebLogin(player, clientIp);
        }
        String cookie = bridge.getCookieName() + "=" + token
                + "; Path=/; Max-Age=" + bridge.getTtlSeconds()
                + "; HttpOnly; SameSite=Lax";
        Map<String, String> extra = new HashMap<>();
        extra.put("Set-Cookie", cookie);
        Map<String, Object> data = new HashMap<>();
        data.put("player", player);
        data.put("authenticated", true);
        data.put("online", true);
        data.put("mode", "online");
        data.put("autoLogin", true);
        data.put("token", token);
        data.put("cookieName", bridge.getCookieName());
        data.put("ttlSeconds", bridge.getTtlSeconds());
        data.put("ip", clientIp);
        return ApiResponse.status(200, AjaxResult.successDataT(data, "ajax.auth.auto-login-success", "IP 匹配，已自动登录"), extra);
    }

    /** 解析登录请求体：优先 JSON（{"username":..,"password":..}），其次表单（username=..&password=..）。 */
    private static Map<String, String> parseBody(String body) {
        Map<String, String> map = new HashMap<>();
        if (body == null || body.isEmpty()) return map;
        String s = body.trim();
        if (s.startsWith("{")) {
            Matcher m = JSON_FIELD.matcher(s);
            while (m.find()) {
                map.put(m.group(1), m.group(2));
            }
        } else {
            for (String pair : s.split("&")) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    String k = pair.substring(0, eq);
                    String v = pair.substring(eq + 1);
                    try {
                        map.put(URLDecoder.decode(k, "UTF-8"), URLDecoder.decode(v, "UTF-8"));
                    } catch (Exception e) {
                        map.put(k, v);
                    }
                }
            }
        }
        return map;
    }
}
