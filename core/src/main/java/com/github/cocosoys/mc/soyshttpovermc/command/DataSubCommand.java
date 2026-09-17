package com.github.cocosoys.mc.soyshttpovermc.command;

import com.github.cocosoys.mc.soyshttpovermc.HttpOverMcPlugin;
import com.github.cocosoys.mc.soyshttpovermc.web.DataHandle;
import com.github.cocosoys.mc.soyshttpovermc.api.DataRegistrationApi;
import com.github.cocosoys.mc.soyshttpovermc.i18n.I18n;
import com.github.cocosoys.mc.soyshttpovermc.orm.SchemaRegistry;
import com.github.cocosoys.mc.soyshttpovermc.orm.SoysSchemaMeta;
import com.github.cocosoys.mc.soyshttpovermc.orm.executor.SqlBackendExecutor;
import lombok.CustomLog;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * /soyshttp data &lt;插件&gt; status|update [版本]|reinstall|uninstall —— 数据层自动化运维。
 *
 * <p>围绕 meta 表（{@code soys_schema_meta}）的运维入口：查看某插件数据状态、
 * 显式触发 schema 迁移 / 重装（保留数据）、摘除数据登记（数据保留）。</p>
 *
 * <ul>
 *   <li>{@code status} —— 展示是否安装、schema 版本、已执行脚本数、归属表、存储后端；</li>
 *   <li>{@code update [版本]} —— 显式迁移（默认到 spec 声明版本；可指定目标版本）；</li>
 *   <li>{@code reinstall} —— 保留数据重装（补默认文件 / 补列 / 迁移 / 种子补缺 / meta 刷新）；</li>
 *   <li>{@code uninstall} —— 摘除数据登记（数据与 meta 保留，重装同标识自动走保留数据更新）。</li>
 * </ul>
 *
 * <p><b>边界</b>：update/reinstall/uninstall 仅对<b>经 {@code DataRegistrationApi} 登记过句柄</b>
 * 的插件有效（此类插件启动时已自动安装/更新）；主插件 SOYSHTTPOverMC 自身数据在启动时自动管理。</p>
 */
@CustomLog
public class DataSubCommand extends SubCommand {

    public DataSubCommand(HttpOverMcPlugin plugin) {
        super(plugin);
    }

    @Override
    public String name() {
        return "data";
    }

    @Override
    public String usage() {
        return I18n.t("command.data.usage",
                "/soyshttp data <插件> status|update [版本]|reinstall|uninstall —— 数据层自动化运维");
    }

    @Override
    public String detail() {
        return I18n.t("command.data.detail",
                "/soyshttp data <插件> status|update [版本]|reinstall|uninstall —— 数据层自动化运维\n"
                        + "  status      查看该插件数据状态（meta 版本 / 脚本 / 归属表 / 存储后端）\n"
                        + "  update [版本]  显式执行 schema 迁移（默认到声明版本；可指定目标版本）\n"
                        + "  reinstall   保留数据重装（补默认文件/补列/迁移/种子补缺/meta 刷新）\n"
                        + "  uninstall   摘除数据登记（数据与 meta 保留；重装自动走保留数据更新）\n"
                        + "  示例: /soyshttp data MCERP status\n"
                        + "        /soyshttp data MCERP update 2\n"
                        + "  说明: update/reinstall/uninstall 仅对已登记数据句柄的插件有效；\n"
                        + "       主插件自身数据随启动自动管理。");
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            msg(sender, "§e" + usage());
            return;
        }
        String pluginName = args[1];
        String action = args.length >= 3 ? args[2].toLowerCase() : "status";
        DataRegistrationApi api = plugin.getApi() == null ? null : plugin.getApi().getDataRegistration();
        switch (action) {
            case "status":
                status(sender, pluginName);
                break;
            case "update":
                update(sender, pluginName, args);
                break;
            case "reinstall":
                if (api == null) {
                    msgT(sender, "command.data.api-null", "§c数据注册 API 未就绪");
                    return;
                }
                if (api.reinstall(pluginName)) {
                    msgT(sender, "command.data.reinstalled", "§a{0} 已保留数据重装完成", pluginName);
                } else {
                    msgT(sender, "command.data.op-failed", "§c{0} 重装失败（未登记数据句柄或迁移失败，见日志）", pluginName);
                }
                break;
            case "uninstall":
                if (api == null) {
                    msgT(sender, "command.data.api-null", "§c数据注册 API 未就绪");
                    return;
                }
                if (api.unregister(pluginName)) {
                    msgT(sender, "command.data.uninstalled",
                            "§a{0} 数据登记已摘除（数据与 meta 保留，重装自动走保留数据更新）", pluginName);
                } else {
                    msgT(sender, "command.data.op-failed", "§c{0} 摘除失败（未登记数据句柄）", pluginName);
                }
                break;
            default:
                msg(sender, "§e" + usage());
        }
    }

    private void status(CommandSender sender, String pluginName) {
        SoysSchemaMeta row = SchemaRegistry.getPlugin(pluginName);
        Set<String> tables = SchemaRegistry.tablesOf(pluginName);
        String backend = "YAML";
        if (com.github.cocosoys.mc.soyshttpovermc.orm.DATA.sqlEnabled()) {
            SqlBackendExecutor sql = SqlBackendExecutor.get();
            backend = sql == null ? "SQL(?)" : sql.name();
        }
        sendColored(sender, "§a§l[SOYSHTTPOverMC] §7数据运维状态：§f" + pluginName);
        line(sender, I18n.t("command.data.backend", "存储后端"), backend);
        if (row == null) {
            line(sender, I18n.t("command.data.installed", "安装状态"), I18n.t("command.data.not-installed", "无 meta 记录（未初始化）"));
            line(sender, I18n.t("command.data.tables", "归属表"), tables.isEmpty() ? "—" : String.join(", ", tables));
            return;
        }
        line(sender, I18n.t("command.data.installed", "安装状态"), I18n.t("command.data.installed-yes", "已安装"));
        line(sender, I18n.t("command.data.version", "schema 版本"), String.valueOf(row.getSchemaVersion()));
        List<String> scripts = SchemaRegistry.parseScripts(row);
        line(sender, I18n.t("command.data.scripts", "已执行脚本"), scripts.isEmpty() ? "—" : String.join(", ", scripts));
        line(sender, I18n.t("command.data.tables", "归属表"), tables.isEmpty() ? "—" : String.join(", ", tables));
        DataHandle h = plugin.getApi() == null ? null : plugin.getApi().getDataRegistration().handleOf(pluginName);
        line(sender, I18n.t("command.data.registered", "句柄登记"), h != null && h.isRegistered()
                ? I18n.t("command.data.registered-yes", "已登记（可 update/reinstall/uninstall）")
                : I18n.t("command.data.registered-no", "未登记（仅随启动自动更新）"));
    }

    private void update(CommandSender sender, String pluginName, String[] args) {
        DataRegistrationApi api = plugin.getApi() == null ? null : plugin.getApi().getDataRegistration();
        if (api == null) {
            msgT(sender, "command.data.api-null", "§c数据注册 API 未就绪");
            return;
        }
        boolean ok;
        if (args.length >= 4) {
            int target;
            try {
                target = Integer.parseInt(args[3]);
            } catch (NumberFormatException ex) {
                msgT(sender, "command.data.bad-version", "§c目标版本必须是整数: {0}", args[3]);
                return;
            }
            ok = api.update(pluginName, target);
        } else {
            ok = api.update(pluginName);
        }
        if (ok) {
            msgT(sender, "command.data.updated", "§a{0} 数据更新完成", pluginName);
        } else {
            msgT(sender, "command.data.op-failed", "§c{0} 更新失败（未登记数据句柄或迁移失败，见日志）", pluginName);
        }
    }

    private void line(CommandSender sender, String label, String value) {
        sender.sendMessage("  §7" + label + "：§f" + value);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> out = new ArrayList<>();
        DataRegistrationApi api = plugin.getApi() == null ? null : plugin.getApi().getDataRegistration();
        if (args.length == 2 && api != null) {
            out.addAll(api.registeredNames());
        } else if (args.length == 3) {
            out.add("status");
            out.add("update");
            out.add("reinstall");
            out.add("uninstall");
        }
        return out;
    }
}
