package soys.soyshttpovermc.spring.service;

import soys.soyshttpovermc.spring.entity.SystemInfoEntity;

import java.util.Map;

/**
 * 系统级 Service 接口（业务抽象声明，仿 MyBatis-Plus 的 XxxService）：
 * 控制器 {@code SystemApi} 依赖本接口获取存活检测与版本信息，不感知具体组装逻辑。
 */
public interface ISystemService extends IBaseService<SystemInfoEntity> {

    /** 存活检测数据：{pong, time, name, port, online} */
    Map<String, Object> ping();

    /** 网关版本信息实体 */
    SystemInfoEntity getVersion();
}
