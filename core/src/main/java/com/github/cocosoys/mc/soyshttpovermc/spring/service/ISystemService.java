package com.github.cocosoys.mc.soyshttpovermc.spring.service;

import com.github.cocosoys.mc.soyshttpovermc.spring.entity.vo.PingEntityVO;
import com.github.cocosoys.mc.soyshttpovermc.spring.entity.vo.SystemInfoEntityVO;
import com.github.cocosoys.mc.soyshttpovermc.spring.entity.vo.WhoAmIEntityVO;
import com.github.cocosoys.mc.soyshttpovermc.web.ApiRequestContext;


/**
 * 系统级 Service 接口（业务抽象声明，仿 MyBatis-Plus 的 XxxService）：
 * 控制器 {@code SystemApi} 依赖本接口获取存活检测与版本信息，不感知具体组装逻辑。
 */
public interface ISystemService extends IBaseService<SystemInfoEntityVO> {

    /**
     * 存活检测数据：{pong, time, name, port, online}（实体化返回）
     */
    PingEntityVO ping();

    /**
     * 网关版本信息实体
     */
    SystemInfoEntityVO getVersion();

    /**
     * 请求上下文演示：从 {@link ApiRequestContext} 组装 whoami 实体（替代 controller 内 Map 组装）。
     */
    WhoAmIEntityVO whoAmI(ApiRequestContext ctx);
}
