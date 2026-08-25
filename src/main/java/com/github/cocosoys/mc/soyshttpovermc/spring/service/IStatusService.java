package com.github.cocosoys.mc.soyshttpovermc.spring.service;

import com.github.cocosoys.mc.soyshttpovermc.spring.entity.RecentRequestEntity;
import com.github.cocosoys.mc.soyshttpovermc.spring.entity.StatusEntity;

import java.util.List;

/**
 * 隧道状态 Service 接口（业务抽象声明，仿 MyBatis-Plus 的 XxxService）：
 * 控制器 {@code StatusApi} 仅依赖本接口，不感知数据来源
 * （隧道统计 / 内存 / 未来外部存储均可替换实现），满足依赖倒置。
 */
public interface IStatusService extends IBaseService<StatusEntity> {

    /** 组装完整隧道状态实体：在线 / 端口 / Bot / 运行时长 / 请求计数 / 延迟 / 近期请求 */
    StatusEntity getStatus();

    /** 近期请求快照（轻量，供探测/调试复用） */
    List<RecentRequestEntity> getRecentRequests();
}
