package com.github.cocosoys.mc.soyshttpovermc.spring.entity.vo;

import com.github.cocosoys.mc.soyshttpovermc.spring.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 存活检测实体（{@code GET /api/ping} 返回体，替代临时 Map 组装）：
 * 由 {@code SystemServiceImpl.ping()} 组装，供首页 / 探活脚本免凭证获取在线状态。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PingEntityVO extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 恒为 true（能返回即存活） */
    private boolean pong;

    /** 服务器当前毫秒时间戳 */
    private long time;

    /** 网关名称（SOYSHTTPOverMC） */
    private String name;

    /** 明文 HTTP 端口 */
    private int port;

    /** 恒为 true */
    private boolean online;

    public PingEntityVO() {
    }
}
