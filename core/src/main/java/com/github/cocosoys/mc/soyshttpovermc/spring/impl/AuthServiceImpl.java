package com.github.cocosoys.mc.soyshttpovermc.spring.impl;

import com.github.cocosoys.mc.soyshttpovermc.api.event.GatewayEvent;
import com.github.cocosoys.mc.soyshttpovermc.enums.LoginMode;
import com.github.cocosoys.mc.soyshttpovermc.spring.entity.SoysSsoTicket;
import com.github.cocosoys.mc.soyshttpovermc.spring.entity.vo.AuthStatusEntityVO;
import com.github.cocosoys.mc.soyshttpovermc.spring.entity.vo.LoginModeEntityVO;
import com.github.cocosoys.mc.soyshttpovermc.spring.entity.vo.LoginResultEntityVO;
import com.github.cocosoys.mc.soyshttpovermc.spring.service.IAuthService;
import com.github.cocosoys.mc.soyshttpovermc.util.AjaxResult;
import com.github.cocosoys.mc.soyshttpovermc.util.ApiResponse;
import com.github.cocosoys.mc.soyshttpovermc.web.ApiRequestContext;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.bridge.AuthLoginBridge;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.issuer.CredentialPresentation;
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
 * 返回数据一律实体化（{@code spring/entity/vo}），不出现临时 Map 组装。
 */
public class AuthServiceImpl implements IAuthService {

    /**
     * 登录请求体中的字段提取（JSON 与表单双兼容）
     */
    private static final Pattern JSON_FIELD = Pattern.compile("\"([a-zA-Z0-9_]+)\"\\s*:\\s*\"([^\"]*)\"");

    private volatile AuthLoginBridge bridge;

    public AuthServiceImpl(AuthLoginBridge bridge) {
        this.bridge = bridge;
    }

    /**
     * 热替换登录桥（/soyshttp reload 重建网关后调用，保持与最新 session-token 颁发器一致）。
     */
    public void setBridge(AuthLoginBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public ApiResponse login(String body, ApiRequestContext ctx) {
        if (bridge == null) {
            return ApiResponse.jsonErrorT(503, "ajax.auth.issuer-not-enabled", "会话令牌颁发器未启用（请在 gateway/issuers/session-token.yml 设 enabled: true）");
        }
        Map<String, String> form = parseBody(body);
        String username = form.get("username");
        String password = form.get("password");
        String clientIp = ctx == null ? null : ctx.getIp();
        // 无登录插件 → 免密码登录（仅凭用户名直登）；有登录插件 → 用户名 + 密码校验
        String token;
        if (!bridge.loginRequiresPassword()) {
            if (username == null || username.isEmpty()) {
                fireLogin("", false, "缺少必填参数: username", clientIp);
                return ApiResponse.jsonErrorT(400, "ajax.auth.missing-username", "缺少必填参数: username");
            }
            token = bridge.loginByUsername(username.trim());
            if (token == null) {
                fireLogin(username.trim(), false, "用户名不合法（仅字母/数字/下划线，≤16 字符）或离线登录被策略禁止", clientIp);
                return ApiResponse.jsonErrorT(400, "ajax.auth.username-invalid", "用户名不合法（仅字母/数字/下划线，≤16 字符）或离线登录被策略禁止");
            }
        } else {
            if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
                fireLogin(username == null ? "" : username.trim(), false, "缺少必填参数: username / password", clientIp);
                return ApiResponse.jsonErrorT(400, "ajax.auth.missing-username-password", "缺少必填参数: username / password");
            }
            token = bridge.login(username.trim(), password);
            if (token == null) {
                fireLogin(username.trim(), false, "账号或密码错误（AuthMe 校验失败，或服务器未安装 AuthMe，或禁止离线登录）", clientIp);
                return ApiResponse.jsonErrorT(401, "ajax.auth.bad-credentials", "账号或密码错误（AuthMe 校验失败，或服务器未安装 AuthMe，或禁止离线登录）");
            }
        }
        String player = username.trim();
        boolean remember = isTruthy(form.get("remember"));
        // 记录网页端登录 IP（用于后续游戏端 IP 匹配免登录，开关见 config.yml auto.login.ip.enabled）
        if (clientIp != null && !clientIp.equals("0.0.0.0")) {
            bridge.recordWebLogin(player, clientIp);
        }
        // 设备指纹（可选）：登录请求携带 X-Device-Fingerprint（前端 SoysAuth 自动采集）且 fp 启用时
        // 立即登记设备绑定（登录即绑定，避免下次自动登录走“无绑定放行一次”的迁移路径）
        if (bridge.isFpEnabled()) {
            String fp = fingerprintOf(ctx);
            if (fp != null && !fp.isEmpty()) {
                bridge.registerDevice(player, fp, clientIp, form.get("device"));
            }
        }
        // 登录模式（与 bridge.login 内部同一策略）：玩家在线→online；不在线→offline（离线专属 cookie）
        LoginMode mode = bridge.getLoginModePolicy().decideLogin(player);
        LoginResultEntityVO data = new LoginResultEntityVO();
        data.setPlayer(player);
        data.setToken(token);
        data.setCookieName(bridge.getCookieName());
        data.setTtlSeconds(bridge.getTtlSeconds());
        data.setMode(mode.name().toLowerCase()); // online / offline（离线模式登录标签）
        data.setAuthenticated(true);

        // 记住我（设备免登录）：勾选且启用 → 签发长期设备凭证并下发 HttpOnly Cookie（soys_remember）
        Map<String, String> extra = null;
        if (remember && bridge.isRememberEnabled()) {
            String rememberToken = bridge.issueRemember(player);
            if (rememberToken != null) {
                extra = new HashMap<>();
                extra.put("Set-Cookie", bridge.buildSetCookie(bridge.getRememberCookieName(), rememberToken,
                        bridge.getRememberTtlSeconds()));
                data.setRemember(true);
                data.setRememberCookieName(bridge.getRememberCookieName());
                data.setRememberTtlSeconds(bridge.getRememberTtlSeconds());
            }
        }
        fireLogin(player, true, "登录成功", clientIp);
        return ApiResponse.status(200, AjaxResult.successDataT(data, "ajax.auth.login-success", "登录成功"), extra);
    }

    @Override
    public AjaxResult logout(CredentialPresentation credential) {
        if (bridge == null) {
            return AjaxResult.errorT(503, "ajax.auth.issuer-not-enabled-short", "会话令牌颁发器未启用");
        }
        // 退出登录时清除网页端登录 IP 记录 + 撤销该玩家全部“记住我”设备凭证
        String player = bridge.subjectOf(credential);
        if (player != null) {
            bridge.clearWebLogin(player);
            bridge.revokeRemember(player);
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
        // me 与 checkStatus 已登录分支结构一致，复用 AuthStatusEntity
        AuthStatusEntityVO data = new AuthStatusEntityVO();
        data.setPlayer(player);
        data.setAuthenticated(true);
        data.setOnline(Bukkit.getPlayerExact(player) != null);
        // 登录模式：offline=玩家使用离线模式登录网页（打标签）；online=在线正常登录（含升级后）
        data.setMode(mode == null ? null : mode.name().toLowerCase());
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
        LoginModeEntityVO data = new LoginModeEntityVO();
        data.setRequiresPassword(bridge.loginRequiresPassword());
        data.setCookieName(bridge.getCookieName());
        data.setTtlSeconds(bridge.getTtlSeconds());
        data.setRememberEnabled(bridge.isRememberEnabled());
        data.setRememberTtlSeconds(bridge.getRememberTtlSeconds());
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
                AuthStatusEntityVO data = new AuthStatusEntityVO();
                data.setPlayer(loggedPlayer);
                data.setAuthenticated(true);
                data.setOnline(Bukkit.getPlayerExact(loggedPlayer) != null);
                data.setMode(mode == null ? null : mode.name().toLowerCase());
                data.setIp(clientIp);
                return ApiResponse.status(200, AjaxResult.success(data), null);
            }
        }

        // 1.5) 未登录：尝试“记住我”设备凭证自动登录（soys_remember cookie，精准到个人设备）
        if (bridge.isRememberEnabled() && credential != null) {
            String rememberToken = credential.getCookie(bridge.getRememberCookieName());
            if (rememberToken != null && !rememberToken.isEmpty()) {
                String rememberedPlayer = bridge.resolveRemember(rememberToken);
                if (rememberedPlayer != null) {
                    // —— 设备指纹双因子（auth.yml auto.login.fp.*，默认关闭）——
                    // strict（默认）：指纹缺失 / 与绑定表不一致 → 拒绝自动登录（fpMismatch=true）；
                    // 无绑定记录（存量迁移）：本次放行一次并要求前端立即补绑（fpBindRequired=true，
                    // 同时顺手登记本次指纹，补绑动作本身由前端调 /api/auth/device/register 完成）；
                    // 宽松模式（fp.strict=false）：缺失/不一致均放行（仅打标签，不拒绝）。
                    String fp = fingerprintOf(ctx);
                    boolean fpOk = true;
                    boolean fpBindRequired = false;
                    if (bridge.isFpEnabled()) {
                        if (fp == null || fp.isEmpty()) {
                            fpOk = bridge.isFpStrict() ? false : true;
                        } else if (bridge.isDeviceBound(rememberedPlayer)) {
                            fpOk = bridge.deviceMatches(rememberedPlayer, fp);
                            if (!fpOk) {
                                fpOk = !bridge.isFpStrict(); // strict=拒绝；宽松=放行
                            }
                        } else {
                            fpOk = true; // 存量迁移：无绑定记录 → 本次放行一次
                            fpBindRequired = true;
                            bridge.registerDevice(rememberedPlayer, fp, clientIp, null);
                        }
                    }
                    if (!fpOk) {
                        AuthStatusEntityVO data = new AuthStatusEntityVO();
                        data.setAuthenticated(false);
                        data.setPlayer(rememberedPlayer);
                        data.setFpEnabled(true);
                        data.setFpMismatch(true);
                        data.setIp(clientIp);
                        return ApiResponse.status(200,
                                AjaxResult.successDataT(data, "ajax.auth.fp-mismatch", "设备指纹不匹配，请重新登录并完成设备绑定"),
                                null);
                    }
                    String token = bridge.issueForRemember(rememberedPlayer);
                    if (token != null) {
                        String cookie = bridge.buildSetCookie(bridge.getCookieName(), token,
                                bridge.getTtlSeconds());
                        Map<String, String> extra = new HashMap<>();
                        extra.put("Set-Cookie", cookie);
                        LoginMode mode = bridge.getLoginModePolicy().decideLogin(rememberedPlayer);
                        AuthStatusEntityVO data = new AuthStatusEntityVO();
                        data.setPlayer(rememberedPlayer);
                        data.setAuthenticated(true);
                        data.setOnline(Bukkit.getPlayerExact(rememberedPlayer) != null);
                        data.setMode(mode == null ? null : mode.name().toLowerCase());
                        data.setRememberAutoLogin(true);
                        data.setFpEnabled(bridge.isFpEnabled());
                        data.setFpBindRequired(fpBindRequired);
                        data.setToken(token);
                        data.setCookieName(bridge.getCookieName());
                        data.setTtlSeconds(bridge.getTtlSeconds());
                        data.setIp(clientIp);
                        fireLogin(rememberedPlayer, true, "设备凭证，已自动登录", clientIp);
                        return ApiResponse.status(200,
                                AjaxResult.successDataT(data, "ajax.auth.remember-login-success", "设备凭证，已自动登录"),
                                extra);
                    }
                }
            }
        }

        // 2) 未登录：使用传入的 player 参数，检查游戏端登录状态 + IP 匹配
        if (player == null || player.isEmpty()) {
            // 无 player 参数 → 返回未登录状态（供前端判断是否需要显示登录表单）
            AuthStatusEntityVO data = new AuthStatusEntityVO();
            data.setAuthenticated(false);
            data.setIp(clientIp);
            return ApiResponse.status(200, AjaxResult.success(data), null);
        }

        // 旧 IP 匹配免登录开关（config.yml auto.login.ip.enabled，默认 false）：关闭时不做 IP 自动登录
        if (!bridge.isIpEnabled()) {
            AuthStatusEntityVO data = new AuthStatusEntityVO();
            data.setAuthenticated(false);
            data.setPlayer(player);
            data.setIp(clientIp);
            return ApiResponse.status(200, AjaxResult.success(data), null);
        }

        // 检查该玩家是否在游戏端已登录且 IP 匹配
        boolean gameLoggedIn = bridge.isGameLoggedIn(player);
        boolean ipMatched = gameLoggedIn && clientIp != null && !clientIp.equals("0.0.0.0")
                && bridge.gameIpMatches(player, clientIp);

        if (!gameLoggedIn || !ipMatched) {
            AuthStatusEntityVO data = new AuthStatusEntityVO();
            data.setAuthenticated(false);
            data.setPlayer(player);
            data.setGameLoggedIn(gameLoggedIn);
            data.setIpMatched(ipMatched);
            data.setIp(clientIp);
            return ApiResponse.status(200, AjaxResult.success(data), null);
        }

        // 3) 游戏端已登录 + IP 匹配 → 自动签发在线令牌（游戏→网页自动登录）
        String token = bridge.issueToken(player, LoginMode.ONLINE);
        // 记录网页端登录 IP（后续游戏端重连时可用于免登录）
        if (clientIp != null && !clientIp.equals("0.0.0.0")) {
            bridge.recordWebLogin(player, clientIp);
        }
        String cookie = bridge.buildSetCookie(bridge.getCookieName(), token, bridge.getTtlSeconds());
        Map<String, String> extra = new HashMap<>();
        extra.put("Set-Cookie", cookie);
        AuthStatusEntityVO data = new AuthStatusEntityVO();
        data.setPlayer(player);
        data.setAuthenticated(true);
        data.setOnline(true);
        data.setMode("online");
        data.setAutoLogin(true);
        data.setToken(token);
        data.setCookieName(bridge.getCookieName());
        data.setTtlSeconds(bridge.getTtlSeconds());
        data.setIp(clientIp);
        fireLogin(player, true, "IP 匹配，已自动登录", clientIp);
        return ApiResponse.status(200, AjaxResult.successDataT(data, "ajax.auth.auto-login-success", "IP 匹配，已自动登录"), extra);
    }

    @Override
    public AjaxResult registerDevice(CredentialPresentation credential, ApiRequestContext ctx, String body) {
        if (bridge == null) {
            return AjaxResult.errorT(503, "ajax.auth.issuer-not-enabled-short", "会话令牌颁发器未启用");
        }
        String player = bridge.subjectOf(credential);
        if (player == null) {
            return AjaxResult.unauthorizedT("ajax.auth.not-logged-in", "未登录或凭证无效");
        }
        Map<String, String> form = parseBody(body);
        String fp = form.get("fingerprint");
        if (fp == null || fp.isEmpty()) {
            return AjaxResult.errorT(400, "ajax.auth.fp-missing", "缺少必填参数: fingerprint");
        }
        String clientIp = ctx == null ? null : ctx.getIp();
        boolean ok = bridge.registerDevice(player, fp, clientIp, form.get("device"));
        if (!ok) {
            return AjaxResult.errorT(500, "ajax.auth.fp-bind-fail", "设备登记失败，请稍后重试");
        }
        return AjaxResult.successT("ajax.auth.device-bound", "设备已登记（本设备免登录）");
    }

    @Override
    public ApiResponse bindDevice(String body) {
        if (bridge == null) {
            return ApiResponse.jsonErrorT(503, "ajax.auth.issuer-not-enabled", "会话令牌颁发器未启用（请在 gateway/issuers/session-token.yml 设 enabled: true）");
        }
        Map<String, String> form = parseBody(body);
        String ticket = form.get("ticket");
        String fp = form.get("fingerprint");
        if (ticket == null || ticket.isEmpty() || fp == null || fp.isEmpty()) {
            return ApiResponse.jsonErrorT(400, "ajax.auth.fp-bind-missing", "缺少必填参数: ticket / fingerprint");
        }
        // 消费一次性票据（与 /api/auth/issue 共用票据池：任一成功即失效，防重放）
        String player = bridge.consumeTicket(ticket);
        if (player == null) {
            return ApiResponse.jsonErrorT(400, "ajax.auth.login-ticket-used", "登录票据无效或已使用，请重新登录游戏以获取新的网页登录链接");
        }
        if (!bridge.registerDevice(player, fp, null, form.get("device"))) {
            return ApiResponse.jsonErrorT(500, "ajax.auth.fp-bind-fail", "设备绑定失败，请稍后重试");
        }
        // 绑定成功 → 签发“记住我”设备凭证（启用时）：浏览器下次访问 /api/auth/status 即自动登录
        // （新绑定的指纹与后续请求一致，双因子校验通过）。不直接下发会话 Cookie——自动登录链
        // 完整走一遍 checkStatus ② 分支，保证双因子判定与正常路径一致。
        Map<String, String> extra = null;
        LoginResultEntityVO data = new LoginResultEntityVO();
        data.setPlayer(player);
        data.setAuthenticated(true);
        if (bridge.isRememberEnabled()) {
            String rememberToken = bridge.issueRemember(player);
            if (rememberToken != null) {
                extra = new HashMap<>();
                extra.put("Set-Cookie", bridge.buildSetCookie(bridge.getRememberCookieName(), rememberToken,
                        bridge.getRememberTtlSeconds()));
                data.setRemember(true);
                data.setRememberCookieName(bridge.getRememberCookieName());
                data.setRememberTtlSeconds(bridge.getRememberTtlSeconds());
            }
        }
        return ApiResponse.status(200,
                AjaxResult.successDataT(data, "ajax.auth.device-bind-success", "设备绑定成功，已自动登录"), extra);
    }

    /**
     * 触发登录结果事件（异步事件：登录流程在 HTTP 处理线程池）。
     * 附属插件（如 MCERP 登录日志）可监听 {@link GatewayEvent.GatewayLoginResultEvent} 记录成败。
     */
    private static void fireLogin(String player, boolean success, String reason, String ip) {
        try {
            Bukkit.getPluginManager().callEvent(
                    new GatewayEvent.GatewayLoginResultEvent(player, success, reason, ip));
        } catch (Throwable ignored) {
        }
    }

    /**
     * SSO 跨域回跳换票（GET /api/auth/sso/callback?ticket=）：
     * 消费一次性票据 → 为本服签发会话 Cookie → 302 回票据登记的 redirectUrl（白名单校验）。
     * 票据无效/已消费/过期 → 302 回登录页（不暴露错误细节，防探测）。
     */
    @Override
    public ApiResponse ssoCallback(String ticket) {
        if (bridge == null) {
            return ApiResponse.jsonErrorT(503, "ajax.auth.issuer-not-enabled", "会话令牌颁发器未启用");
        }
        SoysSsoTicket row = bridge.consumeTicketRow(ticket);
        if (row == null || row.getSubject() == null || row.getSubject().isEmpty()) {
            return redirect302("/login.html");
        }
        String subject = row.getSubject();
        LoginMode mode = bridge.getLoginModePolicy() == null
                ? LoginMode.OFFLINE
                : bridge.getLoginModePolicy().decideLogin(subject);
        String token = bridge.issueToken(subject, mode);
        if (token == null) {
            return redirect302("/login.html");
        }
        // 回跳目标白名单校验（相对路径=站内放行；跨域 URL origin 须在 sso.allowed-origins）
        String target = row.getRedirectUrl();
        if (target == null || target.isEmpty() || !bridge.isSsoTargetAllowed(target)) {
            target = "/";
        }
        Map<String, String> extra = new HashMap<>();
        extra.put("Set-Cookie", bridge.buildSetCookie(bridge.getCookieName(), token, bridge.getTtlSeconds()));
        extra.put("Location", target);
        fireLogin(subject, true, "SSO 跨域回跳登录", row.getClientIp());
        return ApiResponse.status(302, AjaxResult.success("ok", ""), extra);
    }

    /** 302 跳转（空响应体）。 */
    private static ApiResponse redirect302(String location) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Location", location);
        return ApiResponse.status(302, AjaxResult.success("ok", ""), headers);
    }

    /**
     * 从请求头提取设备指纹（X-Device-Fingerprint，大小写不敏感）；缺失返回 null。
     */
    private static String fingerprintOf(ApiRequestContext ctx) {
        if (ctx == null || ctx.getHeaders() == null) return null;
        for (Map.Entry<String, String> e : ctx.getHeaders().entrySet()) {
            if (e.getKey() != null && AuthLoginBridge.FP_HEADER.equalsIgnoreCase(e.getKey())) {
                String v = e.getValue();
                return (v == null || v.trim().isEmpty()) ? null : v.trim();
            }
        }
        return null;
    }

    /**
     * 解析“记住我”勾选：true/1/on/yes 视为勾选。
     */
    private static boolean isTruthy(String v) {
        if (v == null) return false;
        String s = v.trim().toLowerCase();
        return s.equals("true") || s.equals("1") || s.equals("on") || s.equals("yes");
    }

    /**
     * 解析登录请求体：优先 JSON（{"username":..,"password":..}），其次表单（username=..&password=..）。
     */
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
