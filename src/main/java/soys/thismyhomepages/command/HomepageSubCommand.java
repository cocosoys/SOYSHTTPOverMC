package soys.thismyhomepages.command;

import org.bukkit.command.CommandSender;

import soys.soyshttpovermc.HttpOverMcPlugin;
import soys.soyshttpovermc.command.SubCommand;
import soys.soyshttpovermc.i18n.I18n;
import soys.thismyhomepages.homepage.HomepageRegistry;
import soys.thismyhomepages.homepage.HomepageState;

import java.util.ArrayList;
import java.util.List;

/**
 * /soyshttp homepage 子指令：首页列表查看与切换管理（归属 thismyhomepages 主页模块）。
 *
 * <pre>
 *   /soyshttp homepage list          —— 列出所有已注册的首页
 *   /soyshttp homepage set &lt;名称&gt;   —— 切换到指定首页
 *   /soyshttp homepage info          —— 显示当前首页名称
 * </pre>
 */
public class HomepageSubCommand extends SubCommand {

    private final HomepageRegistry registry;
    private final HomepageState state;

    public HomepageSubCommand(HttpOverMcPlugin plugin, HomepageRegistry registry) {
        super(plugin);
        this.registry = registry;
        this.state = new HomepageState(plugin);
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
        return I18n.t("command.homepage.usage", "homepage <list|set <name>|info> —— 首页列表查看与切换管理");
    }

    @Override
    public String detail() {
        return I18n.t("command.homepage.detail",
                "首页列表查看与切换管理\n" +
                "  /soyshttp homepage list          —— 列出所有已注册的首页\n" +
                "  /soyshttp homepage set <名称>   —— 切换到指定首页\n" +
                "  /soyshttp homepage info          —— 显示当前首页名称\n" +
                "示例:\n" +
                "  /soyshttp homepage list\n" +
                "  /soyshttp homepage set default\n" +
                "  /soyshttp homepage info");
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            msgT(sender, "command.homepage.usage-short", "用法: /{0} {1}", label, usage());
            return;
        }

        if (registry == null) {
            msgT(sender, "command.homepage.uninit", "§c首页注册表尚未初始化。");
            return;
        }

        String sub = args[1].toLowerCase();

        switch (sub) {
            case "list": {
                List<String> names = registry.list();
                if (names.isEmpty()) {
                    msgT(sender, "command.homepage.none", "§e当前没有已注册的首页。");
                } else {
                    msgT(sender, "command.homepage.list-title", "§a已注册首页列表:");
                    String current = registry.getCurrentName();
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
                if (registry.switchTo(target)) {
                    // 持久化当前选择到 thismyhomepages/config.yml
                    state.saveCurrent(target);
                    msgT(sender, "command.homepage.switched", "§a已切换到首页: §f{0}", target);
                } else {
                    msgT(sender, "command.homepage.not-found",
                            "§c未找到名为 '{0}' 的首页。可用 '/{1} homepage list' 查看所有已注册的首页。",
                            target, label);
                }
                break;
            }
            case "info": {
                String cur = registry.getCurrentName();
                if (cur == null) {
                    msgT(sender, "command.homepage.no-current", "§e当前未设置首页。");
                } else {
                    msgT(sender, "command.homepage.current", "§a当前首页: §f{0}", cur);
                }
                break;
            }
            default: {
                msgT(sender, "command.homepage.unknown",
                        "§c未知子指令: {0}。可用: list / set <name> / info", sub);
            }
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            // 二级补全：list / set / info
            List<String> out = new ArrayList<>();
            String prefix = args[1].toLowerCase();
            for (String opt : new String[]{"list", "set", "info"}) {
                if (opt.startsWith(prefix)) out.add(opt);
            }
            return out;
        }
        if (args.length == 3 && "set".equalsIgnoreCase(args[1]) && registry != null) {
            // 三级补全：set 后面的首页名称
            String prefix = args[2].toLowerCase();
            List<String> out = new ArrayList<>();
            for (String name : registry.list()) {
                if (name.toLowerCase().startsWith(prefix)) out.add(name);
            }
            return out;
        }
        return java.util.Collections.emptyList();
    }
}