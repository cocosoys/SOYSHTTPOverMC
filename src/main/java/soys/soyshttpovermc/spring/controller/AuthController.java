package soys.soyshttpovermc.spring.controller;

import soys.soyshttpovermc.annotations.*;
import soys.soyshttpovermc.gateway.policy.auth.issuer.CredentialPresentation;
import soys.soyshttpovermc.spring.service.IAuthService;
import soys.soyshttpovermc.util.AjaxResult;
import soys.soyshttpovermc.util.ApiResponse;

/**
 * 登录窗口认证 API（控制器层，仿 Spring MVC / MyBatis-Plus）：
 * <b>只做接口声明与抽象调用</b>——声明映射注解与端点，调用 {@link IAuthService}。
 *
 * <p>两个登录通道：
 * <ul>
 *   <li><b>弹窗登录</b>（web/login.html 调试页 / SoysAuth 登录弹窗调用）：
 *       POST /api/auth/login（玩家名+AuthMe 密码 → 返回令牌）、POST /api/auth/logout、GET /api/auth/me；</li>
 *   <li><b>票据登录</b>（游戏内链接 → 前端登录页 login.html）：
 *       GET /api/auth/login?ticket=（302 到 /login.html?ticket=）、POST /api/auth/issue
 *       （票据+密码校验 → JSON + Set-Cookie）、GET /api/auth/mode（登录模式探测）。</li>
 * </ul>
 * logout / me 依赖网关自动注入的 {@link CredentialPresentation} 参数（ApiRegistry 凭证注入），
 * 无凭证请求会被 AuthPolicy 先以 401 拒绝；各端点均标 {@link ApiPublic}（仅需登录、不做权限镜像）。
 */
@RequestMapping("/auth")
public class AuthController {

    private final IAuthService authService;

    public AuthController(IAuthService authService) {
        this.authService = authService;
    }

    @ApiName("弹窗登录")
    @ApiPublic
    @PostMapping("/login")
    public AjaxResult login(@RequestBody String body) {
        return authService.login(body);
    }

    @ApiName("票据登录入口")
    @ApiPublic
    @GetMapping("/login")
    public ApiResponse serveLogin(@RequestParam(name = "ticket", required = false) String ticket) {
        return authService.serveLogin(ticket);
    }

    @ApiName("票据/免密登录提交")
    @ApiPublic
    @PostMapping("/issue")
    public ApiResponse issue(@RequestBody String body) {
        return authService.issue(body);
    }

    @ApiName("登录模式")
    @ApiPublic
    @GetMapping("/mode")
    public AjaxResult mode() {
        return authService.loginMode();
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
