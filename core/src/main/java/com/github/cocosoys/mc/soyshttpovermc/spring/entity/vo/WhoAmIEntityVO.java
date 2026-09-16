package com.github.cocosoys.mc.soyshttpovermc.spring.entity.vo;

import com.github.cocosoys.mc.soyshttpovermc.spring.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 请求上下文演示实体（{@code GET /api/whoami} 返回体，替代临时 Map 组装）：
 * 由 {@code SystemServiceImpl.whoAmI(ctx)} 从 {@code ApiRequestContext} 组装，
 * 展示 SOYS 请求上下文注入能力（客户端 IP / 方法 / 路径 / 认证状态 / 玩家）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WhoAmIEntityVO extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 客户端 IP（本地回环可能为 null） */
    private String ip;

    /** HTTP 方法（GET/POST/...） */
    private String method;

    /** 实际挂载路径（含前缀，如 /api/whoami） */
    private String path;

    /** 是否已携带有效凭证通过认证 */
    private boolean authenticated;

    /** 凭证绑定的玩家名（未登录为 null） */
    private String player;

    /** 该玩家是否在线 */
    private boolean online;

    public WhoAmIEntityVO() {
    }
}
