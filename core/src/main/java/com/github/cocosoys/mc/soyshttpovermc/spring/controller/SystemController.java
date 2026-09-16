package com.github.cocosoys.mc.soyshttpovermc.spring.controller;

import com.github.cocosoys.mc.soyshttpovermc.annotations.ApiName;
import com.github.cocosoys.mc.soyshttpovermc.annotations.ApiPublic;
import com.github.cocosoys.mc.soyshttpovermc.annotations.Anonymous;
import com.github.cocosoys.mc.soyshttpovermc.annotations.GetMapping;
import com.github.cocosoys.mc.soyshttpovermc.spring.service.ISystemService;
import com.github.cocosoys.mc.soyshttpovermc.util.AjaxResult;
import com.github.cocosoys.mc.soyshttpovermc.web.ApiRequestContext;


/**
 * 内置系统 API（控制器层，仿 Spring MVC / MyBatis-Plus）：
 * <b>只做接口声明与抽象调用</b>——声明映射注解与端点，调用 {@link ISystemService}。
 * 也是网关的存活检测端点（/api/ping，auth.yml 的 exempt 已豁免鉴权）。
 */
public class SystemController {

    private final ISystemService systemService;

    public SystemController(ISystemService systemService) {
        this.systemService = systemService;
    }

    @ApiName("网关存活检测")
    @Anonymous
    @GetMapping("/ping")
    public AjaxResult ping() {
        // 匿名端点（首页/探活脚本免凭证获取在线状态）；@Anonymous 认证门放行 + auth.yml exempt 双保险
        return AjaxResult.success(systemService.ping());
    }

    @ApiName("网关版本")
    @Anonymous
    @GetMapping("/version")
    public AjaxResult version() {
        // 调用 service 获取版本信息实体（演示实体类用法）
        return AjaxResult.success(systemService.getVersion());
    }

    /**
     * 请求上下文演示端点（公开）：展示 {@link ApiRequestContext} 参数注入——
     * 开发者无需自行解析请求头/令牌，直接拿到客户端 IP / 玩家名 / 玩家实体 / 凭证等。
     */
    @ApiName("请求上下文")
    @Anonymous
    @GetMapping("/whoami")
    public AjaxResult whoami(ApiRequestContext ctx) {
        // 组装下沉至 service 层（SystemServiceImpl.whoAmI 返回实体），controller 不做数据拼装
        return AjaxResult.success(systemService.whoAmI(ctx));
    }
}
