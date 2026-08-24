package soys.soyshttpovermc.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import soys.soyshttpovermc.HttpOverMcPlugin;
import soys.soyshttpovermc.i18n.I18n;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static soys.soyshttpovermc.util.StringListUtil.matchByPrefix;

import soys.soyshttpovermc.util.StringListUtil;

/**
 * /soyshttp 命令执行器（从 {@code HttpOverMcPlugin} 抽离）：
 * 本类仅负责<b>分发</b>与<b>op 校验</b>与<b>tab 补全</b>，具体逻辑下放到各 {@link SubCommand} 子类。
 * help 总览/详情现已抽为独立子指令 {@link HelpSubCommand}（复用本类的 {@link #sendUsage}），便于单独维护。
 *
 * <p>当前已注册子指令（{@code help} 自身亦为其中之一，隐藏子指令见 {@link SubCommand#isHide()}）：
 * <ul>
 *   <li>{@code reload} —— 热重载配置与网关；</li>
 *   <li>{@code key <subject>} —— 为指定主体下发最高权限凭证；</li>
 *   <li>{@code reconnect} —— 主 Bot 重新连接；</li>
 *   <li>{@code send <url|/page> [显示文字] [玩家]} —— 向玩家发送可点击链接；</li>
 *   <li>{@code pages} —— 查看已注册的界面（默认仅 .html 页 + 跳转；{@code pages all} 含全部资源/脚本）；</li>
 *   <li>{@code api} —— 查看已注册的注解式 API 端点（方法/路径/owner/权限）；</li>
 *   <li>{@code tokens} —— 查询所有已颁发的会话令牌；</li>
 *   <li>{@code lang [语言代码]} —— 查看/切换当前语言（language/ 目录语言包）；</li>
 *   <li>{@code help [子指令|页码]} —— 查看全部子指令总览（分页），或某子指令的详细用法。</li>
 * </ul>
 * 另支持简写 {@code /shttp}（plugin.yml 注册别名命令，共用本执行器）。
 *
 * <p><b>新增子指令</b>：见 {@link SubCommand} 类注释，只需新建子类并到本构造器 {@code register(...)} 即可。
 */
public class SoysHttpCommand implements CommandExecutor, TabCompleter {

    private final HttpOverMcPlugin plugin;
    /** 子指令注册表：name(小写) -> 实例（LinkedHashMap 保持 help 展示顺序）。 */
    private final Map<String, SubCommand> subs = new LinkedHashMap<>();

    public SoysHttpCommand(HttpOverMcPlugin plugin) {
        this.plugin = plugin;
        // —— 子指令注册（新增在此追加一行即可）——
        registerSubCommand(new EulaSubCommand(plugin));
        registerSubCommand(new StatusSubCommand(plugin));
        registerSubCommand(new ReportSubCommand(plugin));
        registerSubCommand(new ReloadSubCommand(plugin));
        registerSubCommand(new KeySubCommand(plugin));
        registerSubCommand(new ReconnectSubCommand(plugin));
        registerSubCommand(new SendSubCommand(plugin));
        registerSubCommand(new PagesSubCommand(plugin));
        registerSubCommand(new ApiSubCommand(plugin));
        registerSubCommand(new TokensSubCommand(plugin));
        registerSubCommand(new LangSubCommand(plugin));
        registerSubCommand(new LogLevelSubCommand(plugin));
        registerSubCommand(new MigrateSub(plugin));
        registerSubCommand(new SyncSub(plugin));
        // help 作为独立子指令（最后注册，使其在总览中排在末尾；其内部复用本类的 sendUsage）
        registerSubCommand(new HelpSubCommand(plugin, this));
    }

    /** 注册一个子指令（name 自动转小写作为匹配键）。 */
    private void register(SubCommand sub) {
        getSubCommands().put(sub.name().toLowerCase(), sub);
    }

    /** 公开注册入口（第三方插件经门面 {@code getExtension().registerSubCommand(...)} 调用；name 重复时覆盖）。 */
    public void registerSubCommand(SubCommand sub) {
        if (sub == null) return;
        register(sub);
    }

    /** 子指令名 → 实例（门面 / 补全器使用）。 */
    public Map<String, SubCommand> getSubCommands() {
        return subs;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 仅响应 /soyshttp 与简写 /shttp
        if (!command.getName().equalsIgnoreCase("soyshttp") && !command.getName().equalsIgnoreCase("shttp")) {
            return false;
        }
        if (args.length == 0) {
            sendUsage(sender, label, 1);
            return true;
        }
        SubCommand sub = getSubCommands().get(args[0].toLowerCase());
        if (sub == null) {
            msgT(sender, "command.common.unknown-child", "§c未知子指令: {0}", args[0]);
            sendUsage(sender, label, 1);
            return true;
        }
        if (sub.requireOp() && !sender.isOp()) {
            msgT(sender, "command.common.no-op", "§c无权限（需 op）");
            return true;
        }
        sub.execute(sender, label, args);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase();
        if (args.length == 1) {
            // 一级：子指令名 + help（过滤 op 权限 + 前缀）
            List<String> out = new ArrayList<>();
            for (Map.Entry<String, SubCommand> e : getSubCommands().entrySet()) {
                SubCommand sub = e.getValue();
                if (sub.requireOp() && !sender.isOp()) continue;
                if (sub.isHide()) continue;
                if (e.getKey().startsWith(prefix)) out.add(e.getKey());
            }
            return matchByPrefix(args[args.length-1],out);
        }
        // 二级及以后参数：交给对应子指令的 tabComplete（含 help 的二级参数补全）
        SubCommand sub = getSubCommands().get(args[0].toLowerCase());
        if (sub == null || sub.isHide()) return Collections.emptyList();
        if (!(sub.requireOp() && !sender.isOp())) {
            List<String> out = sub.tabComplete(sender, args);
            if (out != null) out.removeIf(s -> !s.toLowerCase().startsWith(prefix));
            return out == null ? Collections.emptyList() : matchByPrefix(args[args.length-1],out);
        }
        return Collections.emptyList();
    }

    /**
     * 汇总输出全部可见子指令（树形：页眉「/{label} -」为根，每行一条「│‑ 名称  说明」分支；
     * 分页：页眉 + 内容 + 页尾，每页默认 {@link StringListUtil#DEFAULT_PAGE_SIZE} 条；
     * 页号自动夹到合法区间，越界不报错）。页眉/页尾格式由 static final 常量统一控制。
     * 隐藏子指令（{@link SubCommand#isHide()}）被跳过；{@code help} 自身作为普通子指令出现在列表末尾。
     * 包级可见，供 {@link HelpSubCommand} 复用。
     */
    void sendUsage(CommandSender sender, String label, int page) {
        if (!sender.isOp()) return; // 非 op 不展示 op 指令
        // 名称列宽：按全部可见条目最大名称长度对齐，跨页保持一致
        List<String[]> rows = new ArrayList<>();
        for (SubCommand sub : getSubCommands().values()) {
            if (sub.isHide()) continue;                      // 隐藏子指令不进帮助列表
            String usage=sub.usage().replaceFirst(sub.usage().substring(sub.usage().lastIndexOf(" —— ")),"");
//            String usage=sub.usage().replaceFirst("/soyshttp "+sub.name()+" —— ","");
            rows.add(new String[]{sub.name(), usage});
        }

        int nameWidth = 0;
        for (String[] row : rows) {
            nameWidth = Math.max(nameWidth, row[0].length());
        }

        List<String> content = new ArrayList<>();
        for (String[] row : rows) {
            content.add(I18n.t(BRANCH_PREFIX, "§7        │- ") + row[0] + spaces(nameWidth - row[0].length() + 2) + "§8" + row[1]);
        }

        String header = I18n.t(HELP_HEADER_KEY, "/{0} -│", label);
        int totalItems = rows.size();
        int size = StringListUtil.DEFAULT_PAGE_SIZE;
        int cur = Math.max(1, Math.min(page, Math.max(1, (totalItems + size - 1) / size)));
        String footer = I18n.t(HELP_FOOTER_KEY,
                "  §7· 第 {0}/{1} 页 · 共 {2} 条 · /{3} help <页码> 翻页 · /{3} help <子指令> 看详情 ·",
                cur, Math.max(1, (totalItems + size - 1) / size), totalItems, label);
        for (String line : StringListUtil.page(header, content, footer, cur, size).lines) {
            sender.sendMessage(line);
        }
    }

    /** 树形分支前缀（与页眉「§7/{label} -」同组 §7；后端说明用 §8 已在拼装时区分）。 */
    private static final String BRANCH_PREFIX = "command.common.help-prefix";

    /** 页眉 i18n 键（树根「/{label} -」，{0} 为命令标签）。 */
    private static final String HELP_HEADER_KEY = "command.common.help-title";
    /** 页尾 i18n 键（帮助分页展露时统一静态控制）。 */
    private static final String HELP_FOOTER_KEY = "command.common.help-footer";

    private static String spaces(int n) {
        if (n <= 0) return "";
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) sb.append(' ');
        return sb.toString();
    }

    private void msg(CommandSender sender, String text) {
        sender.sendMessage("§a[SOYSHTTPOverMC]§r " + text);
    }

    /** 按 i18n 键翻译后发送（带统一前缀）：key 缺失回退 {@code fallback}，占位符用 {@code args} 替换。 */
    private void msgT(CommandSender sender, String key, String fallback, Object... args) {
        msg(sender, I18n.t(key, fallback, args));
    }
}
