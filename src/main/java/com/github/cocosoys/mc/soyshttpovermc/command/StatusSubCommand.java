package com.github.cocosoys.mc.soyshttpovermc.command;
import com.github.cocosoys.mc.soyshttpovermc.enums.ProxyPlatform;
import lombok.CustomLog;

import org.bukkit.command.CommandSender;

import com.github.cocosoys.mc.soyshttpovermc.HttpOverMcPlugin;
import com.github.cocosoys.mc.soyshttpovermc.i18n.I18n;

/**
 * /soyshttp status —— 查看 HTTP 服务运行状态。
 */
@CustomLog
public class StatusSubCommand extends SubCommand {

    public StatusSubCommand(HttpOverMcPlugin plugin) {
        super(plugin);
        hide=true;
    }

    @Override
    public String name() {
        return "status";
    }

    @Override
    public String usage() {
        return I18n.t("command.status.usage", "/soyshttp status —— 查看HTTP服务运行状态");
    }

    @Override
    public String detail() {
        return I18n.t("command.status.detail",
                "/soyshttp status —— 查看HTTP服务运行状态\n"
                + "展示监听地址、HTTPS 开关、Bot 就绪、网关策略、已注册页面/API 与运行拓扑等汇总信息。");
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        String on = I18n.t("command.status.on", "开");
        String off = I18n.t("command.status.off", "关");
        String yes = I18n.t("command.status.yes", "就绪");
        String no = I18n.t("command.status.no", "未就绪");
        String topo = I18n.t("command.status.standalone", "独立服");
        if (plugin.getProxyPlatform() != null
                && plugin.getProxyPlatform() != ProxyPlatform.STANDALONE) {
            topo = plugin.getServerName() != null && !plugin.getServerName().isEmpty()
                    ? plugin.getServerName()
                    : plugin.getProxyPlatform().toString();
        }
        String gw = plugin.getGateway() == null
                ? I18n.t("command.status.gw-closed", "关闭")
                : I18n.t("command.status.gw-policies", "{0} 个策略启用", plugin.getGateway().getPolicies().size());
        int pages = plugin.getWebRegistry() == null || plugin.getWebRegistry().listEntries() == null
                ? 0 : plugin.getWebRegistry().listEntries().size();
        int apis = plugin.getApiRegistry() == null || plugin.getApiRegistry().listEndpoints() == null
                ? 0 : plugin.getApiRegistry().listEndpoints().size();
        int bots = plugin.getBotManager() == null ? 0 : plugin.getBotManager().getBotNames().size();

        sender.sendMessage(I18n.t("command.status.title", "§a§l[SOYSHTTPOverMC] §7HTTP 服务运行状态："));
        line(sender, I18n.t("command.status.addr", "监听地址"), plugin.getDelegate().getMcHost() + ":" + plugin.getDelegate().getMcPort());
        line(sender, I18n.t("command.status.https", "HTTPS(TLS)"), plugin.getDelegate().isTlsEnabled() ? on : off);
        line(sender, I18n.t("command.status.bot", "Bot 隧道"), plugin.getDelegate().isBotReady() ? yes : no);
        line(sender, I18n.t("command.status.gw", "网关"), gw);
        line(sender, I18n.t("command.status.pages", "已注册页面"), String.valueOf(pages));
        line(sender, I18n.t("command.status.apis", "已注册 API"), String.valueOf(apis));
        line(sender, I18n.t("command.status.bot-count", "受管 Bot"), String.valueOf(bots));
        line(sender, I18n.t("command.status.topo", "运行拓扑"), topo);
    }

    private void line(CommandSender sender, String label, String value) {
        sender.sendMessage("  §7" + label + "：§f" + value);
    }
}