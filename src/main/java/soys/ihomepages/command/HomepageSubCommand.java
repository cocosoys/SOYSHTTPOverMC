package soys.ihomepages.command;

import org.bukkit.command.CommandSender;

import soys.soyshttpovermc.HttpOverMcPlugin;
import soys.soyshttpovermc.command.SubCommand;
import soys.soyshttpovermc.i18n.I18n;
import soys.ihomepages.api.HomeApi;

import java.util.ArrayList;
import java.util.List;

/**
 * /soyshttp homepage 子指令：首页列表查看与切换管理（归属 ihomepages 主页模块）。
 *
 * <pre>
 *   /soyshttp homepage list            —— 列出所有已记录的主页位置
 *   /soyshttp homepage set &lt;名称&gt;     —— 切换到指定主页（写 homepage.current + 同步 web.home + reload）
 *   /soyshttp homepage info            —— 显示当前主页名称
 *   /soyshttp homepage reload          —— 重新应用当前主页（按 homepage.current 刷新 web.home，不切换）
 * </pre>
 */
public class HomepageSubCommand extends SubCommand {

    private final HomeApi homeApi;

    public HomepageSubCommand(HttpOverMcPlugin plugin, HomeApi homeApi) {
        super(plugin);
        this.homeApi = homeApi;
    }

    @Override
    public String name() {
        return "homepage";
    }

    @Override
    public boolean requireOp() {
        return true;
    }

    @Override
    public String usage() {
        return I18n.t("command.homepage.usage", "/soyshttp homepage <list|set <name>|info|reload> —— 首页列表查看与切换管理");
    }

    @Override
    public String detail() {
        return I18n.t("command.homepage.detail",
                "首页列表查看与切换管理\n" +
                "  /soyshttp homepage list            —— 列出所有已记录的主页位置\n" +
                "  /soyshttp homepage set <名称>     —— 切换到指定主页（写 homepage.current + 同步 web.home + reload）\n" +
                "  /soyshttp homepage info            —— 显示当前主页名称\n" +
                "  /soyshttp homepage reload          —— 重新应用当前主页（按 homepage.current 刷新 web.home，不切换）\n" +
                "示例:\n" +
                "  /soyshttp homepage list\n" +
                "  /soyshttp homepage set default\n" +
                "  /soyshttp homepage reload");
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            msgT(sender, "command.homepage.usage-short", "用法: /{0} {1}", label, usage());
            return;
        }

        if (homeApi == null) {
            msgT(sender, "command.homepage.uninit", "§c首页模块尚未初始化。");
            return;
        }

        String sub = args[1].toLowerCase();

        switch (sub) {
            case "list": {
                List<String> names = homeApi.list();
                if (names.isEmpty()) {
                    msgT(sender, "command.homepage.none", "§e当前没有已记录的主页。");
                } else {
                    msgT(sender, "command.homepage.list-title", "§a已记录主页列表:");
                    String current = homeApi.getCurrent();
                    for (String n : names) {
                        boolean isCurrent = n.equals(current);
                        String marker = isCurrent
                                ? I18n.t("command.homepage.current-marker", " §b← 当前")
                                : "";
                        msgT(sender, "command.homepage.item", "  §7- §f{0}{1}", n, marker);
                    }
                }
                break;
            }
            case "set": {
                if (args.length < 3) {
                    msgT(sender, "command.homepage.set-usage", "§c用法: /{0} homepage set <首页名称>", label);
                    return;
                }
                String target = args[2];
                if (homeApi.switchTo(target)) {
                    msgT(sender, "command.homepage.switched", "§a已切换到主页: §f{0}§a（已同步 web.home 并 reload）", target);
                } else {
                    msgT(sender, "command.homepage.not-found",
                            "§c未找到名为 '{0}' 的主页。可用 '/{1} homepage list' 查看所有已记录的主页。",
                            target, label);
                }
                break;
            }
            case "info": {
                String cur = homeApi.getCurrent();
                if (cur == null) {
                    msgT(sender, "command.homepage.no-current", "§e当前未设置主页。");
                } else {
                    msgT(sender, "command.homepage.current", "§a当前主页: §f{0}", cur);
                }
                break;
            }
            case "reload": {
                // 按 homepage.current 重新应用 web.home（不切换）；等价于 /soyshttp reload 中对本模块的钩子动作
                plugin.reloadHttpConfig();
                msgT(sender, "command.homepage.reloaded", "§a已按当前主页重新应用 web.home。");
                break;
            }
            default: {
                msgT(sender, "command.homepage.unknown",
                        "§c未知子指令: {0}。可用: list / set <name> / info / reload", sub);
            }
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            // 二级补全：list / set / info / reload
            List<String> out = new ArrayList<>();
            String prefix = args[1].toLowerCase();
            for (String opt : new String[]{"list", "set", "info", "reload"}) {
                if (opt.startsWith(prefix)) out.add(opt);
            }
            return out;
        }
        if (args.length == 3 && "set".equalsIgnoreCase(args[1]) && homeApi != null) {
            // 三级补全：set 后面的主页名称
            String prefix = args[2].toLowerCase();
            List<String> out = new ArrayList<>();
            for (String name : homeApi.list()) {
                if (name.toLowerCase().startsWith(prefix)) out.add(name);
            }
            return out;
        }
        return java.util.Collections.emptyList();
    }
}
