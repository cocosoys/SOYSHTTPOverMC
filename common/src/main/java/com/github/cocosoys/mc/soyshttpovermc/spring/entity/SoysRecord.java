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
 * 通用记录实体（ORM 表 {@code soys_records}，落 {@code data/soys_records.yml} 或 SQL 表）：
 * 令牌黑名单 / 签发审计 / 实例心跳 / 全局 JWT 密钥 四类 KV 记录统一以本实体持久化，
 * 经 {@code DATA} 门面路由 SQL（mysql &gt; sqlite）或 YAML 后端。
 *
 * <p>记录 key 约定（{@link com.github.cocosoys.mc.soyshttpovermc.storage.RecordSyncStorage}）：
 * <ul>
 *   <li>黑名单：{@code blacklist:&lt;jti&gt;}（type=BLACKLIST，data=JSON{server_id,revoked_at}）</li>
 *   <li>审计：{@code audit:&lt;jti&gt;:&lt;nonce&gt;}（type=AUDIT，append-only，nonce 保证唯一）</li>
 *   <li>心跳：{@code instance:&lt;serverId&gt;}（type=INSTANCE）</li>
 *   <li>密钥：{@code meta:jwt_secret}（type=META，data=base64）</li>
 * </ul>
 * 业务时间以 {@link #updatedAt} 为准；{@code createTime/updateTime} 为审计字段，由 ORM 写路径自动填充。
 */
@TableName("soys_records")
@Data
@EqualsAndHashCode(callSuper = true)
public class SoysRecord extends BaseEntity {

    /**
     * 主键：记录 key（{@code blacklist:&lt;jti&gt;} / {@code audit:&lt;jti&gt;:&lt;nonce&gt;} /
     * {@code instance:&lt;serverId&gt;} / {@code meta:jwt_secret}）。
     */
    @TableId(type = IdType.INPUT)
    private String key;

    /**
     * 记录类型（BLACKLIST / AUDIT / INSTANCE / META）。
     */
    private String type;

    /**
     * 负载（JSON 字符串，各类型结构见 {@link com.github.cocosoys.mc.soyshttpovermc.storage.RecordSyncStorage}）。
     */
    private String data;

    /**
     * 业务时间（yyyy-MM-dd HH:mm:ss；黑名单注销时刻 / 审计签发时刻 / 心跳时间 / 密钥更新时间）。
     */
    @JsonFormat(pattern = BeanCodec.DATE_TIME_PATTERN)
    private Date updatedAt;

    public SoysRecord() {
    }

    /**
     * 便捷构造（业务时间取当前时刻）。
     *
     * @param key  记录主键
     * @param type 记录类型
     * @param data 负载 JSON
     */
    public SoysRecord(String key, String type, String data) {
        this.key = key;
        this.type = type;
        this.data = data;
        this.updatedAt = new Date();
    }
}