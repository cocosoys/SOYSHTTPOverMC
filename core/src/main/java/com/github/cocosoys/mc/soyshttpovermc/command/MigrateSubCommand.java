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
 * /soyshttp migrate &lt;yaml|sqlite|mysql&gt; &lt;yaml|sqlite|mysql&gt; —— 在 ORM 后端之间显式迁移
 * 全部已登记表数据（绕过 DATA 自动路由，直接读写指定后端）。
 *
 * <p><b>合并语义</b>：源全量读取 → 目标逐条 upsert（按主键覆盖，<b>不清空</b>目标）；SQL 目标端
 * 同一事务失败回滚，YAML 端内存构建后一次性原子落盘。与 {@link SyncSub}（覆盖语义：先清后写）相对。</p>
 *
 * <p>执行前先<b>异步预览</b>并追加 {@code confirm} 才执行；逐表失败明细（表名 + 原因）；
 * 写入后<b>行数校验</b>；整个执行持有 {@link MigrationTables} 互斥锁。</p>
 *
 * <p>迁移范围 = {@link MigrationTables} 统一清单；所有后端读写统一走 {@link DATA} 门面。</p>
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
                "/soyshttp migrate <yaml|sqlite|mysql> <yaml|sqlite|mysql> [confirm] —— 在 ORM 后端间迁移全部已登记表数据（合并语义）");
    }

    @Override
    public String detail() {
        return usage() + "\n"
                + I18n.t("command.migrate.detail",
                "  来源/目标: yaml（data/<表名>.yml）、sqlite、mysql（storage.backends 对应后端）\n"
                        + "  逐条按主键 upsert 写入目标，不清空目标（合并语义）；确认执行追加 confirm");
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
        boolean confirm = args.length >= 4 && "confirm".equalsIgnoreCase(args[3]);
        if (!confirm) {
            msgT(sender, "command.migrate.preview-counting", "§e正在统计目标表行数（异步）…");
            printPreview(sender, from, to);
            msgT(sender, "command.migrate.need-confirm",
                    "§6以上为合并迁移预览：不清空目标表（按主键 upsert 覆盖），确认执行请追加 confirm: /soyshttp migrate {0} {1} confirm",
                    from.getId(), to.getId());
            return;
        }
        msgT(sender, "command.migrate.start", "§7开始迁移: {0} → {1}", from.getId(), to.getId());
        migrateAsync(sender, from, to);
    }

    /**
     * 打印目标表当前行数 + 说明（confirm 前的预览；异步执行，COUNT 不阻塞主线程）。
     */
    private void printPreview(CommandSender sender, StorageType from, StorageType to) {
        List<Class<?>> tables = MigrationTables.collect(plugin);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            msgT(sender, "command.migrate.preview-header",
                    "§6===== 合并迁移预览: {0} → {1} =====", from.getId(), to.getId());
            for (Class<?> c : tables) {
                long rows = DATA.count(to, c);
                msgT(sender, "command.migrate.preview-row",
                        "  目标表 {0}（{1}）: 当前 {2} 行", MigrationTables.tableName(c), to.getId(),
                        rows < 0 ? "无法读取" : Long.toString(rows));
            }
            msgT(sender, "command.migrate.preview-note",
                    "§7合并语义：按主键 upsert 覆盖，不清空目标；SQL 端同一事务，失败回滚。");
        });
    }

    /**
     * 逐表执行合并迁移（异步 + 互斥锁）：源全量读 → 目标建表（容忍式）→ 目标逐条 upsert；
     * 逐表失败明细 + 行数校验。
     */
    private void migrateAsync(CommandSender sender, StorageType from, StorageType to) {
        List<Class<?>> tables = MigrationTables.collect(plugin);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            if (!MigrationTables.tryLock()) {
                msgT(sender, "command.migrate.busy", "§c已有其他迁移（sync/migrate）正在进行中，请稍后再试");
                return;
            }
            try {
                int tableOk = 0;
                int totalRows = 0;
                List<String> fails = new ArrayList<>();
                for (Class<?> c : tables) {
                    try {
                        long sourceCount = DATA.count(from, c);
                        @SuppressWarnings("unchecked")
                        Class<Object> beanClass = (Class<Object>) c;
                        List<Object> beans = DATA.select(from, beanClass);
                        DATA.ensureTable(to, beanClass);
                        int written = DATA.mergeAll(to, beanClass, beans);
                        tableOk++;
                        totalRows += written;
                        long targetCount = DATA.count(to, c);
                        if (sourceCount >= 0 && targetCount >= 0 && sourceCount != targetCount) {
                            msgT(sender, "command.migrate.table-count-mismatch",
                                    "§e表 {0}: 写入 {1} 条，但目标行数 {2} ≠ 源行数 {3}（可能重复键被合并）",
                                    MigrationTables.tableName(c), written, targetCount, sourceCount);
                        }
                        msgT(sender, "command.migrate.table-done",
                                "§a表 {0}: {1} → {2} 完成（{3} 条）",
                                MigrationTables.tableName(c), from.getId(), to.getId(), written);
                    } catch (Throwable t) {
                        fails.add(MigrationTables.tableName(c) + "(" + t.getMessage() + ")");
                        msgT(sender, "command.migrate.table-fail",
                                "§c表 {0}: {1} → {2} 失败: {3}",
                                MigrationTables.tableName(c), from.getId(), to.getId(), t.getMessage());
                    }
                }
                if (fails.isEmpty()) {
                    msgT(sender, "command.migrate.done",
                            "§a迁移完成: {0} → {1}，共 {2} 张表 {3} 条记录",
                            from.getId(), to.getId(), tableOk, totalRows);
                } else {
                    msgT(sender, "command.migrate.done-with-fail",
                            "§e迁移完成（部分失败）: 成功 {0} 张表 {1} 条，失败 {2} 张: {3}",
                            tableOk, totalRows, fails.size(), String.join(", ", fails));
                }
            } finally {
                MigrationTables.unlock();
            }
        });
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
