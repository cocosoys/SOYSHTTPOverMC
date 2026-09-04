package com.github.cocosoys.mc.soyshttpovermc.permission.local;

import com.dlz.db.annotation.IdType;
import com.dlz.db.annotation.TableId;
import com.dlz.db.annotation.TableName;
import lombok.Data;

/**
 * 本地内置权限表 · 用户-组关联实体（ORM，落 {@code data/soys_perm_user_group.yml} 或 SQL 表 {@code soys_perm_user_group}）。
 *
 * <p>主键为合成键 {@code player|group}（用户输入，INPUT 类型；YAML 后端无自增、需显式主键）。
 * 唯一性由合成主键保证；查询按 {@link #player} 或 {@link #group} 条件筛选。</p>
 */
@TableName("soys_perm_user_group")
@Data
public class SoysPermUserGroup {

    /**
     * 合成主键 {@code player|group}（player/group 均小写归一）。
     */
    @TableId(type = IdType.INPUT)
    private String id;

    /**
     * 玩家名（小写）。
     */
    private String player;

    /**
     * 组标识（小写）。
     */
    private String group;

    public SoysPermUserGroup() {
    }

    public SoysPermUserGroup(String player, String group) {
        this.id = player + "|" + group;
        this.player = player;
        this.group = group;
    }
}
