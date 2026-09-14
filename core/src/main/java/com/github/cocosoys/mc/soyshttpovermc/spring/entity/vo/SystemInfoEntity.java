package com.github.cocosoys.mc.soyshttpovermc.spring.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 网关系统信息实体（{@link BaseEntity} 的参考实现，演示实体类用法）：
 * 由 {@code SystemApi.version()} 返回，经 JsonWriter 反射序列化为 JSON。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SystemInfoEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    private String name;
    private String version;
    private String protocol;
    private int port;

    public SystemInfoEntity() {
    }

    public SystemInfoEntity(String name, String version, String protocol, int port) {
        this.name = name;
        this.version = version;
        this.protocol = protocol;
        this.port = port;
    }
}
