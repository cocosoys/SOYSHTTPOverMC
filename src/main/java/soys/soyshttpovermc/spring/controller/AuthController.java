package soys.soyshttpovermc.spring.controller;

import soys.soyshttpovermc.annotations.*;
import soys.soyshttpovermc.gateway.policy.auth.issuer.CredentialPresentation;
import soys.soyshttpovermc.spring.service.IAuthService;
import soys.soyshttpovermc.util.AjaxResult;

/**
 * 登录窗口认证 API（控制器层，仿 Spring MVC / MyBatis-Plus）：
 * <b>只做接口声明与抽象调用</b>——声明映射注解与端点，调用 {@link IAuthService}。
 *
 * <p>三个端点（供网页登录窗口 web/login.html 调用）：
 * <ul>
 *   <li>{@code POST /api/auth/login} —— 玩家名 + AuthMe 密码登录，成功返回会话令牌
 *       （auth.yml 的 exempt /auth/* 已豁免，登录前无需凭证）；</li>
 *   <li>{@code POST /api/auth/logout} —— 撤销当前凭证对应的会话令牌（需已登录）；</li>
 *   <li>{@code GET /api/auth/me} —— 当前登录信息（玩家名 + 在线状态，需已登录）。</li>
 * </ul>
 * logout / me 依赖网关自动注入的 {@link CredentialPresentation} 参数（ApiRegistry 凭证注入），
 * 无凭证请求会被 AuthPolicy 先以 401 拒绝；三者均标 {@link ApiPublic}（仅需登录、不做权限镜像）。
 */
@RequestMapping("/auth")
public class AuthController {

    private final IAuthService authService;

    public AuthController(IAuthService authService) {
        this.authService = authService;
    }

    @ApiName("登录")
    @ApiPublic
    @PostMapping("/login")
    public AjaxResult login(@RequestBody String body) {
        return authService.login(body);
    }

    @ApiName("退出登录")
    @ApiPublic
    @PostMapping("/logout")
    public AjaxResult logout(CredentialPresentation credential) {
        return authService.logout(credential);
    }

    @ApiName("登录信息")
    @ApiPublic
    @GetMapping("/me")
    public AjaxResult me(CredentialPresentation credential) {
        return authService.me(credential);
    }
}
