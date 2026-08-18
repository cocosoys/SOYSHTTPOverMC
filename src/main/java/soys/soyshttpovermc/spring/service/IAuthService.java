package soys.soyshttpovermc.spring.service;

import soys.soyshttpovermc.gateway.policy.auth.issuer.CredentialPresentation;
import soys.soyshttpovermc.util.AjaxResult;
import soys.soyshttpovermc.util.ApiResponse;

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
     */
    AjaxResult login(String body);

    /** 退出登录：撤销当前请求凭证对应的会话令牌。 */
    AjaxResult logout(CredentialPresentation credential);

    /** 登录信息：返回当前凭证绑定的玩家名与在线状态（未登录返回 401）。 */
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

    /** 登录模式信息：{@code GET /api/auth/mode} → {requiresPassword, cookieName, ttlSeconds}（前端切换表单用）。 */
    AjaxResult loginMode();
}
