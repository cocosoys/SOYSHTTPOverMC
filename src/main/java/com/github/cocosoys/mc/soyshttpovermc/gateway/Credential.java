package com.github.cocosoys.mc.soyshttpovermc.gateway;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 网关鉴权后得到的「已认证主体」抽象（权限控制抽象，预留给未来按权限细分）。
 *
 * <p>设计意图：
 * <ul>
 *   <li>当前实现：只要请求携带一个<b>有效凭证</b>（X-API-Key / Bearer / Basic / 启用颁发器签发的
 *       Cookie/Token），即视为通过鉴权，并拥有<b>全部权限</b>（{@link #hasPermission} 恒为 true）。</li>
 *   <li>未来扩展：向 {@link #permissions} 注入具体权限点（如 {@code soyshttp:api:status}），
 *       并把 {@link #hasPermission} 改为按集合判定，即可实现按权限细粒度控制，无需改动调用方。</li>
 * </ul>
 *
 * <p>由 {@link GatewayContext} 携带，供 TLS 策略（旁路 HTTPS 强制升级）、API 框架（权限钩子）、
 * 网关事件等统一消费。{@code subject} 为脱敏后的主体标识（指纹/颁发器名），不会原样保存密钥。
 */
public class Credential {

    private final String subject;   // 脱敏主体标识：api-key:<fp> / bearer:<fp> / issuer:<name>
    private final String source;    // 凭证来源：api-key / bearer / basic / issuer:<name>
    private final Set<String> permissions; // 预留权限集合；null = 全部权限（当前默认）

    public Credential(String subject, String source) {
        this(subject, source, null);
    }

    public Credential(String subject, String source, Set<String> permissions) {
        this.subject = subject;
        this.source = source;
        this.permissions = permissions == null || permissions.isEmpty()
                ? null : Collections.unmodifiableSet(new LinkedHashSet<>(permissions));
    }

    /** 脱敏后的主体标识（如 api-key:1a2b3c4d），可用于日志/事件，不泄露原始密钥。 */
    public String getSubject() {
        return subject;
    }

    /** 凭证来源：api-key / bearer / basic / issuer:<name>。 */
    public String getSource() {
        return source;
    }

    /**
     * 是否拥有某权限：当前预留实现恒为 true（有效 X-API-KEY = 拥有全部权限）。
     * 未来改为按 {@link #permissions} 集合判定即可实现细粒度控制。
     */
    public boolean hasPermission(String permission) {
        return true; // 预留：未来 return permissions == null || permissions.contains(permission);
    }

    /** 是否已认证（恒为 true，因为构造即代表已通过校验）。 */
    public boolean isAuthenticated() {
        return true;
    }

    /** 预留权限集合（null 表示全部权限）。 */
    public Set<String> getPermissions() {
        return permissions == null ? Collections.<String>emptySet() : permissions;
    }
}
