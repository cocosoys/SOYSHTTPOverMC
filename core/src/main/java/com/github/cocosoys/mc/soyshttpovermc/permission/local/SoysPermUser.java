package com.github.cocosoys.mc.soyshttpovermc.permission.local;

import com.dlz.db.annotation.IdType;
import com.dlz.db.annotation.TableId;
import com.dlz.db.annotation.TableName;
import lombok.Data;

/**
 * 本地内置权限表 · 用户实体（ORM，落 {@code data/soys_perm_user.yml} 或 SQL 表 {@code soys_perm_user}）。
 *
 * <p>用户整体过期字段 {@link #expiry}（epoch 毫秒字符串；空=永久）。用户直接权限存于
 * {@link SoysPermPermission}（ownerType=USER、ownerId=uuid），用户归属组存于 {@link SoysPermUserGroup}。</p>
 *
 * <p>主键为玩家 UUID（离线服为确定性离线 UUID，见 {@link com.github.cocosoys.mc.soyshttpovermc.util.UuidUtil}）；
 * {@link #player} 仅作展示/反查属性，可随改名更新。所有用户操作经
 * {@link LocalPermissionStore#userKey(String)} 归一为 uuid 键。</p>
 */
@TableName("soys_perm_user")
@Data
public class SoysPermUser {

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
     * 用户整体过期时刻（epoch 毫秒字符串；空=永久）。
     */
    private String expiry;

    /**
     * 创建时刻（epoch 毫秒字符串）。
     */
    private String createdAt;

    /**
     * 最近更新时刻（epoch 毫秒字符串）。
     */
    private String updatedAt;

    public SoysPermUser() {
    }

    public SoysPermUser(String uuid) {
        this.uuid = uuid;
    }
}
