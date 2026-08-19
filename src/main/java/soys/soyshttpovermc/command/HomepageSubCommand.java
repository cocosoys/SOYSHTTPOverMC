package soys.soyshttpovermc.command;

import org.bukkit.command.CommandSender;

import soys.soyshttpovermc.HttpOverMcPlugin;
import soys.soyshttpovermc.web.WebRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * /soyshttp homepage 子指令：首页列表查看与切换管理。
 *
 * <pre>
 *   /soyshttp homepage list          —— 列出所有已注册的首页
 *   /soyshttp homepage set &lt;名称&gt;   —— 切换到指定首页
 *   /soyshttp homepage info          —— 显示当前首页名称
 * </pre>
 */
public class HomepageSubCommand extends SubCommand {

    public HomepageSubCommand(HttpOverMcPlugin plugin) {
        super(plugin);
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
        return "homepage <list|set <name>|info> —— 首页列表查看与切换管理";
    }

    @Override
    public String detail() {
        return "首页列表查看与切换管理\n" +
                "  /soyshttp homepage list          —— 列出所有已注册的首页\n" +
                "  /soyshttp homepage set <名称>   —— 切换到指定首页\n" +
                "  /soyshttp homepage info          —— 显示当前首页名称\n" +
                "示例:\n" +
                "  /soyshttp homepage list\n" +
                "  /soyshttp homepage set default\n" +
                "  /soyshttp homepage info";
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            msg(sender, "用法: /" + label + " " + usage());
            return;
        }

        WebRegistry web = plugin.getWebRegistry();
        if (web == null) {
            msg(sender, "§c网页注册表尚未初始化。");
            return;
        }

        String sub = args[1].toLowerCase();

        switch (sub) {
            case "list": {
                List<String> names = web.getHomepageNames();
                if (names.isEmpty()) {
                    msg(sender, "§e当前没有已注册的首页。");
                } else {
                    msg(sender, "§a已注册首页列表:");
                    String current = web.getCurrentHomepageName();
                    for (String n : names) {
                        boolean isCurrent = n.equals(current);
                        String marker = isCurrent ? " §b← 当前" : "";
                        msg(sender, "  §7- §f" + n + marker);
                    }
                }
                break;
            }
            case "set": {
                if (args.length < 3) {
                    msg(sender, "§c用法: /" + label + " homepage set <首页名称>");
                    return;
                }
                String target = args[2];
                if (web.switchHomepage(target)) {
                    // 持久化当前选择到 config.yml
                    plugin.getConfig().set("homepage.current", target);
                    plugin.saveConfig();
                    msg(sender, "§a已切换到首页: §f" + target);
                } else {
                    msg(sender, "§c未找到名为 '" + target + "' 的首页。可用 '/" + label
                            + " homepage list' 查看所有已注册的首页。");
                }
                break;
            }
            case "info": {
                String cur = web.getCurrentHomepageName();
                if (cur == null) {
                    msg(sender, "§e当前未设置首页。");
                } else {
                    msg(sender, "§a当前首页: §f" + cur);
                }
                break;
            }
            default: {
                msg(sender, "§c未知子指令: " + sub + "。可用: list / set <name> / info");
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
        if (args.length == 3 && "set".equalsIgnoreCase(args[1])) {
            // 三级补全：set 后面的首页名称
            WebRegistry web = plugin.getWebRegistry();
            if (web != null) {
                String prefix = args[2].toLowerCase();
                List<String> out = new ArrayList<>();
                for (String name : web.getHomepageNames()) {
                    if (name.toLowerCase().startsWith(prefix)) out.add(name);
                }
                return out;
            }
        }
        return java.util.Collections.emptyList();
    }
}