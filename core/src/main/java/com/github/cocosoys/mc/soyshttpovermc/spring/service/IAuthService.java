package com.github.cocosoys.mc.soyshttpovermc.spring.service;

import com.github.cocosoys.mc.soyshttpovermc.util.AjaxResult;
import com.github.cocosoys.mc.soyshttpovermc.util.ApiResponse;
import com.github.cocosoys.mc.soyshttpovermc.web.ApiRequestContext;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.issuer.CredentialPresentation;

/**
 * 登录窗口认证 Service 接口（仿 MyBatis-Plus 的 {@code XxxService}）：
 * 弹窗登录（/api/auth/login|logout|me）与票据登录（/api/auth/login|issue|mode）两类流程，
 * 供网页登录窗口页面（web/login.html）与 in-game 登录链接调用。
 * 业务校验与令牌签发复用 {@code AuthLoginBridge}（AuthMe 密码校验 + session-token 颁发器）。
 */
public interface IAuthService {

    /**
     * 弹窗登录：body 为 JSON 或表单的 {@code {username, password}}。
     * 成功返回 {@code {player, token, cookieName, ttlSeconds}}；失败返回 401。
     *
     * @param ctx 请求上下文（用于记录网页端登录 IP，实现 IP 匹配免登录）
     */
    ApiResponse login(String body, ApiRequestContext ctx);

    /**
     * 退出登录：撤销当前请求凭证对应的会话令牌。
     */
    AjaxResult logout(CredentialPresentation credential);

    /**
     * 登录信息：返回当前凭证绑定的玩家名与在线状态（未登录返回 401）。
     */
    AjaxResult me(CredentialPresentation credential);

    /**
     * 票据登录入口：{@code GET /api/auth/login?ticket=...}。
     * 有登录插件=票据+密码二次验证（票据有效 → 302 到前端登录页 /login.html?ticket=...，无效 → 400 JSON）；
     * 无登录插件=免密码，直接 302 到前端登录页。
     */
    ApiResponse serveLogin(String ticket);

    /**
     * 票据/免密登录提交：{@code POST /api/auth/issue}（body 为表单）。
     * 有登录插件 → 解析 {@code ticket+password} 校验；无登录插件 → 解析 {@code username} 直登。
     * 成功返回 JSON 成功体 + Set-Cookie（令牌仅验证通过后下发一次）。
     */
    ApiResponse issue(String body);

    /**
     * 登录模式信息：{@code GET /api/auth/mode} → {requiresPassword, cookieName, ttlSeconds}（前端切换表单用）。
     */
    AjaxResult loginMode();

    /**
     * 登录状态检查：{@code GET /api/auth/status}。
     * <ul>
     *   <li>当前请求已登录 → 返回已登录状态（player, authenticated, online, mode）；</li>
     *   <li>未登录但携带 {@code player} 参数 → 检查该玩家是否在游戏端已登录且 IP 匹配，
     *       匹配则自动签发令牌并返回 Set-Cookie（游戏→网页自动登录）；</li>
     *   <li>未登录且无 player 参数或不匹配 → 返回未登录状态。</li>
     * </ul>
     * 用于网页端首次打开时自动检测游戏端登录状态，实现 IP 匹配免密登录。
     */
    ApiResponse checkStatus(ApiRequestContext ctx, String player);
}
