package com.github.cocosoys.mc.soyshttpovermc.spring.entity;

import com.dlz.db.annotation.IdType;
import com.dlz.db.annotation.TableId;
import com.dlz.db.annotation.TableName;
import com.github.cocosoys.mc.soyshttpovermc.enums.SoysPermOwnerType;
import lombok.Data;

/**
 * 本地内置权限表 · 统一权限实体（ORM，落 {@code data/soys_perm_permission.yml} 或 SQL 表 {@code soys_perm_permission}）。
 *
 * <p>组 / 用户 / X-API-Key 权限共用本表，以 {@link #ownerType} 区分（GROUP / USER / APIKEY），
 *
 * <p>节点规范化约定：</p>
 * <ul>
 *   <li>写入时统一做 <b>{@code ':' → '.'} 归一</b>（如 {@code test:ping} 存为 {@code test.ping}，两者判定等价）；</li>
 *   <li>节点以 <b>{@code -} 开头表示否定</b>：写入时剥离前缀存 {@link #permission}，{@link #negative}=true；</li>
 *   <li>通配支持：全量 {@code *}、段级尾通配 {@code a.*}（匹配 {@code a.x} / {@code a.x.y}）。</li>
 * </ul>
 *
 * <p>主键为自增键（Long，{@code IdType.AUTO}；SQL 端 AUTO_INCREMENT / SQLite AUTOINCREMENT，
 * YAML 端由 ORM 分配 max+1）。业务键 {@code ownerType|ownerId|permission} 的唯一性由逻辑层查重保证。</p>
 *
 * <p>审计字段（createTime 等）继承自 {@link BaseEntity}，落库列 {@code create_time}
 * （yyyy-MM-dd HH:mm:ss）。</p>
 */
@TableName("soys_perm_permission")
@Data
public class SoysPermPermission extends BaseEntity {

    /**
     * 自增主键（Long；SQL 端 AUTO_INCREMENT / SQLite AUTOINCREMENT，YAML 端由 ORM 分配 max+1）。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 主体类型：{@link SoysPermOwnerType} 的落库代码（GROUP / USER / APIKEY）。
     */
    private String ownerType;

    /**
     * 主体标识：组名（GROUP）、玩家 UUID（USER）或 X-API-Key 主键 id（APIKEY，见 soys_api_key 实体）。
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


    public SoysPermPermission() {
    }

    public SoysPermPermission(String ownerType, String ownerId, String permission, boolean negative) {
        this.ownerType = ownerType;
        this.ownerId = ownerId;
        this.permission = permission;
        this.negative = negative;
        setCreateTime(new java.util.Date());
    }
}
