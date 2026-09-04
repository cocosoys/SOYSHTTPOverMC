package com.github.cocosoys.mc.soyshttpovermc.permission.local;

import com.dlz.db.annotation.IdType;
import com.dlz.db.annotation.TableId;
import com.dlz.db.annotation.TableName;
import lombok.Data;

/**
 * 本地内置权限表 · 用户-组关联实体（ORM，落 {@code data/soys_perm_user_group.yml} 或 SQL 表 {@code soys_perm_user_group}）。
 *
 * <p>主键为合成键 {@code uuid|group}（用户输入，INPUT 类型；YAML 后端无自增、需显式主键）。
 * 唯一性由合成主键保证；查询按 {@link #uuid} 或 {@link #group} 条件筛选。
 * 用户侧一律挂 UUID（见 {@link com.github.cocosoys.mc.soyshttpovermc.util.UuidUtil}），改名不丢。</p>
 */
@TableName("soys_perm_user_group")
@Data
public class SoysPermUserGroup {

    /**
     * 合成主键 {@code uuid|group}（uuid 标准小写带横线；group 小写归一）。
     */
    @TableId(type = IdType.INPUT)
    private String id;

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
        this.id = uuid + "|" + group;
        this.uuid = uuid;
        this.group = group;
    }
}
