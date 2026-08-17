package soys.soyshttpovermc.command;

import soys.soyshttpovermc.HttpOverMcPlugin;
import soys.soyshttpovermc.storage.StorageManager;

import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * /soyshttp sync —— 将主存储全量数据覆盖同步到所有辅助存储
 * （照抄 SOYSOceanBox 的 /soceanboxadmin sync）。⚠️ 多实例共享同一 MySQL 主库时请勿互相同步。
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
        return "/soyshttp sync —— 主存储全量数据覆盖写入所有辅助存储";
    }

    @Override
    public String detail() {
        return usage() + "\n  ⚠️ 多实例共享同一 MySQL 主库时请勿执行（会以本服主库覆盖其它实例的辅助副本）";
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        StorageManager manager = plugin.getStorageManager();
        if (manager == null) {
            msg(sender, "§c数据存储未启用（内存模式），无法同步");
            return;
        }
        if (manager.getSecondaries().isEmpty()) {
            msg(sender, "§e无辅助存储，无需同步");
            return;
        }
        msg(sender, "§7开始同步主存储 → " + describe(manager));
        manager.submit(() -> {
            try {
                int count = manager.syncToSecondaries();
                msg(sender, "§a同步完成，已写入 " + count + " 条记录到所有辅助存储");
            } catch (Exception e) {
                msg(sender, "§c同步失败: " + e.getMessage());
            }
        });
    }

    private String describe(StorageManager manager) {
        StringBuilder sb = new StringBuilder();
        for (soys.soyshttpovermc.storage.DataStorage s : manager.getSecondaries()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(s.getType().getDisplayName());
        }
        return sb.toString();
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return java.util.Collections.emptyList();
    }
}
