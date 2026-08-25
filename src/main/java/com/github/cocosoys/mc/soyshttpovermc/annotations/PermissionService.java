package com.github.cocosoys.mc.soyshttpovermc.annotations;

import com.github.cocosoys.mc.soyshttpovermc.gateway.policy.auth.issuer.CredentialPresentation;

/**
 * 权限判定服务接口（仿 Spring Security 的 PermissionEvaluator）：
 * 由接入方（如登录插件）实现，把请求携带的凭证映射为"主体"（玩家/账号），
 * 再判定该主体是否拥有 {@link ApiPermission} 声明的权限。
 *
 * <pre>
 *   apiRegistry.setPermissionService((credential, permission) -&gt; {
 *       String subject = someIssuer.subjectOf(credential);   // 凭证 → 主体
 *       return permissionTable.contains(subject, permission);
 *   });
 * </pre>
 */
public interface PermissionService {

    /**
     * 判定凭证是否拥有指定权限。
     *
     * @param credential 请求携带的凭证（可能为空）
     * @param permission 权限标识（@ApiPermission 的 value）
     * @return true=放行；false=拒绝（返回 403）
     */
    boolean hasPermission(CredentialPresentation credential, String permission);
}
