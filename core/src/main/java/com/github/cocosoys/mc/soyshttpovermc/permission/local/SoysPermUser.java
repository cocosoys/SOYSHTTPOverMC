package com.github.cocosoys.mc.soyshttpovermc.permission.local;

import com.dlz.db.annotation.IdType;
import com.dlz.db.annotation.TableId;
import com.dlz.db.annotation.TableName;
import lombok.Data;

/**
 * 本地内置权限表 · 用户实体（ORM，落 {@code data/soys_perm_user.yml} 或 SQL 表 {@code soys_perm_user}）。
 *
 * <p>用户整体过期字段 {@link #expiry}（epoch 毫秒字符串；空=永久）。用户直接权限存于
 * {@link SoysPermPermission}（ownerType=USER），用户归属组存于 {@link SoysPermUserGroup}。</p>
 *
 * <p>主键为玩家名（统一小写归一）。</p>
 */
@TableName("soys_perm_user")
@Data
public class SoysPermUser {

    /**
     * 玩家名（主键，统一小写）。
     */
    @TableId(type = IdType.INPUT)
    private String player;

    /**
     * 玩家 UUID（可选，便于跨服/换名）。
     */
    private String uuid;

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

    public SoysPermUser(String player) {
        this.player = player;
    }
}
