package com.github.cocosoys.mc.soyshttpovermc.spring.entity.vo;

import com.github.cocosoys.mc.soyshttpovermc.spring.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 登录模式探测实体（{@code GET /api/auth/mode} 返回体 data，替代临时 Map 组装）：
 * 由 {@code AuthServiceImpl.loginMode()} 组装，前端据此切换登录表单形态。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LoginModeEntityVO extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 是否要求密码（有登录插件=true；无登录插件=免密直登） */
    private boolean requiresPassword;

    /** 会话 Cookie 名 */
    private String cookieName;

    /** 会话有效期（秒） */
    private long ttlSeconds;

    /** 是否启用"记住我"设备免登录 */
    private boolean rememberEnabled;

    /** 记住我有效期（秒） */
    private long rememberTtlSeconds;

    public LoginModeEntityVO() {
    }
}
