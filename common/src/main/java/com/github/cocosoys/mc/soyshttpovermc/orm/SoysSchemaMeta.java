package com.github.cocosoys.mc.soyshttpovermc.orm;

import com.dlz.db.annotation.IdType;
import com.dlz.db.annotation.TableId;
import com.dlz.db.annotation.TableName;
import lombok.Data;

/**
 * 自动运维元数据表（Schema Registry）：记录<b>每个表</b>的归属插件、schema 版本与已执行脚本，
 * 用于卸载 / 重装 / 更新时自动识别。
 *
 * <p>存储：SQL 表 {@code soys_schema_meta} 或 YAML 文件 {@code data/soys_schema_meta.yml}
 * （文件名 = 表名，与双端同构约定一致）。</p>
 *
 * <p><b>键模型</b>：dlz ORM 仅支持单主键，故以组合键
 * {@code id = plugin + ":" + tableName} 作为 {@link TableId}（逻辑双主键）——
 * {@code plugin} 与 {@code tableName} 仍保留独立字段便于按归属/按表查询。</p>
 *
 * <p><b>记录语义</b>：</p>
 * <ul>
 *   <li>{@code tableName = "*"} 行 = 插件级迁移记录（schemaVersion = 已应用最高迁移版本，
 *       executedScripts = 已执行脚本清单，仅 MySQL 方言执行迁移）；</li>
 *   <li>{@code tableName = 具体表名} 行 = 表归属记录（state 标记 INSTALLED；
 *       purge 时按它识别可清理的表，并做"他属校验"）。</li>
 * </ul>
 */
@TableName("soys_schema_meta")
@Data
public class SoysSchemaMeta {

    /**
     * 组合主键：{@code <plugin>:<tableName>}（dlz 单主键约束；逻辑双主键）。
     */
    @TableId(type = IdType.INPUT)
    private String id;

    /**
     * 归属插件（主插件名 / Expansion identifier）。
     */
    private String plugin;

    /**
     * 表名（{@code "*"} = 插件级迁移记录；否则为具体 ORM 表名 / YAML 文件名）。
     */
    private String tableName;

    /**
     * 当前 schema 版本（插件级行 = 已应用最高迁移版本；表级行冗余表版本）。
     */
    private int schemaVersion;

    /**
     * 状态：INSTALLED / UNINSTALLED（一期恒 INSTALLED；UNINSTALLED 预留）。
     */
    private String state;

    /**
     * 已执行脚本清单（JSON 数组字符串，如 {@code ["init.sql","V2__xxx.sql"]}；插件级行使用）。
     */
    private String executedScripts;

    /**
     * 创建时刻（epoch 毫秒字符串）。
     */
    private String createdAt;

    /**
     * 最近更新时刻（epoch 毫秒字符串）。
     */
    private String updatedAt;

    public SoysSchemaMeta() {
    }

    /**
     * 构造并生成组合主键。
     */
    public SoysSchemaMeta(String plugin, String tableName) {
        this.plugin = plugin;
        this.tableName = tableName;
        this.id = key(plugin, tableName);
    }

    /**
     * 组合主键生成（{@code plugin:tableName}）。
     */
    public static String key(String plugin, String tableName) {
        return plugin + ":" + tableName;
    }
}
