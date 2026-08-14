package soys.soyshttpovermc.spring.service;

import soys.soyshttpovermc.gateway.policy.auth.issuer.CredentialPresentation;
import soys.soyshttpovermc.util.AjaxResult;

/**
 * 登录窗口认证 Service 接口（仿 MyBatis-Plus 的 {@code XxxService}）：
 * 登录 / 退出 / 登录信息三个端点，供网页登录窗口页面（web/login.html）调用。
 * 业务校验与令牌签发复用 {@code AuthLoginBridge}（AuthMe 密码校验 + session-token 颁发器）。
 */
public interface IAuthService {

    /**
     * 登录：body 为 JSON 或表单的 {@code {username, password}}。
     * 成功返回 {@code {player, token, cookieName, ttlSeconds}}；失败返回 401。
     */
    AjaxResult login(String body);

    /** 退出登录：撤销当前请求凭证对应的会话令牌。 */
    AjaxResult logout(CredentialPresentation credential);

    /** 登录信息：返回当前凭证绑定的玩家名与在线状态（未登录返回 401）。 */
    AjaxResult me(CredentialPresentation credential);
}
