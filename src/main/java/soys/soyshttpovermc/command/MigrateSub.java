package soys.soyshttpovermc.command;

import soys.soyshttpovermc.HttpOverMcPlugin;
import soys.soyshttpovermc.storage.StorageManager;
import soys.soyshttpovermc.storage.StorageType;

import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.List;

/**
 * /soyshttp migrate &lt;来源&gt; &lt;目标&gt; [overwrite] —— 在任意两个存储后端之间迁移全量数据
 * （照抄 SOYSOceanBox 的 /soceanboxadmin migrate）。异步执行，完成后回执迁移记录数。
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
        return "/soyshttp migrate <来源> <目标> [overwrite] —— 在 yaml/sqlite/mysql 后端间迁移全量数据";
    }

    @Override
    public String detail() {
        return usage() + "\n  来源/目标: yaml | sqlite | mysql（须已启用）\n"
                + "  追加 overwrite 先清空目标后端再写入（默认不清空，按 key 覆盖）\n"
                + "  示例: /soyshttp migrate yaml mysql —— 把 yaml 数据迁入 mysql";
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        StorageManager manager = plugin.getStorageManager();
        if (manager == null) {
            msg(sender, "§c数据存储未启用（内存模式），无法迁移");
            return;
        }
        if (args.length < 3) {
            msg(sender, "§e" + usage());
            return;
        }
        StorageType from = StorageType.fromId(args[1]);
        StorageType to = StorageType.fromId(args[2]);
        if (from == null || to == null) {
            msg(sender, "§c未知后端（可选: yaml / sqlite / mysql）");
            return;
        }
        if (from == to) {
            msg(sender, "§c来源与目标不能相同");
            return;
        }
        boolean overwrite = args.length > 3 && "overwrite".equalsIgnoreCase(args[3]);
        msg(sender, "§7开始迁移: " + from.getDisplayName() + " → " + to.getDisplayName()
                + (overwrite ? "（overwrite）" : ""));
        manager.submit(() -> {
            try {
                int count = manager.migrate(from, to, overwrite);
                msg(sender, "§a迁移完成，共 " + count + " 条记录 → " + to.getDisplayName());
            } catch (Exception e) {
                msg(sender, "§c迁移失败: " + e.getMessage());
            }
        });
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> backends = Arrays.asList("yaml", "sqlite", "mysql");
        if (args.length == 2 || args.length == 3) {
            return backends;
        }
        return java.util.Collections.emptyList();
    }
}
