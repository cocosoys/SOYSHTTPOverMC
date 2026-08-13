package soys.soyshttpovermc.api.spring.entity;

import lombok.Data;

import java.io.Serializable;

/**
 * 隧道状态 - 近期请求项实体（嵌套于 {@link StatusEntity} 的 recent 列表）。
 * ms 为 null 表示无延迟样本。
 */
@Data
public class RecentRequestEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    private String method;
    private String path;
    private Integer code;
    private Double ms;
}
