package soys.soyshttpovermc.api.spring.entity;

import lombok.Data;

import java.io.Serializable;

/**
 * 隧道状态 - 延迟实体（嵌套于 {@link StatusEntity}）。
 * avgMs/maxMs 为 null 表示暂无样本。
 */
@Data
public class LatencyEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    private Double avgMs;
    private Double maxMs;
}
