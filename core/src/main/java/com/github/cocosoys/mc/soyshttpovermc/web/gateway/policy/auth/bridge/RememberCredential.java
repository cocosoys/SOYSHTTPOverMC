package com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.bridge;

import com.dlz.db.annotation.TableId;
import com.dlz.db.annotation.TableName;
import lombok.Data;

/**
 * “记住我”（设备免登录）凭证登记（ORM 实体，落 {@code data/soys_remember.yml} 或 SQL 表）。
 *
 * <p>每次签发 remember JWT（{@code rm_} 前缀，见 {@link com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.issuer.SessionTokenIssuer#issueRememberToken}）
 * 时登记一条，主键 = 该 JWT 的 {@code jti}。用途：
 * <ul>
 *   <li><b>重启不丢</b>：JWT 无状态 + 实体登记，服务器重启后仍可识别设备凭证；</li>
 *   <li><b>服务端可撤销</b>：退出登录时按玩家枚举实体 → jti 进黑名单 + 删除实体；</li>
 *   <li><b>防越权</b>：验证时要求实体存在且未过期，仅凭篡改/伪造 JWT 无法通过。</li>
 * </ul>
 * 存储复用现有 ORM 门面（SQL.Pojo / YAML.Pojo，见 {@code storage.backends.*} 配置）。
 */
@TableName("soys_remember")
@Data
public class RememberCredential {

    /**
     * 对应 remember JWT 的 jti（唯一主键）。
     */
    @TableId
    private String jti;

    /**
     * 绑定玩家名。
     */
    private String player;

    /**
     * 签发时刻（epoch 毫秒字符串）。
     */
    private String issuedAt;

    /**
     * 过期时刻（epoch 毫秒字符串）。
     */
    private String expiresAt;

    public RememberCredential() {
    }

    public RememberCredential(String jti, String player, String issuedAt, String expiresAt) {
        this.jti = jti;
        this.player = player;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }
}
