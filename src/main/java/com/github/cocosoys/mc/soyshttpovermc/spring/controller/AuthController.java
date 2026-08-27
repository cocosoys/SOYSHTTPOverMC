package com.github.cocosoys.mc.soyshttpovermc.spring.controller;
import com.github.cocosoys.mc.soyshttpovermc.annotations.GetMapping;

import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.issuer.CredentialPresentation;
import com.github.cocosoys.mc.soyshttpovermc.spring.service.IAuthService;
import com.github.cocosoys.mc.soyshttpovermc.util.AjaxResult;
import com.github.cocosoys.mc.soyshttpovermc.util.ApiResponse;

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
 * 无凭证请求会被 AuthPolicy 先以 401 拒绝；各端点均标 {@link com.github.cocosoys.mc.soyshttpovermc.annotations.ApiPublic}（仅需登录、不做权限镜像）。
 */
@com.github.cocosoys.mc.soyshttpovermc.annotations.RequestMapping("/auth")
public class AuthController {

    private final IAuthService authService;

    public AuthController(IAuthService authService) {
        this.authService = authService;
    }

    @com.github.cocosoys.mc.soyshttpovermc.annotations.ApiName("弹窗登录")
    @com.github.cocosoys.mc.soyshttpovermc.annotations.ApiPublic
    @com.github.cocosoys.mc.soyshttpovermc.annotations.PostMapping("/login")
    public AjaxResult login(@com.github.cocosoys.mc.soyshttpovermc.annotations.RequestBody String body) {
        return authService.login(body);
    }

    @com.github.cocosoys.mc.soyshttpovermc.annotations.ApiName("票据登录入口")
    @com.github.cocosoys.mc.soyshttpovermc.annotations.ApiPublic
    @com.github.cocosoys.mc.soyshttpovermc.annotations.GetMapping("/login")
    public ApiResponse serveLogin(@com.github.cocosoys.mc.soyshttpovermc.annotations.RequestParam(name = "ticket", required = false) String ticket) {
        return authService.serveLogin(ticket);
    }

    @com.github.cocosoys.mc.soyshttpovermc.annotations.ApiName("票据/免密登录提交")
    @com.github.cocosoys.mc.soyshttpovermc.annotations.ApiPublic
    @com.github.cocosoys.mc.soyshttpovermc.annotations.PostMapping("/issue")
    public ApiResponse issue(@com.github.cocosoys.mc.soyshttpovermc.annotations.RequestBody String body) {
        return authService.issue(body);
    }

    @com.github.cocosoys.mc.soyshttpovermc.annotations.ApiName("登录模式")
    @com.github.cocosoys.mc.soyshttpovermc.annotations.ApiPublic
    @com.github.cocosoys.mc.soyshttpovermc.annotations.GetMapping("/mode")
    public AjaxResult mode() {
        return authService.loginMode();
    }

    @com.github.cocosoys.mc.soyshttpovermc.annotations.ApiName("退出登录")
    @com.github.cocosoys.mc.soyshttpovermc.annotations.ApiPublic
    @com.github.cocosoys.mc.soyshttpovermc.annotations.PostMapping("/logout")
    public AjaxResult logout(CredentialPresentation credential) {
        return authService.logout(credential);
    }

    @com.github.cocosoys.mc.soyshttpovermc.annotations.ApiName("登录信息")
    @com.github.cocosoys.mc.soyshttpovermc.annotations.ApiPublic
    @GetMapping("/me")
    public AjaxResult me(CredentialPresentation credential) {
        return authService.me(credential);
    }
}
