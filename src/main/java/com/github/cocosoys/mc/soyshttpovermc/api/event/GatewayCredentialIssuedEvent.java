package com.github.cocosoys.mc.soyshttpovermc.api.event;

import org.bukkit.event.HandlerList;

import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.issuer.CredentialIssuer;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.issuer.IssuedCredential;

/**
 * 凭证下发事件：/soyshttp key 命令或登录插件调用颁发器下发凭证后触发。
 * 其他插件可监听此事件做记录、通知客户端、与自家登录系统联动等。
 *
 * <p>注意：本事件为<b>同步事件</b>（在主线程触发，如命令/登录流程），
 * 不要在异步线程触发；若确需异步触发请自行扩展异步变体。
 */
public class GatewayCredentialIssuedEvent extends GatewayEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String subject;
    private final CredentialIssuer issuer;
    private final IssuedCredential credential;

    public GatewayCredentialIssuedEvent(String subject, CredentialIssuer issuer, IssuedCredential credential) {
        super(false); // 同步：命令/登录流程在主线程触发
        this.subject = subject == null ? "" : subject;
        this.issuer = issuer;
        this.credential = credential;
    }

    /** 凭证所属主体（玩家 UUID/用户名） */
    public String getSubject() {
        return subject;
    }

    public CredentialIssuer getIssuer() {
        return issuer;
    }

    public String getIssuerName() {
        return issuer == null ? "" : issuer.name();
    }

    /** 下发的凭证（X-API-Key / Bearer / Cookie 三种形态） */
    public IssuedCredential getCredential() {
        return credential;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
