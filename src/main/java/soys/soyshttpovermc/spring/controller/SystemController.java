package soys.soyshttpovermc.spring.controller;

import soys.soyshttpovermc.util.AjaxResult;
import soys.soyshttpovermc.annotations.ApiName;
import soys.soyshttpovermc.annotations.ApiPermission;
import soys.soyshttpovermc.annotations.ApiPublic;
import soys.soyshttpovermc.annotations.GetMapping;
import soys.soyshttpovermc.spring.service.ISystemService;

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
    @ApiPermission("soyshttp:api:ping")
    @GetMapping("/ping")
    public AjaxResult ping() {
        // 调用 service 获取存活数据（公开端点，auth 豁免）；供首页/探活脚本免凭证获取在线状态
        return AjaxResult.success(systemService.ping());
    }

    @ApiName("网关版本")
    @ApiPublic
    @GetMapping("/version")
    public AjaxResult version() {
        // 调用 service 获取版本信息实体（演示实体类用法）
        return AjaxResult.success(systemService.getVersion());
    }
}
