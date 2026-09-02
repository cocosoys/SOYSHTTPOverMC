package com.github.cocosoys.mc.soyshttpovermc.command;

import com.github.cocosoys.mc.soyshttpovermc.HttpOverMcPlugin;
import com.github.cocosoys.mc.soyshttpovermc.i18n.I18n;
import com.github.cocosoys.mc.soyshttpovermc.storage.DataStorage;
import com.github.cocosoys.mc.soyshttpovermc.storage.StorageManager;
import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * /soyshttp sync —— 将主存储全量数据覆盖同步到所有辅助存储
 * ⚠️ 多实例共享同一 MySQL 主库时请勿互相同步。
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
        return I18n.t("command.sync.usage", "/soyshttp sync —— 主存储全量数据覆盖写入所有辅助存储");
    }

    @Override
    public String detail() {
        return usage() + "\n"
                + I18n.t("command.sync.detail",
                "  多实例共享同一 MySQL 主库时请勿执行（会以本服主库覆盖其它实例的辅助副本）");
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        StorageManager manager = plugin.getStorageManager();
        if (manager == null) {
            msgT(sender, "command.sync.storage-off", "§c数据存储未启用（内存模式），无法同步");
            return;
        }
        if (manager.getSecondaries().isEmpty()) {
            msgT(sender, "command.sync.no-secondary", "§e无辅助存储，无需同步");
            return;
        }
        msgT(sender, "command.sync.start", "§7开始同步主存储 → {0}", describe(manager));
        manager.submit(() -> {
            try {
                int count = manager.syncToSecondaries();
                msgT(sender, "command.sync.done",
                        "§a同步完成，已写入 {0} 条记录到所有辅助存储", count);
            } catch (Exception e) {
                msgT(sender, "command.sync.fail", "§c同步失败: {0}", e.getMessage());
            }
        });
    }

    private String describe(StorageManager manager) {
        StringBuilder sb = new StringBuilder();
        for (DataStorage s : manager.getSecondaries()) {
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
