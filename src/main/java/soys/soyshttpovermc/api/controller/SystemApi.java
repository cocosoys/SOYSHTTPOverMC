package soys.soyshttpovermc.api.controller;

import soys.soyshttpovermc.api.util.AjaxResult;
import soys.soyshttpovermc.api.annotations.ApiName;
import soys.soyshttpovermc.api.annotations.ApiPermission;
import soys.soyshttpovermc.api.annotations.GetMapping;
import soys.soyshttpovermc.api.entity.SystemInfoEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * 内置系统 API（演示注解式 API 框架的用法，也是网关的存活检测端点）：
 * GET /ping → 实际路由为 /api/ping（auth 开启时自动加 /api 前缀）。
 * 需要 API 凭证（auth 策略先行），可用 X-API-Key / Bearer / Cookie 任一形态。
 */
public class SystemApi {

    private final int port;

    public SystemApi(int port) {
        this.port = port;
    }

    @ApiName("网关存活检测")
    @ApiPermission("soyshttp:api:ping")
    @GetMapping("/ping")
    public AjaxResult ping() {
        // 公开端点（auth.yml 的 exempt 已豁免鉴权）：供首页/探活脚本免凭证获取服务器在线状态
        Map<String, Object> data = new HashMap<>();
        data.put("pong", true);
        data.put("time", System.currentTimeMillis());
        data.put("name", "SOYSHTTPOverMC");
        data.put("port", port);
        data.put("online", true);
        return AjaxResult.success(data);
    }

    @ApiName("网关版本")
    @GetMapping("/version")
    public AjaxResult version() {
        // 实体类用法演示：返回继承 BaseEntity 的实体，JsonWriter 反射序列化
        SystemInfoEntity info = new SystemInfoEntity("SOYSHTTPOverMC", "1.0.0",
                "三协议端口: MC / 明文 HTTP / HTTPS", port);
        return AjaxResult.success(info);
    }
}
