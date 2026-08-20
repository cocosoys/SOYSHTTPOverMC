package soys.soyshttpovermc.command;

import org.bukkit.command.CommandSender;

import soys.soyshttpovermc.HttpOverMcPlugin;
import soys.soyshttpovermc.i18n.I18n;

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
        return I18n.t("command.reconnect.usage", "/soyshttp reconnect —— 主 Bot 重新连接");
    }

    @Override
    public String detail() {
        return I18n.t("command.reconnect.detail",
                "/soyshttp reconnect —— 触发主 Bot 重新连接（被踢出游戏等特殊情况后恢复隧道）。\n"
                + "适用于 Bot 掉线 / 被踢后 HTTP 隧道不可用的情况；执行后请观察日志，\n"
                + "确认 Bot 重新进服并注册插件消息通道（httpproxy:main）后再访问页面。");
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (plugin.getBotManager() == null) {
            msgT(sender, "command.reconnect.bot-uninit", "Bot 未初始化");
            return;
        }
        plugin.getBotManager().reconnectMainBot();
        msgT(sender, "command.reconnect.triggered",
                "主 Bot 重新连接已触发: user={0}，请稍候查看日志确认就绪",
                plugin.getConfig().getString("bot.username", "__http_proxy__"));
    }
}
