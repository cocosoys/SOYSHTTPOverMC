package soys.soyshttpovermc.command;
import soys.soyshttpovermc.enums.StorageType;

import soys.soyshttpovermc.HttpOverMcPlugin;
import soys.soyshttpovermc.i18n.I18n;
import soys.soyshttpovermc.storage.StorageManager;

import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * /soyshttp migrate &lt;来源&gt; &lt;目标&gt; [overwrite] —— 在任意两个存储后端之间迁移全量数据
 * 异步执行，完成后回执迁移记录数。
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
                "/soyshttp migrate <来源> <目标> [overwrite] —— 在 " + backendNames() + " 后端间迁移全量数据");
    }

    @Override
    public String detail() {
        return usage() + "\n  来源/目标: " + backendNames() + "（须已启用）\n"
                + "  追加 overwrite 先清空目标后端再写入（默认不清空，按 key 覆盖）\n"
                + "  示例: /soyshttp migrate yaml mysql —— 把 yaml 数据迁入 mysql";
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        StorageManager manager = plugin.getStorageManager();
        if (manager == null) {
            msgT(sender, "command.migrate.storage-off",
                    "§c数据存储未启用（内存模式），无法迁移");
            return;
        }
        if (args.length < 3) {
            msg(sender, "§e" + usage());
            return;
        }
        StorageType from = StorageType.fromId(args[1]);
        StorageType to = StorageType.fromId(args[2]);
        if (from == null || to == null) {
            msgT(sender, "command.migrate.unknown-backend", "§c未知后端（可选: " + backendNames() + "）");
            return;
        }
        if (from == to) {
            msgT(sender, "command.migrate.same-backend", "§c来源与目标不能相同");
            return;
        }
        boolean overwrite = args.length > 3 && "overwrite".equalsIgnoreCase(args[3]);
        msgT(sender, "command.migrate.start", "§7开始迁移: {0} → {1}{2}",
                from.getDisplayName(), to.getDisplayName(),
                overwrite ? I18n.t("command.migrate.overwrite-suffix", "（overwrite）") : "");
        manager.submit(() -> {
            try {
                int count = manager.migrate(from, to, overwrite);
                msgT(sender, "command.migrate.done", "§a迁移完成，共 {0} 条记录 → {1}", count, to.getDisplayName());
            } catch (Exception e) {
                msgT(sender, "command.migrate.fail", "§c迁移失败: {0}", e.getMessage());
            }
        });
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2 || args.length == 3) {
            List<String> backends = new java.util.ArrayList<>(StorageType.values().length);
            for (StorageType t : StorageType.values()) {
                backends.add(t.getId());
            }
            return backends;
        }
        return java.util.Collections.emptyList();
    }

    /** 全部后端 ID 的斜杠拼接（如 "yaml/sqlite/mysql"），供提示文本复用，避免散落硬编码。 */
    private static String backendNames() {
        StringBuilder sb = new StringBuilder();
        for (StorageType t : StorageType.values()) {
            if (sb.length() > 0) sb.append('/');
            sb.append(t.getId());
        }
        return sb.toString();
    }
}
