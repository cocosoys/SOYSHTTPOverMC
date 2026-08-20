package soys.soyshttpovermc.command;

import org.bukkit.command.CommandSender;

import soys.soyshttpovermc.HttpOverMcPlugin;
import soys.soyshttpovermc.i18n.I18n;
import soys.soyshttpovermc.web.WebRegistry;

import java.util.Arrays;
import java.util.List;

/**
 * /soyshttp pages —— 查看已登记的网页。
 *
 * <ul>
 *   <li>无参数 {@code /shttp pages}：仅展示<b>可打开界面</b>（.html 页 + 跳转入口，含内置与第三方插件登记），
 *       不罗列 .js / .css / .vue / 图片等纯静态资源，避免刷屏；</li>
 *   <li>带参数 {@code /shttp pages all}（亦可 {@code resources} / {@code *}）：展示<b>全部登记项</b>
 *       （页 + 资源 + 跳转），并标注每项种类（页/资源/跳转→目标），便于排查资源未加载等问题。</li>
 * </ul>
 */
public class PagesSubCommand extends SubCommand {

    /** 内置可打开界面（.html 页），owner 标记为内置。路径 → 说明（i18n 键）。 */
    private static final String[][] BUILTIN_PAGES = {
            {"/", "command.pages.builtin.root-home"},
            {"/login", "command.pages.builtin.login-page"},
            {"/login.html", "command.pages.builtin.login-page-html"},
            {"/status", "command.pages.builtin.status-panel"},
            {"/news", "command.pages.builtin.news-page"},
    };

    /** 内置纯静态资源（脚本/图标），默认不展示，仅在 {@code pages all} 时列出。路径 → 说明（i18n 键）。 */
    private static final String[][] BUILTIN_RESOURCES = {
            {"/soys-auth.js", "command.pages.builtin.auth-js"},
            {"/favicon.ico", "command.pages.builtin.favicon"},
    };

    /** “查看全部”的别名参数（忽略大小写）。 */
    private static final List<String> ALL_ARGS = Arrays.asList("all", "resources", "*");

    public PagesSubCommand(HttpOverMcPlugin plugin) {
        super(plugin);
    }

    @Override
    public String name() {
        return "pages";
    }

    @Override
    public String usage() {
        return I18n.t("command.pages.usage",
                "/soyshttp pages [all] —— 查看已登记界面（默认仅 UI 页；all 含全部资源/脚本）");
    }

    @Override
    public String detail() {
        return I18n.t("command.pages.detail",
                "/soyshttp pages [all] —— 查看已登记的网页。\n"
                + "  无参数    仅列出可打开界面（.html 页 + 跳转入口），隐藏 .js/.css/.vue/图片等纯资源。\n"
                + "  all       列出全部登记项（含资源/脚本），并标注种类 [页]/[资源]/[跳转→目标]。\n"
                + "别名：resources、* 与 all 等价。");
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        WebRegistry reg = plugin.getWebRegistry();
        if (reg == null) {
            msgT(sender, "command.pages.uninit", "网页登记处未初始化");
            return;
        }
        boolean all = args.length > 1 && ALL_ARGS.contains(args[1].toLowerCase());

        if (all) {
            msgT(sender, "command.pages.title-all", "§a全部已登记项（内置 + 第三方；含页/资源/跳转）:");
            printBuiltin(sender, BUILTIN_PAGES);
            printBuiltin(sender, BUILTIN_RESOURCES);
            printRegistry(sender, reg, true);
            return;
        }

        msgT(sender, "command.pages.title-default", "§a可打开界面（.html 页 + 跳转，内置 + 第三方）:");
        printBuiltin(sender, BUILTIN_PAGES);
        printRegistry(sender, reg, false);
        sender.sendMessage(I18n.t("command.pages.all-hint",
                "  §7（查看全部资源/脚本请输入 §f/shttp pages all§7）"));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) return Arrays.asList("all");
        return java.util.Collections.emptyList();
    }

    private void printBuiltin(CommandSender sender, String[][] items) {
        for (String[] p : items) {
            sender.sendMessage("  §e" + p[0] + " §7—— " + I18n.t(p[1], p[1]));
        }
    }

    private void printRegistry(CommandSender sender, WebRegistry reg, boolean all) {
        int shown = 0;
        for (WebRegistry.Entry e : reg.listEntries()) {
            if (!all && !e.isNavigable()) continue;
            String owner = e.ownerPlugin == null ? "?" : e.ownerPlugin;
            sender.sendMessage("  §e" + e.path + " §7(owner=" + owner + ") [" + e.kindLabel() + "]");
            shown++;
        }
        if (shown == 0) {
            sender.sendMessage(I18n.t("command.pages.empty-thirdparty", "  §7（无第三方插件登记的网页）"));
        }
    }
}
