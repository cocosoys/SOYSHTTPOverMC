package soys.soyshttpovermc.command;

import org.bukkit.command.CommandSender;

import soys.soyshttpovermc.HttpOverMcPlugin;
import soys.soyshttpovermc.log.LogKit;

/**
 * /soyshttp reload —— 热重载日志级别 + 网关策略与 TLS 配置（gateway/ 目录），无需重启服务器。
 */
public class ReloadSubCommand extends SubCommand {

    public ReloadSubCommand(HttpOverMcPlugin plugin) {
        super(plugin);
    }

    @Override
    public String name() {
        return "reload";
    }

    @Override
    public String usage() {
        return "/soyshttp reload —— 热重载配置与网关";
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        plugin.reloadHttpConfig();
        msg(sender, "网关策略已热重载："
                + (plugin.getGateway() == null ? "网关关闭" : plugin.getGateway().getPolicies().size() + " 个策略启用")
                + "，HTTPS=" + (plugin.isTlsEnabled() ? "开" : "关")
                + "，事件调试=" + (plugin.isDebugEventsEnabled() ? "开" : "关")
                + "，日志级别=" + LogKit.levelName() + "（配置=" + plugin.getConfig().getString("log.level", "INFO") + "）");
    }
}
