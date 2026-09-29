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
 * 令牌注销黑名单记录负载（soys_records.data 的 {@code BLACKLIST} 类型 JSON 结构）。
 *
 * <p>由 {@link com.github.cocosoys.mc.soyshttpovermc.storage.RecordSyncStorage#revokeToken} 序列化写入；
 * 键名保持 {@code server_id / revoked_at}（与既有存储兼容）。{@code revokedAt} 输出
 * {@code yyyy-MM-dd HH:mm:ss}（原 KV 布局为毫秒时间戳）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BlacklistRecordVO extends BaseEntity {

    /**
     * 注销来源服务器 ID（跨服拓扑标识，如 lobby / survival）。
     */
    @JsonProperty("server_id")
    private String serverId;

    /**
     * 注销时刻（yyyy-MM-dd HH:mm:ss；判定黑名单生效时间）。
     */
    @JsonProperty("revoked_at")
    @JsonFormat(pattern = BeanCodec.DATE_TIME_PATTERN)
    private Date revokedAt;

    /**
     * 附加参数在纯序列化 VO 中不参与输出（避免 BaseEntity 惰性初始化带出空 {@code params:{}}）。
     */
    @Override
    public Map<String, Object> getParams() {
        return null;
    }
}
