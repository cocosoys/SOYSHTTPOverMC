package com.github.cocosoys.mc.soyshttpovermc.spring.entity.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.cocosoys.mc.soyshttpovermc.orm.convertor.BeanCodec;
import com.github.cocosoys.mc.soyshttpovermc.spring.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;
import java.util.Map;

/**
 * 令牌签发审计记录负载（soys_records.data 的 {@code AUDIT} 类型 JSON 结构）。
 *
 * <p>由 {@link com.github.cocosoys.mc.soyshttpovermc.storage.RecordSyncStorage#recordIssued} 序列化写入；
 * 键名保持 {@code server_id / subject / mode / admin / jti / issued_at / expires_at}。
 * {@code admin} 输出布尔 {@code true/false}（原 KV 布局手写 {@code 1/0}）；
 * 时间字段输出 {@code yyyy-MM-dd HH:mm:ss}（原为毫秒时间戳）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuditRecordVO extends BaseEntity {

    /**
     * 签发来源服务器 ID（跨服拓扑标识）。
     */
    @JsonProperty("server_id")
    private String serverId;

    /**
     * 登录主体（玩家名 / 服主标识；匿名时可能为空）。
     */
    @JsonProperty("subject")
    private String subject;

    /**
     * 登录模式（如 online / offline / remember）。
     */
    @JsonProperty("mode")
    private String mode;

    /**
     * 是否服主最高权限签发（admin=true）。
     */
    @JsonProperty("admin")
    private boolean admin;

    /**
     * 令牌 JTI（全局唯一，黑名单联动键）。
     */
    @JsonProperty("jti")
    private String jti;

    /**
     * 签发时刻（yyyy-MM-dd HH:mm:ss）。
     */
    @JsonProperty("issued_at")
    @JsonFormat(pattern = BeanCodec.DATE_TIME_PATTERN)
    private Date issuedAt;

    /**
     * 过期时刻（yyyy-MM-dd HH:mm:ss）。
     */
    @JsonProperty("expires_at")
    @JsonFormat(pattern = BeanCodec.DATE_TIME_PATTERN)
    private Date expiresAt;

    /**
     * 附加参数在纯序列化 VO 中不参与输出（避免 BaseEntity 惰性初始化带出空 {@code params:{}}）。
     */
    @Override
    public Map<String, Object> getParams() {
        return null;
    }
}
