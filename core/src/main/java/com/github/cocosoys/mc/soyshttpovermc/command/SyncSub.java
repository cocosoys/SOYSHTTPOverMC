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
import com.github.cocosoys.mc.soyshttpovermc.orm.executor.YamlBackendExecutor;
import com.github.cocosoys.mc.soyshttpovermc.spring.entity.SoysRecord;
import com.github.cocosoys.mc.soyshttpovermc.web.DataHandle;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * /soyshttp sync —— 后端覆盖迁移（先清后写）。
 *
 * <ul>
 *   <li>{@code /soyshttp sync}（无参）：主存储 → 全部辅助存储，逐辅助全量覆盖（原版语义）；</li>
 *   <li>{@code /soyshttp sync <from> <to>}：定向覆盖（需追加 {@code confirm} 才执行）；</li>
 *   <li>{@code /soyshttp sync <from> <to> confirm}：执行定向覆盖。</li>
 * </ul>
 *
 * <p>覆盖语义：源全量读取 → 目标<b>先清空后写入</b>；SQL 目标端包在同一事务内，中途失败回滚；
 * 执行前打印目标表当前行数 + 醒目警告，并要求二次确认。</p>
 *
 * <p>迁移范围 = SchemaRegistry 汇总的<b>全部已登记插件（含主插件）归属表</b> +
 * 主插件自身表（soys_schema_meta / soys_records 均参与）。</p>
 */
public class SyncSub extends SubCommand {

    public SyncSub(HttpOverMcPlugin plugin) {
        super(plugin);
    }

    @Override
    public String name() {
        return "sync";
    }

    @Override
    public boolean requireOp() {
        return true;
    }

    @Override
    public String usage() {
        return I18n.t("command.sync.usage",
                "/soyshttp sync [<yaml|sqlite|mysql> <yaml|sqlite|mysql> [confirm]] —— 后端覆盖迁移（主→全部辅助 / 定向）");
    }

    @Override
    public String detail() {
        return usage() + "\n"
                + I18n.t("command.sync.detail",
                "  无参：主存储 → 全部辅助存储全量覆盖（先清后写）\n"
                        + "  定向：sync <yaml|sqlite|mysql> <yaml|sqlite|mysql> [confirm]（先清后写，SQL 端同事务回滚）");
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        // 无参：主 → 全部辅助
        if (args.length == 1) {
            StorageType primary = Backends.primary();
            List<StorageType> secondaries = Backends.secondaries();
            if (secondaries.isEmpty()) {
                msgT(sender, "command.sync.no-secondary",
                        "§c当前无辅助存储（已启用: {0}），无需同步", primary.getId());
                return;
            }
            StringBuilder sb = new StringBuilder();
            for (StorageType t : secondaries) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(t.getId());
            }
            msgT(sender, "command.sync.start-all",
                    "§e开始覆盖同步: 主存储 {0} → 辅助存储 [{1}]（异步执行，逐个覆盖）", primary.getId(), sb);
            syncAll(sender, primary, secondaries);
            return;
        }
        if (args.length < 3) {
            msg(sender, "§e" + usage());
            return;
        }
        StorageType from = StorageType.fromId(args[1]);
        StorageType to = StorageType.fromId(args[2]);
        if (from == null) {
            msgT(sender, "command.sync.unknown-backend", "§c未知来源后端（可选: yaml / sqlite / mysql）");
            return;
        }
        if (to == null) {
            msgT(sender, "command.sync.unknown-backend", "§c未知目标后端（可选: yaml / sqlite / mysql）");
            return;
        }
        if (from == to) {
            msgT(sender, "command.sync.same-backend", "§c来源与目标不能相同");
            return;
        }
        if (!Backends.isEnabled(from)) {
            msgT(sender, "command.sync.source-not-enabled", "§c来源后端 {0} 未启用（storage.backends.{0}.enabled=false）", from.getId());
            return;
        }
        if (!Backends.isEnabled(to)) {
            msgT(sender, "command.sync.target-not-enabled", "§c目标后端 {0} 未启用（storage.backends.{0}.enabled=false）", to.getId());
            return;
        }
        boolean confirm = args.length >= 4 && "confirm".equalsIgnoreCase(args[3]);
        if (!confirm) {
            printPreview(sender, from, to);
            msgT(sender, "command.sync.need-confirm",
                    "§6以上为覆盖预览：将【先清空目标表后写入】，不可恢复！确认执行请追加 confirm: /soyshttp sync {0} {1} confirm",
                    from.getId(), to.getId());
            return;
        }
        msgT(sender, "command.sync.start-directed", "§e开始定向覆盖: {0} → {1}（异步执行）", from.getId(), to.getId());
        syncAll(sender, from, java.util.Collections.singletonList(to));
    }

    /**
     * 打印目标表当前行数 + 醒目警告（confirm 前的预览）。
     */
    private void printPreview(CommandSender sender, StorageType from, StorageType to) {
        msgT(sender, "command.sync.preview-header", "§6===== 覆盖迁移预览: {0} → {1} =====", from.getId(), to.getId());
        for (Class<?> c : collectTableClasses()) {
            long rows = countTarget(to, c);
            msgT(sender, "command.sync.preview-row",
                    "  目标表 {0}（{1}）: 当前 {2} 行", tableName(c), to.getId(),
                    rows < 0 ? "无法读取" : Long.toString(rows));
        }
        msgT(sender, "command.sync.preview-warn",
                "§c⚠ 覆盖将【清空目标表后写入】（先清后写），SQL 端同一事务，失败回滚；此操作不可恢复！");
    }

    /**
     * 聚合全部待迁移表实体类：主插件自身（mainDataSpec + soys_schema_meta + soys_records）
     * + 全部已登记附属插件归属表（去重，保持声明顺序）。
     */
    private List<Class<?>> collectTableClasses() {
        Set<Class<?>> out = new LinkedHashSet<>();
        // 主插件自身表
        DataSpec main = plugin.getMainDataSpec();
        if (main != null) {
            out.addAll(main.mergedTableClasses());
        }
        out.add(SoysSchemaMeta.class);   // soys_schema_meta 保持迁移
        out.add(SoysRecord.class);       // soys_records（RecordSyncStorage 直接建表，未登记 SchemaRegistry）
        // 附属插件
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

    /** 目标表当前行数（YAML 全量读计数；SQL 走独立 JDBC COUNT）。 */
    private long countTarget(StorageType to, Class<?> beanClass) {
        try {
            if (to == StorageType.YAML) {
                return YAML.Pojo.select(beanClass).size();
            }
            SecondarySqlBackend sec = SecondarySqlBackend.of(to);
            return sec == null ? -1 : sec.count(beanClass);
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * 逐目标覆盖执行：源全量读 → 目标清空 → 目标全量写。
     * SQL 目标端经 SecondarySqlBackend 同一事务（清空 + REPLACE INTO 失败回滚）。
     */
    private void syncAll(CommandSender sender, StorageType from, List<StorageType> targets) {
        List<Class<?>> tables = collectTableClasses();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            int tableOk = 0;
            int totalRows = 0;
            List<String> fails = new ArrayList<>();
            for (StorageType to : targets) {
                for (Class<?> c : tables) {
                    try {
                        List<?> beans = readAll(from, c);
                        int written = writeAll(to, c, beans);
                        tableOk++;
                        totalRows += written;
                        msgT(sender, "command.sync.table-done",
                                "§a表 {0}: {1} → {2} 完成（{3} 条）", tableName(c), from.getId(), to.getId(), written);
                    } catch (Throwable t) {
                        fails.add(tableName(c) + "(" + t.getMessage() + ")");
                        msgT(sender, "command.sync.table-fail",
                                "§c表 {0}: {1} → {2} 失败: {3}", tableName(c), from.getId(), to.getId(), t.getMessage());
                    }
                }
            }
            if (fails.isEmpty()) {
                msgT(sender, "command.sync.done",
                        "§a覆盖同步完成: {0} → {1}，共 {2} 张表 {3} 条记录", from.getId(), joinTargets(targets), tableOk, totalRows);
            } else {
                msgT(sender, "command.sync.done-with-fail",
                        "§e覆盖同步完成（部分失败）: 成功 {0} 张表 {1} 条，失败 {2} 张: {3}",
                        tableOk, totalRows, fails.size(), String.join(", ", fails));
            }
        });
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

    /** 目标覆盖写入（先清后写；SQL 同一事务）。 */
    private int writeAll(StorageType to, Class<?> beanClass, List<?> beans) throws Exception {
        if (to == StorageType.YAML) {
            YamlBackendExecutor yaml = YamlBackendExecutor.get(YAML.Pojo.getDataDir());
            yaml.clear(beanClass);
            int n = 0;
            for (Object b : beans) {
                if (YAML.Pojo.insert(b)) {
                    n++;
                }
            }
            return n;
        }
        SecondarySqlBackend sec = SecondarySqlBackend.of(to);
        if (sec == null) {
            throw new IllegalStateException("辅助 SQL 后端未装配: " + to.getId());
        }
        return sec.upsertAll(beanClass, beans);
    }

    private static String joinTargets(List<StorageType> targets) {
        StringBuilder sb = new StringBuilder();
        for (StorageType t : targets) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(t.getId());
        }
        return sb.toString();
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 2 || args.length == 3) {
            for (StorageType t : StorageType.values()) {
                out.add(t.getId());
            }
        } else if (args.length == 4 && args[3].isEmpty()) {
            out.add("confirm");
        }
        return out;
    }
}
