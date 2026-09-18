package com.github.cocosoys.mc.soyshttpovermc.spring.entity.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 隧道状态 - 延迟实体（嵌套于 {@link StatusEntityVO}）。
 * avgMs/maxMs 为 null 表示暂无样本。
 */
@Data
public class LatencyEntityVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 平均延迟（毫秒；null = 暂无样本）。 */
    private Double avgMs;

    /** 最大延迟（毫秒；null = 暂无样本）。 */
    private Double maxMs;
}
