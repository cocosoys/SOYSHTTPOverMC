package com.github.cocosoys.mc.soyshttpovermc.spring.entity;

import com.dlz.db.annotation.IdType;
import com.dlz.db.annotation.TableId;
import com.dlz.db.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.github.cocosoys.mc.soyshttpovermc.orm.convertor.BeanCodec;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * X-API-Key 本地存储实体（ORM，落 {@code data/soys_api_key.yml} 或 SQL 表 {@code soys_api_key}）。
 *
 * <p>替代原「apiKey 明文混入 {@link SoysPermUser}（以 key 派生伪 UUID 为主键）」的存储方式：
 * 密钥<b>不存明文</b>，仅存 SHA-256 全量哈希（{@link #apiKey}）与 8 位短指纹（{@link #fingerprint}，
 * 与 {@link com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.util.AuthUtils#fingerprint}
 * 同算法，用于日志/展示脱敏）。明文仅在生成时向管理员展示一次。</p>
 *
 * <p>认证门（AuthPolicy）与权限门（CombinedPermissionService）按请求头明文 → SHA-256 → 哈希查表；
 * 权限节点复用 {@link SoysPermPermission}（ownerType={@link SoysPermPermission#TYPE_APIKEY}、
 * ownerId=本表 {@link #id}），支持否定/通配/过期。</p>
 *
 * <p>{@link #uuid} 为<b>绑定玩家预留</b>（可空 = 未绑定）：未来接入后，携带该 key 的请求
 * 等同于对应玩家凭证（权限 = 该玩家权限链）。一期仅存储不启用绑定判定。</p>
 *
 * <p>审计字段（createTime 等）继承自 {@link BaseEntity}，落库列 {@code create_time}（yyyy-MM-dd HH:mm:ss）。</p>
 */
@TableName("soys_api_key")
@Data
@EqualsAndHashCode(callSuper = true)
public class SoysApiKey extends BaseEntity {

    /**
     * 主键：平台生成的 UUID（@TableId INPUT）。
     */
    @TableId(type = IdType.INPUT)
    private String id;

    /**
     * 密钥 SHA-256 全量哈希（hex，唯一；不存明文）。
     */
    private String apiKey;

    /**
     * 短指纹（SHA-256 前 8 位，唯一；用于日志/展示脱敏，不参与校验）。
     */
    private String fingerprint;

    /**
     * 绑定玩家 UUID（可空 = 未绑定；标准小写带横线，离线服为离线 UUID）。
     */
    private String uuid;

    /**
     * 绑定玩家名（冗余属性，随改名更新；可空）。
     */
    private String player;

    /**
     * 是否启用（false = 停用，认证门直接拒绝；默认 true）。
     */
    private boolean enabled;

    /**
     * 过期时刻（yyyy-MM-dd HH:mm:ss；null = 永久有效）。
     */
    @JsonFormat(pattern = BeanCodec.DATE_TIME_PATTERN)
    private Date expiry;

    /**
     * 最后使用时刻（yyyy-MM-dd HH:mm:ss；校验命中时更新）。
     */
    @JsonFormat(pattern = BeanCodec.DATE_TIME_PATTERN)
    private Date lastUsedAt;

    /**
     * 累计使用次数（校验命中时 +1）。
     */
    private long usedCount;

    /**
     * 备注（用途/归属说明等）。
     */
    private String remark;

    public SoysApiKey() {
    }

    /**
     * 构造并生成主键（新建时调用；enabled 默认 true）。
     *
     * @param id          平台生成 UUID
     * @param apiKeyHash  密钥 SHA-256 全量哈希
     * @param fingerprint 8 位短指纹
     */
    public SoysApiKey(String id, String apiKeyHash, String fingerprint) {
        this.id = id;
        this.apiKey = apiKeyHash;
        this.fingerprint = fingerprint;
        this.enabled = true;
    }
}
