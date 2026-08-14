package soys.soyshttpovermc.command;

import org.bukkit.command.CommandSender;

import soys.soyshttpovermc.HttpOverMcPlugin;
import soys.soyshttpovermc.web.WebRegistry;

import java.util.List;

/**
 * /soyshttp pages —— 查看当前已注册的全部网页。
 */
public class PagesSubCommand extends SubCommand {

    public PagesSubCommand(HttpOverMcPlugin plugin) {
        super(plugin);
    }

    @Override
    public String name() {
        return "pages";
    }

    @Override
    public String usage() {
        return "/soyshttp pages —— 查看已注册页面";
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        WebRegistry reg = plugin.getWebRegistry();
        if (reg == null) {
            msg(sender, "网页登记处未初始化");
            return;
        }
        List<String> list = reg.listPaths();
        msg(sender, "已注册网页(" + list.size() + "):");
        for (String s : list) sender.sendMessage("  " + s);
    }
}
