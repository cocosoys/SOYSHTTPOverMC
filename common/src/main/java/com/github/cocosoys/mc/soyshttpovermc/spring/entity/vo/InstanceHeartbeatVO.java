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
 * 实例心跳记录负载（soys_records.data 的 {@code INSTANCE} 类型 JSON 结构）。
 *
 * <p>由 {@link com.github.cocosoys.mc.soyshttpovermc.storage.RecordSyncStorage#heartbeat} 序列化写入；
 * 键名保持 {@code name / host / port / last_heartbeat}。
 * {@code lastHeartbeat} 输出 {@code yyyy-MM-dd HH:mm:ss}（原 KV 布局为毫秒时间戳）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InstanceHeartbeatVO extends BaseEntity {

    /**
     * 子服显示名（跨服拓扑展示用）。
     */
    @JsonProperty("name")
    private String name;

    /**
     * 子服地址（host，不含端口）。
     */
    @JsonProperty("host")
    private String host;

    /**
     * 子服端口。
     */
    @JsonProperty("port")
    private int port;

    /**
     * 最近一次心跳时刻（yyyy-MM-dd HH:mm:ss）。
     */
    @JsonProperty("last_heartbeat")
    @JsonFormat(pattern = BeanCodec.DATE_TIME_PATTERN)
    private Date lastHeartbeat;

    /**
     * 附加参数在纯序列化 VO 中不参与输出（避免 BaseEntity 惰性初始化带出空 {@code params:{}}）。
     */
    @Override
    public Map<String, Object> getParams() {
        return null;
    }
}
