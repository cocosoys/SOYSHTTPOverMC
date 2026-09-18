package com.github.cocosoys.mc.soyshttpovermc.spring.entity.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 隧道状态 - 请求计数实体（嵌套于 {@link StatusEntityVO}）。
 */
@Data
public class RequestCountEntityVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 请求总数。 */
    private Long total;

    /** GET 请求数。 */
    private Long get;

    /** POST 请求数。 */
    private Long post;

    /** 其它方法请求数。 */
    private Long other;
}
