package com.github.cocosoys.mc.soyshttpovermc.spring.entity.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 隧道状态 - 近期请求项实体（嵌套于 {@link StatusEntityVO} 的 recent 列表）。
 * ms 为 null 表示无延迟样本。
 */
@Data
public class RecentRequestEntityVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 请求方法（GET/POST/...）。 */
    private String method;

    /** 请求路径。 */
    private String path;

    /** HTTP 状态码。 */
    private Integer code;

    /** 处理耗时（毫秒；null = 无延迟样本）。 */
    private Double ms;
}
