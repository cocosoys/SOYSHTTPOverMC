package com.github.cocosoys.mc.soyshttpovermc.spring.controller;

import com.github.cocosoys.mc.soyshttpovermc.util.AjaxResult;
import com.github.cocosoys.mc.soyshttpovermc.annotations.ApiName;
import com.github.cocosoys.mc.soyshttpovermc.annotations.ApiPermission;
import com.github.cocosoys.mc.soyshttpovermc.annotations.GetMapping;
import com.github.cocosoys.mc.soyshttpovermc.spring.service.IStatusService;

/**
 * 隧道状态 API（控制器层，仿 Spring MVC / MyBatis-Plus）：
 * <b>只做接口声明与抽象调用</b>——声明映射注解与端点，调用 {@link IStatusService} 获取实体；
 * 业务拼装逻辑全部在 service 实现类，控制器不持有任何数据来源依赖。
 *
 * <p>GET /status → 实际路由 /api/status（auth 开启时自动加 /api 前缀）
 * → AjaxResult {code,msg,data:StatusEntity}。</p>
 */
public class StatusController {

    private final IStatusService statusService;

    public StatusController(IStatusService statusService) {
        this.statusService = statusService;
    }

    @ApiName("隧道状态")
    @ApiPermission("soyshttp:api:status")
    @GetMapping("/status")
    public AjaxResult status() {
        return AjaxResult.success(statusService.getStatus());
    }
}
