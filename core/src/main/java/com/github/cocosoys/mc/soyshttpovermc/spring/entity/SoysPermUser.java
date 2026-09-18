package com.github.cocosoys.mc.soyshttpovermc.spring.entity;

import com.dlz.db.annotation.IdType;
import com.dlz.db.annotation.TableId;
import com.dlz.db.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.github.cocosoys.mc.soyshttpovermc.permission.local.LocalPermissionStore;
import lombok.Data;

import java.util.Date;

/**
 * 本地内置权限表 · 用户实体（ORM，落 {@code data/soys_perm_user.yml} 或 SQL 表 {@code soys_perm_user}）。
 *
 * <p>用户整体过期字段 {@link #expiry}（yyyy-MM-dd HH:mm:ss；null=永久）。用户直接权限存于
 * {@link SoysPermPermission}（ownerType=USER、ownerId=uuid），用户归属组存于 {@link SoysPermUserGroup}。</p>
 *
 * <p>主键为玩家 UUID（离线服为确定性离线 UUID，见 {@link com.github.cocosoys.mc.soyshttpovermc.util.UuidUtil}）；
 * {@link #player} 仅作展示/反查属性，可随改名更新。所有用户操作经
 * {@link LocalPermissionStore#userKey(String)} 归一为 uuid 键。</p>
 */
@TableName("soys_perm_user")
@Data
public class SoysPermUser extends BaseEntity {

    /**
     * 玩家 UUID（主键，标准小写带横线；离线服为离线 UUID）。
     */
    @TableId(type = IdType.INPUT)
    private String uuid;

    /**
     * 玩家名（属性，仅展示/反查；可随改名更新）。
     */
    private String player;

    /**
     * 用户整体过期时刻（yyyy-MM-dd HH:mm:ss；null=永久）。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date expiry;

    public SoysPermUser() {
    }

    public SoysPermUser(String uuid) {
        this.uuid = uuid;
    }
}
