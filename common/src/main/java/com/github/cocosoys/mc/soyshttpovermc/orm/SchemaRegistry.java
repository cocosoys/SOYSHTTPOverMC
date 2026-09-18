package com.github.cocosoys.mc.soyshttpovermc.orm;

import com.github.cocosoys.mc.soyshttpovermc.orm.executor.SqlBackendExecutor;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 自动运维元数据服务（SchemaRegistry）：读写 {@link SoysSchemaMeta}（跨 SQL / YAML 双端）。
 *
 * <p>定位：卸载 / 重装 / 更新时的<b>自动识别依据</b>——</p>
 * <ul>
 *   <li>{@link #byPlugin(String)}：某插件归属的全部表（purge 识别）；</li>
 *   <li>{@link #byTable(String)}：某表的所有归属插件（purge 的"他属校验"）；</li>
 *   <li>{@link #getPlugin(String)}：插件级迁移记录（version + 已执行脚本）。</li>
 * </ul>
 *
 * <p><b>建表</b>：meta 表自身无需特殊初始化——任何经 {@code DATA} 的读写都会触发
 * SQL 端 {@code ensureTable(SoysSchemaMeta)} / YAML 端懒生成（鸡生蛋问题由 ORM 自动化解）。</p>
 */
public final class SchemaRegistry {

    /** 插件级记录的 tableName 约定。 */
    public static final String PLUGIN_ROW = "*";

    /** 状态：已安装。 */
    public static final String STATE_INSTALLED = "INSTALLED";

    /** 状态：已卸载（数据保留；预留）。 */
    public static final String STATE_UNINSTALLED = "UNINSTALLED";

    private SchemaRegistry() {
    }

    // ===== 查询 =====

    /** 单条记录（plugin + tableName）；不存在返回 null。 */
    public static SoysSchemaMeta get(String plugin, String tableName) {
        return DATA.get(SoysSchemaMeta.class, SoysSchemaMeta.key(plugin, tableName));
    }

    /** 插件级迁移记录（tableName="*"）；未安装返回 null。 */
    public static SoysSchemaMeta getPlugin(String plugin) {
        return get(plugin, PLUGIN_ROW);
    }

    /** 某插件归属的全部记录（含插件级 "*" 行）。 */
    public static List<SoysSchemaMeta> byPlugin(String plugin) {
        return DATA.select(SoysSchemaMeta.class, q -> q.eq(SoysSchemaMeta::getPlugin, plugin));
    }

    /** 某插件归属的表名集合（不含插件级 "*" 行）。 */
    public static Set<String> tablesOf(String plugin) {
        Set<String> out = new HashSet<>();
        for (SoysSchemaMeta m : byPlugin(plugin)) {
            if (!PLUGIN_ROW.equals(m.getTableName())) {
                out.add(m.getTableName());
            }
        }
        return out;
    }

    /** 某表的所有归属插件（不含自身）；用于 purge 他属校验。 */
    public static List<String> ownersOf(String tableName) {
        List<String> owners = new ArrayList<>();
        for (SoysSchemaMeta m : DATA.select(SoysSchemaMeta.class, q -> q.eq(SoysSchemaMeta::getTableName, tableName))) {
            owners.add(m.getPlugin());
        }
        return owners;
    }

    /** 是否已安装（存在插件级记录即视为已初始化）。 */
    public static boolean isInstalled(String plugin) {
        return getPlugin(plugin) != null;
    }

    // ===== 写入 =====

    /**
     * 记录 / 更新插件级迁移行（tableName="*"）。
     *
     * @param plugin  归属插件
     * @param version 当前已应用最高迁移版本
     * @param scripts 已执行脚本清单（可空）
     */
    public static boolean recordPlugin(String plugin, int version, List<String> scripts) {
        SoysSchemaMeta row = getPlugin(plugin);
        if (row == null) {
            row = new SoysSchemaMeta(plugin, PLUGIN_ROW);
            row.setState(STATE_INSTALLED);
            row.setCreatedAt(new Date());
        }
        row.setSchemaVersion(version);
        row.setExecutedScripts(toJson(scripts));
        row.setUpdatedAt(new Date());
        return DATA.insert(row) || upsert(row);
    }

    /**
     * 记录表归属行（tableName = ORM 表名）。
     */
    public static boolean recordTable(String plugin, String tableName) {
        SoysSchemaMeta row = get(plugin, tableName);
        if (row == null) {
            row = new SoysSchemaMeta(plugin, tableName);
            row.setState(STATE_INSTALLED);
            row.setSchemaVersion(0);
            row.setCreatedAt(new Date());
        }
        row.setUpdatedAt(new Date());
        return DATA.insert(row) || upsert(row);
    }

    /** 删除单条记录。 */
    public static boolean remove(String plugin, String tableName) {
        return DATA.deleteById(SoysSchemaMeta.class, SoysSchemaMeta.key(plugin, tableName));
    }

    /** 删除某插件全部记录（含插件级行）。 */
    public static boolean removePlugin(String plugin) {
        boolean ok = true;
        for (SoysSchemaMeta m : byPlugin(plugin)) {
            ok &= DATA.deleteById(SoysSchemaMeta.class, m.getId());
        }
        return ok;
    }

    // ===== 内部 =====

    /**
     * 解析插件级记录的已执行脚本 JSON 数组为 List；null/空 → 空列表。
     */
    public static List<String> parseScripts(SoysSchemaMeta row) {
        List<String> out = new ArrayList<>();
        if (row == null || row.getExecutedScripts() == null) {
            return out;
        }
        String raw = row.getExecutedScripts().trim();
        if (raw.isEmpty() || "[]".equals(raw)) {
            return out;
        }
        // 极简 JSON 字符串数组解析（脚本名不含引号/逗号转义，够用）
        int i = raw.indexOf('[');
        int j = raw.lastIndexOf(']');
        if (i < 0 || j <= i) {
            return out;
        }
        for (String part : raw.substring(i + 1, j).split(",")) {
            String s = part.trim();
            if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
                s = s.substring(1, s.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
            }
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    private static boolean upsert(SoysSchemaMeta row) {
        // 主键已存在时 insert 返回 false → 改走 updateById（跨端对称）
        return DATA.updateById(row);
    }

    private static String toJson(List<String> scripts) {
        if (scripts == null || scripts.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < scripts.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(String.valueOf(scripts.get(i))
                    .replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
        return sb.append(']').toString();
    }

    /** 供 SQL 端 purge 执行 DROP（内部使用）。 */
    static SqlBackendExecutor sql() {
        return SqlBackendExecutor.get();
    }
}
