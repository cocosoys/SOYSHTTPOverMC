package com.github.cocosoys.mc.soyshttpovermc.spring.entity;

import com.dlz.db.annotation.IdType;
import com.dlz.db.annotation.TableId;
import com.dlz.db.annotation.TableName;
import lombok.Data;

/**
 * 本地内置权限表 · 用户-组关联实体（ORM，落 {@code data/soys_perm_user_group.yml} 或 SQL 表 {@code soys_perm_user_group}）。
 *
 * <p>主键为自增键（Long，{@code IdType.AUTO}；SQL 端 AUTO_INCREMENT / SQLite AUTOINCREMENT，
 * YAML 端由 ORM 分配 max+1）。业务键 {@code uuid|group} 的唯一性由逻辑层查重保证；
 * 查询按 {@link #uuid} 或 {@link #group} 条件筛选。
 * 用户侧一律挂 UUID（见 {@link com.github.cocosoys.mc.soyshttpovermc.util.UuidUtil}），改名不丢。</p>
 */
@TableName("soys_perm_user_group")
@Data
public class SoysPermUserGroup extends BaseEntity {

    /**
     * 自增主键（Long；SQL 端 AUTO_INCREMENT / SQLite AUTOINCREMENT，YAML 端由 ORM 分配 max+1）。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 玩家 UUID（标准小写带横线；离线服为离线 UUID）。
     */
    private String uuid;

    /**
     * 组标识（小写）。
     */
    private String group;

    public SoysPermUserGroup() {
    }

    public SoysPermUserGroup(String uuid, String group) {
        this.uuid = uuid;
        this.group = group;
    }
}
