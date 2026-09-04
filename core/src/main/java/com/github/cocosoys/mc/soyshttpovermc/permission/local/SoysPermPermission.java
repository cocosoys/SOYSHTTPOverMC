package com.github.cocosoys.mc.soyshttpovermc.permission.local;

import com.dlz.db.annotation.IdType;
import com.dlz.db.annotation.TableId;
import com.dlz.db.annotation.TableName;
import lombok.Data;

/**
 * 本地内置权限表 · 统一权限实体（ORM，落 {@code data/soys_perm_permission.yml} 或 SQL 表 {@code soys_perm_permission}）。
 *
 * <p>组权限与用户直接权限共用本表，以 {@link #ownerType} 区分（GROUP / USER），{@link #ownerId} 为组名或玩家名。</p>
 *
 * <p>节点规范化约定：</p>
 * <ul>
 *   <li>写入时统一做 <b>{@code ':' → '.'} 归一</b>（如 {@code test:ping} 存为 {@code test.ping}，两者判定等价）；</li>
 *   <li>节点以 <b>{@code -} 开头表示否定</b>：写入时剥离前缀存 {@link #permission}，{@link #negative}=true；</li>
 *   <li>通配支持：全量 {@code *}、段级尾通配 {@code a.*}（匹配 {@code a.x} / {@code a.x.y}）。</li>
 * </ul>
 *
 * <p>主键为合成键 {@code ownerType|ownerId|permission}（用户输入，INPUT 类型）。</p>
 */
@TableName("soys_perm_permission")
@Data
public class SoysPermPermission {

    /** 主体类型：组。 */
    public static final String TYPE_GROUP = "GROUP";
    /** 主体类型：用户。 */
    public static final String TYPE_USER = "USER";

    /**
     * 合成主键 {@code ownerType|ownerId|permission}。
     */
    @TableId(type = IdType.INPUT)
    private String id;

    /**
     * 主体类型：{@link #TYPE_GROUP} / {@link #TYPE_USER}。
     */
    private String ownerType;

    /**
     * 主体标识：组名（GROUP）或玩家 UUID（USER，标准小写带横线；离线服为离线 UUID，见
     * {@link com.github.cocosoys.mc.soyshttpovermc.util.UuidUtil}）。
     */
    private String ownerId;

    /**
     * 权限节点（已归一：{@code ':'→'.'}，{@code -} 前缀已剥离）。
     */
    private String permission;

    /**
     * 是否否定（true=拒绝该节点；默认 false=授予）。
     */
    private boolean negative;

    /**
     * 创建时刻（epoch 毫秒字符串）。
     */
    private String createdAt;

    public SoysPermPermission() {
    }

    public SoysPermPermission(String ownerType, String ownerId, String permission, boolean negative) {
        this.id = ownerType + "|" + ownerId + "|" + permission;
        this.ownerType = ownerType;
        this.ownerId = ownerId;
        this.permission = permission;
        this.negative = negative;
        this.createdAt = String.valueOf(System.currentTimeMillis());
    }
}
