package com.github.cocosoys.mc.soyshttpovermc.command;
import lombok.CustomLog;

import org.bukkit.command.CommandSender;

import com.github.cocosoys.mc.soyshttpovermc.HttpOverMcPlugin;
import com.github.cocosoys.mc.soyshttpovermc.i18n.I18n;

/**
 * /soyshttp report —— 手动上报插件使用记录（config.yml upload.server，无需 upload.enabled 开关）。
 */
@CustomLog
public class ReportSubCommand extends SubCommand {

    public ReportSubCommand(HttpOverMcPlugin plugin) {
        super(plugin);
        hide=true;
    }

    @Override
    public String name() {
        return "report";
    }

    @Override
    public String usage() {
        return I18n.t("command.report.usage", "/soyshttp report —— 手动上报插件使用记录");
    }

    @Override
    public String detail() {
        return I18n.t("command.report.detail",
                "/soyshttp report —— 手动上报插件使用记录\n"
                + "立即把当前服务器地址（IP:端口）匿名 POST 上报到 config.yml 的 upload.server，\n"
                + "不依赖 upload.enabled 开关；仅携带地址与端口，失败不影响插件运行。");
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        plugin.reportContribution();
        msgT(sender, "command.report.sent", "§a已开始手动上报插件使用记录（异步，结果见后台日志）");
    }
}