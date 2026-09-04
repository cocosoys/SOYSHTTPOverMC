package com.github.cocosoys.mc.soyshttpovermc.permission.local;

import com.dlz.db.annotation.IdType;
import com.dlz.db.annotation.TableId;
import com.dlz.db.annotation.TableName;
import lombok.Data;

/**
 * 本地内置权限表 · 权限组实体（ORM，落 {@code data/soys_perm_group.yml} 或 SQL 表 {@code soys_perm_group}）。
 *
 * <p>配套 {@code permission.offline-fallback: local} 本地权限规则：组只含权限（扁平，不支持组套组），
 * 组权限存于 {@link SoysPermPermission}（ownerType=GROUP），用户归属存于 {@link SoysPermUserGroup}。</p>
 *
 * <p>主键为组标识（小写，如 {@code default} / {@code vip} / {@code admin}），由调用方输入（INPUT）。</p>
 */
@TableName("soys_perm_group")
@Data
public class SoysPermGroup {

    /**
     * 组标识（主键，小写；如 default / vip / admin）。
     */
    @TableId(type = IdType.INPUT)
    private String id;

    /**
     * 显示名（可选，默认回退 id）。
     */
    private String display;

    /**
     * 聊天前缀（可选）。
     */
    private String prefix;

    /**
     * 权重（大者优先；组间权限为并集，仅展示/管理参考）。
     */
    private int weight;

    /**
     * 描述（可选）。
     */
    private String description;

    /**
     * 创建时刻（epoch 毫秒字符串）。
     */
    private String createdAt;

    /**
     * 最近更新时刻（epoch 毫秒字符串）。
     */
    private String updatedAt;

    public SoysPermGroup() {
    }

    public SoysPermGroup(String id) {
        this.id = id;
    }
}
