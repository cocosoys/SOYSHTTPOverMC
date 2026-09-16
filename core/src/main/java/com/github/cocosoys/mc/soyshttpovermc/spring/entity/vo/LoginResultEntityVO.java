package com.github.cocosoys.mc.soyshttpovermc.spring.entity.vo;

import com.github.cocosoys.mc.soyshttpovermc.spring.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 弹窗登录成功实体（{@code POST /api/auth/login} 返回体 data，替代临时 Map 组装）：
 * 由 {@code AuthServiceImpl.login()} 组装；remember 系列字段仅在"记住我"勾选且启用时填充。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LoginResultEntityVO extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 登录玩家名（已 trim） */
    private String player;

    /** 会话令牌（HttpOnly Cookie 值） */
    private String token;

    /** 会话 Cookie 名（如 soys_token） */
    private String cookieName;

    /** 会话有效期（秒） */
    private long ttlSeconds;

    /** 登录模式：online / offline（离线专属 cookie） */
    private String mode;

    /** 恒为 true（登录成功） */
    private boolean authenticated;

    /** 是否签发了"记住我"长期设备凭证 */
    private boolean remember;

    /** 记住我 Cookie 名（仅 remember=true 时非空） */
    private String rememberCookieName;

    /** 记住我有效期（秒，仅 remember=true 时有意义） */
    private long rememberTtlSeconds;

    public LoginResultEntityVO() {
    }
}
