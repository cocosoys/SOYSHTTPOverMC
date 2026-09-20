package com.github.cocosoys.mc.soyshttpovermc.command;

import com.github.cocosoys.mc.soyshttpovermc.HttpOverMcPlugin;
import com.github.cocosoys.mc.soyshttpovermc.api.DataRegistrationApi;
import com.github.cocosoys.mc.soyshttpovermc.api.SoysHttpOverMcApi;
import com.github.cocosoys.mc.soyshttpovermc.enums.StorageType;
import com.github.cocosoys.mc.soyshttpovermc.i18n.I18n;
import com.github.cocosoys.mc.soyshttpovermc.orm.Backends;
import com.github.cocosoys.mc.soyshttpovermc.orm.DataSpec;
import com.github.cocosoys.mc.soyshttpovermc.orm.SoysSchemaMeta;
import com.github.cocosoys.mc.soyshttpovermc.orm.YAML;
import com.github.cocosoys.mc.soyshttpovermc.orm.executor.SecondarySqlBackend;
import com.github.cocosoys.mc.soyshttpovermc.spring.entity.SoysRecord;
import com.github.cocosoys.mc.soyshttpovermc.web.DataHandle;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * /soyshttp migrate &lt;yaml|sqlite|mysql&gt; &lt;yaml|sqlite|mysql&gt; —— 在 ORM 后端之间显式迁移
 * 全部已登记表数据（绕过 DATA 自动路由，直接读写指定后端）。
 *
 * <p><b>合并语义</b>：源全量读取 → 目标逐条 upsert（按主键覆盖，<b>不清空</b>目标）；SQL 目标端
 * 逐条 REPLACE INTO、同一事务失败回滚。与 {@link SyncSub}（覆盖语义：先清后写）相对。</p>
 *
 * <p>迁移范围 = SchemaRegistry 汇总的<b>全部已登记插件（含主插件）归属表</b> +
 * 主插件自身表（soys_schema_meta / soys_records 均参与）。异步执行。</p>
 */
public class MigrateSub extends SubCommand {

    public MigrateSub(HttpOverMcPlugin plugin) {
        super(plugin);
    }

    @Override
    public String name() {
        return "migrate";
    }

    @Override
    public boolean requireOp() {
        return true;
    }

    @Override
    public String usage() {
        return I18n.t("command.migrate.usage",
                "/soyshttp migrate <yaml|sqlite|mysql> <yaml|sqlite|mysql> —— 在 ORM 后端间迁移全部已登记表数据（合并语义）");
    }

    @Override
    public String detail() {
        return usage() + "\n"
                + I18n.t("command.migrate.detail",
                "  来源/目标: yaml（data/<表名>.yml）、sqlite、mysql（storage.backends 对应后端）\n"
                        + "  逐条按主键 upsert 写入目标，不清空目标（合并语义）；示例: /soyshttp migrate yaml sqlite");
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (args.length < 3) {
            msg(sender, "§e" + usage());
            return;
        }
        StorageType from = StorageType.fromId(args[1]);
        StorageType to = StorageType.fromId(args[2]);
        if (from == null) {
            msgT(sender, "command.migrate.unknown-backend", "§c未知来源后端（可选: yaml / sqlite / mysql）");
            return;
        }
        if (to == null) {
            msgT(sender, "command.migrate.unknown-backend", "§c未知目标后端（可选: yaml / sqlite / mysql）");
            return;
        }
        if (from == to) {
            msgT(sender, "command.migrate.same-backend", "§c来源与目标不能相同");
            return;
        }
        if (!Backends.isEnabled(from)) {
            msgT(sender, "command.migrate.source-not-enabled", "§c来源后端 {0} 未装配", from.getId());
            return;
        }
        if (!Backends.isEnabled(to)) {
            msgT(sender, "command.migrate.target-not-enabled", "§c目标后端 {0} 未装配", to.getId());
            return;
        }
        msgT(sender, "command.migrate.start", "§7开始迁移: {0} → {1}", from.getId(), to.getId());
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                int[] result = migrate(from, to);
                msgT(sender, "command.migrate.done",
                        "§a迁移完成: {0} → {1}，共 {2} 张表 {3} 条记录（失败 {4} 张）",
                        from.getId(), to.getId(), result[0], result[1], result[2]);
            } catch (Exception e) {
                msgT(sender, "command.migrate.fail", "§c迁移失败: {0}", e.getMessage());
            }
        });
    }

    /**
     * 聚合全部待迁移表实体类（与 SyncSub 同一清单口径）：
     * 主插件自身（mainDataSpec + soys_schema_meta + soys_records）+ 全部已登记附属插件归属表。
     */
    private List<Class<?>> collectTableClasses() {
        Set<Class<?>> out = new LinkedHashSet<>();
        DataSpec main = plugin.getMainDataSpec();
        if (main != null) {
            out.addAll(main.mergedTableClasses());
        }
        out.add(SoysSchemaMeta.class);   // soys_schema_meta 保持迁移
        out.add(SoysRecord.class);       // soys_records（RecordSyncStorage 直接建表，未登记 SchemaRegistry）
        SoysHttpOverMcApi api = plugin.getApi();
        if (api != null) {
            DataRegistrationApi reg = api.getDataRegistration();
            if (reg != null) {
                for (String name : reg.registeredNames()) {
                    DataHandle h = reg.handleOf(name);
                    if (h != null && h.getSpec() != null) {
                        out.addAll(h.getSpec().mergedTableClasses());
                    }
                }
            }
        }
        return new ArrayList<>(out);
    }

    private static String tableName(Class<?> c) {
        return com.github.cocosoys.mc.soyshttpovermc.orm.meta.PojoMeta.of(c).getTableName();
    }

    /**
     * 逐表执行合并迁移：源全量读取 → 目标逐条 upsert（不清空）。
     *
     * @return int[]{成功表数, 总写入条数, 失败表数}
     */
    private int[] migrate(StorageType from, StorageType to) {
        int tableOk = 0;
        int totalRows = 0;
        int failTables = 0;
        for (Class<?> c : collectTableClasses()) {
            try {
                List<?> beans = readAll(from, c);
                int written = writeMerge(to, c, beans);
                tableOk++;
                totalRows += written;
            } catch (Throwable t) {
                failTables++;
            }
        }
        return new int[]{tableOk, totalRows, failTables};
    }

    /** 源全量读取（YAML 走 YAML.Pojo；SQL 走独立 JDBC 直连，主/辅统一）。 */
    private List<?> readAll(StorageType from, Class<?> beanClass) {
        if (from == StorageType.YAML) {
            return YAML.Pojo.select(beanClass);
        }
        SecondarySqlBackend sec = SecondarySqlBackend.of(from);
        if (sec == null) {
            throw new IllegalStateException("辅助 SQL 后端未装配: " + from.getId());
        }
        return sec.selectAll(beanClass);
    }

    /** 目标合并写入（逐条 upsert，不清空；SQL 逐条 REPLACE 同一事务回滚）。 */
    private int writeMerge(StorageType to, Class<?> beanClass, List<?> beans) throws Exception {
        if (to == StorageType.YAML) {
            int n = 0;
            for (Object b : beans) {
                if (YAML.Pojo.insert(b) || YAML.Pojo.updateById(b)) {
                    n++;
                }
            }
            return n;
        }
        SecondarySqlBackend sec = SecondarySqlBackend.of(to);
        if (sec == null) {
            throw new IllegalStateException("辅助 SQL 后端未装配: " + to.getId());
        }
        return sec.mergeAll(beanClass, beans);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2 || args.length == 3) {
            List<String> out = new ArrayList<>();
            for (StorageType t : StorageType.values()) {
                out.add(t.getId());
            }
            return out;
        }
        return java.util.Collections.emptyList();
    }
}
