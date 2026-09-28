package com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.bridge;

import com.dlz.db.annotation.IdType;
import com.dlz.db.annotation.TableId;
import com.dlz.db.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.github.cocosoys.mc.soyshttpovermc.orm.convertor.BeanCodec;
import com.github.cocosoys.mc.soyshttpovermc.spring.entity.BaseEntity;
import lombok.Data;

import java.util.Date;

/**
 * 设备绑定表（“记住我”Cookie + 设备指纹双因子）。
 *
 * <p>记录「玩家 ↔ 设备指纹哈希」的授权绑定：自动登录（记住我）时除校验
 * {@code soys_remember} 凭证有效性外，还校验请求携带的 {@code X-Device-Fingerprint}
 * 与绑定表一致（防 Cookie 被搬到其他设备）。指纹仅存
 * {@code SHA-256(player + "|" + 原始指纹 + 服务端盐)} 哈希，原始指纹不落库、不落日志
 * （指纹非秘密凭据，仅作一致性弱校验；浏览器升级/无痕模式导致指纹漂移时走
 * “重新登录 / 票据绑定”恢复通道，见 auto.login.fp.* 配置）。</p>
 *
 * <p>绑定通道：游戏内登录 → 短时效一次性票据（TTL 可配，默认 60s）→ 浏览器提交
 * {@code ticket + fingerprint} → {@code POST /api/auth/device/bind} 完成绑定并签发记住我 Cookie。</p>
 *
 * <p>审计字段（createTime/updateTime）继承自 {@link BaseEntity}；联合唯一键
 * {@code (player, fingerprint_hash)}：一个玩家可绑定多台设备、一台设备一个绑定。</p>
 */
@TableName("soys_device_binding")
@Data
public class SoysDeviceBinding extends BaseEntity {

    /**
     * 自增主键（Long；SQL 端 AUTO_INCREMENT / SQLite AUTOINCREMENT，YAML 端由 ORM 分配 max+1）。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 玩家名（联合唯一索引之一）。
     */
    private String player;

    /**
     * 玩家 UUID（可空，绑定/登录时回填；改名后以 uuid 为准）。
     */
    private String uuid;

    /**
     * 设备指纹哈希：SHA-256(玩家名 + "|" + 原始指纹 + 服务端盐)，仅存哈希。
     */
    private String fingerprintHash;

    /**
     * 设备描述（UA 摘要，仅展示用；可空）。
     */
    private String deviceLabel;

    /**
     * 最后成功绑定/登录 IP（弱校验参考，不进判定）。
     */
    private String lastIp;

    /**
     * 最后绑定时刻（yyyy-MM-dd HH:mm:ss）。
     */
    @JsonFormat(pattern = BeanCodec.DATE_TIME_PATTERN)
    private Date lastBindAt;

    /**
     * 弃用标记：1 = 设备已弃用（不参与判定；0/null = 有效）。
     */
    private Integer revoked;

    public SoysDeviceBinding() {
    }
}
