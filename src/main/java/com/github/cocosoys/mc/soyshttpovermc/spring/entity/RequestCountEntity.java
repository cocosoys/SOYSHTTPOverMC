package com.github.cocosoys.mc.soyshttpovermc.spring.entity;

import lombok.Data;

import java.io.Serializable;

/**
 * 隧道状态 - 请求计数实体（嵌套于 {@link StatusEntity}）。
 */
@Data
public class RequestCountEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long total;
    private Long get;
    private Long post;
    private Long other;
}
