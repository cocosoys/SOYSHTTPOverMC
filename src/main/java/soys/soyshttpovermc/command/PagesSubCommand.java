package soys.soyshttpovermc.command;

import org.bukkit.command.CommandSender;

import soys.soyshttpovermc.HttpOverMcPlugin;
import soys.soyshttpovermc.web.WebRegistry;

import java.util.List;

/**
 * /soyshttp pages —— 查看当前已注册的全部网页（含 SOYSHTTPOverMC 内置页面与第三方插件登记页面）。
 */
public class PagesSubCommand extends SubCommand {

    /** 内置静态页面清单（jar /web/ 资源，owner 标记为内置）：路径 → 说明。 */
    private static final String[][] BUILTIN = {
            {"/", "游戏门户首页"},
            {"/login", "网页登录页（等价 /login.html）"},
            {"/login.html", "网页登录页（带 .html 形式）"},
            {"/status", "赛博朋克状态面板"},
            {"/news", "资讯页"},
            {"/soys-auth.js", "公共登录组件（任意页面引用即用）"},
            {"/favicon.ico", "站点图标（磁盘 web/favicon.ico 可热替换）"},
    };

    public PagesSubCommand(HttpOverMcPlugin plugin) {
        super(plugin);
    }

    @Override
    public String name() {
        return "pages";
    }

    @Override
    public String usage() {
        return "/soyshttp pages —— 查看已注册页面（含内置）";
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        WebRegistry reg = plugin.getWebRegistry();
        if (reg == null) {
            msg(sender, "网页登记处未初始化");
            return;
        }
        msg(sender, "§a内置页面（" + BUILTIN.length + "）:");
        for (String[] p : BUILTIN) {
            sender.sendMessage("  §e" + p[0] + " §7—— " + p[1]);
        }
        List<String> list = reg.listPaths();
        if (list.isEmpty()) {
            sender.sendMessage("  §7（无第三方插件登记的网页）");
        } else {
            msg(sender, "§a第三方插件登记页面（" + list.size() + "）:");
            for (String s : list) {
                int idx = s.indexOf(" (owner=");
                String path = idx > 0 ? s.substring(0, idx) : s;
                String owner = idx > 0 ? s.substring(idx + 8, s.length() - 1) : "?";
                sender.sendMessage("  §e" + path + " §7(owner=" + owner + ")");
            }
        }
    }
}
