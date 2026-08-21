package soys.soyshttpovermc.command;

import org.bukkit.command.CommandSender;

import soys.soyshttpovermc.HttpOverMcPlugin;
import soys.soyshttpovermc.i18n.I18n;
import soys.soyshttpovermc.util.StringListUtil;
import soys.soyshttpovermc.web.WebRegistry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * /soyshttp pages [all] [页码] —— 查看已登记的网页（分页展示，默认每页 {@link StringListUtil#DEFAULT_PAGE_SIZE} 条）。
 *
 * <ul>
 *   <li>无参数 {@code /shttp pages}：仅展示<b>可打开界面</b>（.html 页 + 跳转入口），
 *       不罗列 .js / .css / .vue / 图片等纯静态资源，避免刷屏；
 *       （核心内置页 /login、/status、/news 在启动时已纳入注册通道，故与第三方插件登记项一同列出，非命令硬编码）</li>
 *   <li>带 {@code all}（亦可 {@code resources} / {@code *}）：展示<b>全部登记项</b>（页 + 资源 + 跳转）。</li>
 *   <li>带页码：翻页查看后续项；登记项存在 description 时自动追加 “ —— ”+description，昵称以 (昵称: ...) 标注。</li>
 * </ul>
 */
public class PagesSubCommand extends SubCommand {

    /** “查看全部”的别名参数（忽略大小写）。 */
    private static final List<String> ALL_ARGS = Arrays.asList("all", "resources", "*");
    /** 每页行数（页眉/内容/页尾由 static final 统一静态控制，默认见 {@link StringListUtil#DEFAULT_PAGE_SIZE}）。 */
    private static final int PAGE_SIZE = StringListUtil.DEFAULT_PAGE_SIZE;
    private static final String HEADER_KEY = "command.pages.title-default";
    private static final String HEADER_ALL_KEY = "command.pages.title-all";
    private static final String FOOTER_KEY = "command.pages.footer";

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
                "/soyshttp pages [all] [页码] —— 查看已登记界面（默认仅 UI 页；all 含全部资源/脚本）");
    }

    @Override
    public String detail() {
        return I18n.t("command.pages.detail",
                "/soyshttp pages [all] [页码] —— 查看已登记的网页（分页展示）。\n"
                + "  无参数       仅列出可打开界面（.html 页 + 跳转入口），隐藏 .js/.css/.vue/图片等纯资源。\n"
                + "  all          列出全部登记项（含资源/脚本），并标注种类 [页]/[资源]/[跳转→目标]。\n"
                + "  页码         翻页查看（每页 10 条，如 /soyshttp pages 2）。\n"
                + "登记项含说明时自动追加 “ —— ”+说明；昵称路由以 (昵称: ...) 标注。\n"
                + "别名：resources、* 与 all 等价。");
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        WebRegistry reg = plugin.getWebRegistry();
        if (reg == null) {
            msgT(sender, "command.pages.uninit", "网页登记处未初始化");
            return;
        }
        boolean all = false;
        int page = 1;
        for (int i = 1; i < args.length; i++) {
            String a = args[i].toLowerCase();
            if (ALL_ARGS.contains(a)) {
                all = true;
            } else if (isNumeric(a)) {
                try {
                    page = Integer.parseInt(a);
                } catch (NumberFormatException ignored) {
                    // 超长数字：忽略，维持默认页
                }
            }
        }

        List<String> content = new ArrayList<>();
        for (WebRegistry.Entry e : reg.listEntries()) {
            if (!all && !e.isNavigable()) continue;
            String owner = e.ownerPlugin == null ? "?" : e.ownerPlugin;
            String line = "  §e" + e.path + " §7(owner=" + owner + ") [" + e.kindLabel() + "]";
            if (e.description != null && !e.description.isEmpty()) {
                line += " §7—— " + e.description;
            }
            if (e.nicknames != null && !e.nicknames.isEmpty()) {
                line += " §7(昵称: " + String.join(" / ", e.nicknames) + ")";
            }
            content.add(line);
        }
        if (content.isEmpty()) {
            sender.sendMessage(I18n.t("command.pages.empty-thirdparty", "  §7（无第三方插件登记的网页）"));
            return;
        }

        int size = PAGE_SIZE;
        int totalItems = content.size();
        int totalPages = Math.max(1, (totalItems + size - 1) / size);
        int cur = Math.max(1, Math.min(page, totalPages));

        String header = I18n.t(all ? HEADER_ALL_KEY : HEADER_KEY,
                all ? "§a全部已登记项（含页/资源/跳转）:" : "§a可打开界面（.html 页 + 跳转，内置 + 第三方）:");
        String footer = I18n.t(FOOTER_KEY,
                "  §7· 第 {0}/{1} 页 · 共 {2} 条 · /soyshttp pages {3}页码 翻页 ·",
                cur, totalPages, totalItems, all ? "all " : "");
        for (String line : StringListUtil.page(header, content, footer, cur, size).lines) {
            sender.sendMessage(line);
        }
        printHome(sender, reg);
    }

    /** 首页 "/" 若已在注册表中（核心默认登记 / 第三方）则上方列表已展示；否则补一行静态解析源提示。 */
    private void printHome(CommandSender sender, WebRegistry reg) {
        for (WebRegistry.Entry e : reg.listEntries()) {
            if ("/".equals(e.path)) return;
        }
        String home = plugin.getConfig().getString("web.home", "");
        String src = (home == null || home.trim().isEmpty()) ? "默认 index.html" : home.trim();
        sender.sendMessage(I18n.t("command.pages.home-hint",
                "  §e/ §7—— 首页（静态解析源:" + src + "）"));
    }

    private static boolean isNumeric(String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) return Arrays.asList("all");
        if (args.length == 3) return Arrays.asList("2", "3", "4", "5");
        return Collections.emptyList();
    }
}