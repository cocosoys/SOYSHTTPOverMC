package soys.soyshttpovermc.command;
import lombok.CustomLog;

import org.bukkit.command.CommandSender;

import soys.soyshttpovermc.HttpOverMcPlugin;
import soys.soyshttpovermc.i18n.I18n;
import soys.soyshttpovermc.log.LogKit;

/**
 * /soyshttp reload —— 热重载日志级别 + 网关策略与 TLS 配置（gateway/ 目录），无需重启服务器。
 */
@CustomLog
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
        return I18n.t("command.reload.usage", "/soyshttp reload —— 热重载配置与网关");
    }

    @Override
    public String detail() {
        return I18n.t("command.reload.detail",
                "/soyshttp reload —— 热重载日志级别 + 网关策略与 TLS 配置（gateway/ 目录），无需重启服务器。\n"
                + "影响范围：gateway/config.yml、gateway/https.yml、gateway/policies/*.yml、gateway/issuers/*.yml、日志级别。\n"
                + "注意：命令类与已加载的注解式控制器不随 reload 重载（需重启服务端生效）。");
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        plugin.reloadHttpConfig();
        String gwState = plugin.getGateway() == null
                ? I18n.t("command.reload.gateway-closed", "网关关闭")
                : I18n.t("command.reload.gateway-policies", "{0} 个策略启用", plugin.getGateway().getPolicies().size());
        String on = I18n.t("command.reload.on", "开");
        String off = I18n.t("command.reload.off", "关");
        msgT(sender, "command.reload.result",
                "网关策略已热重载：{0}，HTTPS={1}，事件调试={2}，日志级别={3}（配置={4}）",
                gwState,
                plugin.isTlsEnabled() ? on : off,
                plugin.isDebugEventsEnabled() ? on : off,
                LogKit.levelName(),
                plugin.getConfig().getString("log.level", "INFO"));
    }
}
