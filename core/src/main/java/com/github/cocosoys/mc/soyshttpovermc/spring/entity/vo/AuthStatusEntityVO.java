package com.github.cocosoys.mc.soyshttpovermc.spring.entity.vo;

import com.github.cocosoys.mc.soyshttpovermc.spring.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 认证状态实体（{@code GET /api/auth/status} 与 {@code GET /api/auth/me} 共用返回体 data）：
 * 由 {@code AuthServiceImpl.checkStatus()/me()} 组装，字段按调用分支选择性填充（未涉及的保持默认值/null）。
 * <ul>
 *   <li>已登录分支：player / authenticated / online / mode（+ip）；</li>
 *   <li>未登录分支：authenticated=false（+ip / player / gameLoggedIn / ipMatched）；</li>
 *   <li>自动登录分支：追加 rememberAutoLogin / token / cookieName / ttlSeconds。</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AuthStatusEntityVO extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 玩家名（未登录分支可能为 null） */
    private String player;

    /** 是否已登录 */
    private boolean authenticated;

    /** 该玩家是否在线 */
    private boolean online;

    /** 登录模式：online / offline（未登录为 null） */
    private String mode;

    /** 客户端 IP */
    private String ip;

    /** 是否由"记住我"设备凭证自动登录 */
    private boolean rememberAutoLogin;

    /** 是否由游戏端 IP 匹配自动登录（旧 auto.login.ip 路径） */
    private boolean autoLogin;

    /** 自动登录下发的会话令牌（仅自动登录分支非空） */
    private String token;

    /** 会话 Cookie 名（仅自动登录分支非空） */
    private String cookieName;

    /** 会话有效期（秒，仅自动登录分支有意义） */
    private long ttlSeconds;

    /** 游戏端是否已登录（仅旧 IP 匹配分支填充） */
    private boolean gameLoggedIn;

    /** 网页 IP 与游戏端 IP 是否匹配（仅旧 IP 匹配分支填充） */
    private boolean ipMatched;

    /** 设备指纹双因子是否启用（auth.yml auto.login.fp.enable，true=本次判定经过了指纹校验） */
    private boolean fpEnabled;

    /** 指纹校验未通过（strict 模式：不一致/未携带 → 拒绝自动登录，前端引导重新登录/票据绑定） */
    private boolean fpMismatch;

    /** 需要立即补绑设备（存量迁移：无绑定记录时本次放行，前端应调 /api/auth/device/register 完成绑定） */
    private boolean fpBindRequired;

    public AuthStatusEntityVO() {
    }
}
