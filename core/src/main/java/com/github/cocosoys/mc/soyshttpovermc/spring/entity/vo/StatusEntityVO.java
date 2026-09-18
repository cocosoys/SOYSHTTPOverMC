package com.github.cocosoys.mc.soyshttpovermc.spring.entity.vo;

import com.github.cocosoys.mc.soyshttpovermc.spring.entity.BaseEntity;
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
public class StatusEntityVO extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 隧道是否在线。 */
    private Boolean online;

    /** 监听端口。 */
    private Integer port;

    /** 已运行时长（毫秒）。 */
    private Long uptimeMillis;

    /** 已运行时长（人类可读，如 3d 5h）。 */
    private String uptime;

    /** 请求计数（嵌套）。 */
    private RequestCountEntityVO requests;

    /** 延迟样本（嵌套）。 */
    private LatencyEntityVO latency;

    /** 近期请求列表（嵌套）。 */
    private List<RecentRequestEntityVO> recent;
}
