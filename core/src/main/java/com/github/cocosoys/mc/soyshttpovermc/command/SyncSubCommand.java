package com.github.cocosoys.mc.soyshttpovermc.command;

import com.github.cocosoys.mc.soyshttpovermc.HttpOverMcPlugin;
import com.github.cocosoys.mc.soyshttpovermc.enums.StorageType;
import com.github.cocosoys.mc.soyshttpovermc.i18n.I18n;
import com.github.cocosoys.mc.soyshttpovermc.orm.Backends;
import com.github.cocosoys.mc.soyshttpovermc.orm.DATA;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

/**
 * /soyshttp sync —— 后端覆盖迁移（先清后写）。
 *
 * <ul>
 *   <li>{@code /soyshttp sync}（无参）：打印主 → 全部辅助预览，追加 {@code confirm} 执行（原版语义）；</li>
 *   <li>{@code /soyshttp sync confirm}：执行无参同步；</li>
 *   <li>{@code /soyshttp sync <from> <to>}：定向覆盖预览（需追加 {@code confirm} 才执行）；</li>
 *   <li>{@code /soyshttp sync <from> <to> confirm}：执行定向覆盖。</li>
 * </ul>
 *
 * <p>无参与定向均先<b>异步预览</b>目标表当前行数并要求 confirm；覆盖语义：源全量读取 →
 * 目标<b>先清空后写入</b>；SQL 目标端包在同一事务内、失败回滚，YAML 端内存构建后一次性原子落盘；
 * 写入后做<b>行数校验</b>（源 count vs 目标 count，不一致告警）。</p>
 *
 * <p>迁移范围 = {@link MigrateSubCommand.MigrationTables} 统一清单。所有后端读写统一走 {@link DATA} 门面
 * （主 SQL 复用主执行器、非主 SQL 独立直连、YAML 内存批量），消除旧版双连接池与双份清单。</p>
 */
public class SyncSubCommand extends SubCommand {

    public SyncSubCommand(HttpOverMcPlugin plugin) {
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
                "  无参：主存储 → 全部辅助存储全量覆盖（先清后写），确认执行追加 confirm\n"
                        + "  定向：sync <yaml|sqlite|mysql> <yaml|sqlite|mysql> [confirm]（先清后写，SQL 端同事务回滚）");
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        // 无参：主 → 全部辅助（预览 + confirm）
        if (args.length == 1) {
            StorageType primary = Backends.primary();
            List<StorageType> secondaries = Backends.secondaries();
            if (secondaries.isEmpty()) {
                msgT(sender, "command.sync.no-secondary",
                        "§c当前无辅助存储（已启用: {0}），无需同步", primary.getId());
                return;
            }
            msgT(sender, "command.sync.preview-all-header",
                    "§6无参同步: 主存储 {0} → 辅助存储 [{1}]（逐个覆盖，先清后写）",
                    primary.getId(), joinTypes(secondaries));
            msgT(sender, "command.sync.preview-counting", "§e正在统计目标表行数（异步）…");
            printPreview(sender, primary, secondaries);
            msgT(sender, "command.sync.need-confirm-all",
                    "§6以上为覆盖预览：将【先清空目标表后写入】，不可恢复！确认执行请追加 confirm: /soyshttp sync confirm");
            return;
        }
        // 执行无参同步
        if (args.length == 2 && "confirm".equalsIgnoreCase(args[1])) {
            StorageType primary = Backends.primary();
            List<StorageType> secondaries = Backends.secondaries();
            if (secondaries.isEmpty()) {
                msgT(sender, "command.sync.no-secondary",
                        "§c当前无辅助存储（已启用: {0}），无需同步", primary.getId());
                return;
            }
            msgT(sender, "command.sync.start-all",
                    "§e开始覆盖同步: 主存储 {0} → 辅助存储 [{1}]（异步执行，逐个覆盖）",
                    primary.getId(), joinTypes(secondaries));
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
            msgT(sender, "command.sync.preview-counting", "§e正在统计目标表行数（异步）…");
            printPreview(sender, from, java.util.Collections.singletonList(to));
            msgT(sender, "command.sync.need-confirm",
                    "§6以上为覆盖预览：将【先清空目标表后写入】，不可恢复！确认执行请追加 confirm: /soyshttp sync {0} {1} confirm",
                    from.getId(), to.getId());
            return;
        }
        msgT(sender, "command.sync.start-directed", "§e开始定向覆盖: {0} → {1}（异步执行）", from.getId(), to.getId());
        syncAll(sender, from, java.util.Collections.singletonList(to));
    }

    /**
     * 打印目标表当前行数 + 醒目警告（confirm 前的预览；异步执行，COUNT 不阻塞主线程）。
     */
    private void printPreview(CommandSender sender, StorageType from, List<StorageType> targets) {
        List<Class<?>> tables = MigrateSubCommand.MigrationTables.collect(plugin);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            msgT(sender, "command.sync.preview-header",
                    "§6===== 覆盖迁移预览: {0} → {1} =====", from.getId(), joinTypes(targets));
            for (StorageType to : targets) {
                for (Class<?> c : tables) {
                    long rows = DATA.count(to, c);
                    msgT(sender, "command.sync.preview-row",
                            "  目标表 {0}（{1}）: 当前 {2} 行", MigrateSubCommand.MigrationTables.tableName(c), to.getId(),
                            rows < 0 ? "无法读取" : Long.toString(rows));
                }
            }
            msgT(sender, "command.sync.preview-warn",
                    "§c⚠ 覆盖将【清空目标表后写入】（先清后写），SQL 端同一事务，失败回滚；此操作不可恢复！");
        });
    }

    /**
     * 逐目标覆盖执行：源全量读 → 目标建表（容忍式）→ 目标清空 + 全量写（DATA 门面统一路由）。
     * 写入后行数校验；整个执行持有 {@link MigrateSubCommand.MigrationTables} 互斥锁。
     */
    private void syncAll(CommandSender sender, StorageType from, List<StorageType> targets) {
        List<Class<?>> tables = MigrateSubCommand.MigrationTables.collect(plugin);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            if (!MigrateSubCommand.MigrationTables.tryLock()) {
                msgT(sender, "command.sync.busy", "§c已有其他迁移（sync/migrate）正在进行中，请稍后再试");
                return;
            }
            try {
                int tableOk = 0;
                int totalRows = 0;
                List<String> fails = new ArrayList<>();
                for (StorageType to : targets) {
                    for (Class<?> c : tables) {
                        try {
                            long sourceCount = DATA.count(from, c);
                            @SuppressWarnings("unchecked")
                            Class<Object> beanClass = (Class<Object>) c;
                            List<Object> beans = DATA.select(from, beanClass);
                            DATA.ensureTable(to, beanClass);
                            int written = DATA.upsertAll(to, beanClass, beans);
                            tableOk++;
                            totalRows += written;
                            long targetCount = DATA.count(to, c);
                            if (sourceCount >= 0 && targetCount >= 0 && sourceCount != targetCount) {
                                msgT(sender, "command.sync.table-count-mismatch",
                                        "§e表 {0}: 写入 {1} 条，但目标行数 {2} ≠ 源行数 {3}（可能重复键被合并）",
                                        MigrateSubCommand.MigrationTables.tableName(c), written, targetCount, sourceCount);
                            }
                            msgT(sender, "command.sync.table-done",
                                    "§a表 {0}: {1} → {2} 完成（{3} 条）",
                                    MigrateSubCommand.MigrationTables.tableName(c), from.getId(), to.getId(), written);
                        } catch (Throwable t) {
                            fails.add(MigrateSubCommand.MigrationTables.tableName(c) + "(" + t.getMessage() + ")");
                            msgT(sender, "command.sync.table-fail",
                                    "§c表 {0}: {1} → {2} 失败: {3}",
                                    MigrateSubCommand.MigrationTables.tableName(c), from.getId(), to.getId(), t.getMessage());
                        }
                    }
                }
                if (fails.isEmpty()) {
                    msgT(sender, "command.sync.done",
                            "§a覆盖同步完成: {0} → {1}，共 {2} 张表 {3} 条记录",
                            from.getId(), joinTypes(targets), tableOk, totalRows);
                } else {
                    msgT(sender, "command.sync.done-with-fail",
                            "§e覆盖同步完成（部分失败）: 成功 {0} 张表 {1} 条，失败 {2} 张: {3}",
                            tableOk, totalRows, fails.size(), String.join(", ", fails));
                }
            } finally {
                MigrateSubCommand.MigrationTables.unlock();
            }
        });
    }

    private static String joinTypes(List<StorageType> targets) {
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
        if (args.length == 1 && args[0].isEmpty()) {
            out.add("confirm");
        }
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
