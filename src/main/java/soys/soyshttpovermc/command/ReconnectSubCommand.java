package soys.soyshttpovermc.command;

import org.bukkit.command.CommandSender;

import soys.soyshttpovermc.HttpOverMcPlugin;

/**
 * /soyshttp reconnect —— 主 Bot 重新连接（被踢出游戏等特殊情况后恢复隧道）。
 */
public class ReconnectSubCommand extends SubCommand {

    public ReconnectSubCommand(HttpOverMcPlugin plugin) {
        super(plugin);
    }

    @Override
    public String name() {
        return "reconnect";
    }

    @Override
    public String usage() {
        return "/soyshttp reconnect —— 主 Bot 重新连接";
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (plugin.getBotManager() == null) {
            msg(sender, "Bot 未初始化");
            return;
        }
        plugin.getBotManager().reconnectMainBot();
        msg(sender, "主 Bot 重新连接已触发: user="
                + plugin.getConfig().getString("bot.username", "__http_proxy__") + "，请稍候查看日志确认就绪");
    }
}
