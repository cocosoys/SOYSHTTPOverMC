package com.github.cocosoys.mc.soyshttpovermc.spring.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 隧道状态实体（{@link BaseEntity} 的实现）：GET /status 的返回数据载体。
 * <pre>
 * data: {
 *   online, port, uptimeMillis, uptime,
 *   requests: {total, get, post, other},
 *   latency:  {avgMs, maxMs},
 *   recent:   [{method, path, code, ms}, ...]
 * }
 * </pre>
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class StatusEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    private Boolean online;
    private Integer port;
    private Long uptimeMillis;
    private String uptime;
    private RequestCountEntity requests;
    private LatencyEntity latency;
    private List<RecentRequestEntity> recent;
}
