package com.github.cocosoys.mc.soyshttpovermc.spring.controller;

import com.github.cocosoys.mc.soyshttpovermc.annotations.ApiName;
import com.github.cocosoys.mc.soyshttpovermc.annotations.ApiPublic;
import com.github.cocosoys.mc.soyshttpovermc.annotations.GetMapping;
import com.github.cocosoys.mc.soyshttpovermc.spring.service.ISystemService;
import com.github.cocosoys.mc.soyshttpovermc.util.AjaxResult;
import com.github.cocosoys.mc.soyshttpovermc.web.ApiRequestContext;

import java.util.HashMap;
import java.util.Map;

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
    @ApiPublic
    @GetMapping("/ping")
    public AjaxResult ping() {
        // 公开端点（首页/探活脚本免凭证获取在线状态）；auth.yml 的 exempt 也一并豁免，双重保险
        return AjaxResult.success(systemService.ping());
    }

    @ApiName("网关版本")
    @ApiPublic
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
    @ApiPublic
    @GetMapping("/whoami")
    public AjaxResult whoami(ApiRequestContext ctx) {
        Map<String, Object> data = new HashMap<>();
        data.put("ip", ctx.getIp());
        data.put("method", ctx.getHttpMethod());
        data.put("path", ctx.getPath());
        data.put("authenticated", ctx.isAuthenticated());
        data.put("player", ctx.getPlayerName());
        data.put("online", ctx.getPlayer() != null);
        return AjaxResult.success(data);
    }
}
