package soys.soyshttpovermc.api;

import soys.soyshttpovermc.gateway.policy.auth.issuer.CredentialIssuer;
import soys.soyshttpovermc.gateway.policy.auth.issuer.IssuedCredential;

import java.util.List;
import java.util.function.Supplier;

/**
 * 能力组 3：鉴权与凭证（委托 {@link GatewayFilter} / {@link CredentialIssuer}）。
 * 由 {@link SoysHttpOverMcApi#getAuthCredential()} 跳转获取。
 */
public interface AuthCredentialApi {

    /** 注册一个凭证颁发器工厂（登录插件接入点；注册名对应 gateway/issuers/&lt;name&gt;.yml） */
    void registerCredentialIssuer(String name, Supplier<CredentialIssuer> factory);

    /** 是否有启用的 auth 策略（决定 API 是否需鉴权） */
    boolean isAuthEnabled();

    /** 列出所有启用的颁发器名 */
    List<String> getIssuerNames();

    /** 用首个启用的颁发器为已认证主体签发凭证；无启用颁发器返回 null */
    IssuedCredential issueCredential(String subject);

    /** 用指定颁发器签发；issuerName 为 null 时取首个启用的 */
    IssuedCredential issueCredential(String issuerName, String subject);

    /**
     * 用首个启用的颁发器签发携带自定义 claims 的凭证（会话令牌颁发器将 claims 写入 JWT payload）。
     * 键限 [a-zA-Z0-9_-]{1,32}，值限 256 字符；保留键 sub/mode/exp/iat/jti/adm 不可用。
     * 非会话令牌颁发器忽略 claims。用于权限范围/标签等业务声明（不参与权限判定）。
     */
    IssuedCredential issueCredential(String subject, java.util.Map<String, String> claims);

    /** 用指定颁发器签发携带自定义 claims 的凭证；issuerName 为 null 时取首个启用的 */
    IssuedCredential issueCredential(String issuerName, String subject, java.util.Map<String, String> claims);
}
