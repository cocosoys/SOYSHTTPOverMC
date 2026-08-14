package soys.soyshttpovermc.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import soys.soyshttpovermc.HttpOverMcPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * /soyshttp 命令执行器（从 {@code HttpOverMcPlugin} 抽离）：
 * 本类仅负责<b>分发</b>与<b>op 校验</b>与<b>help 聚合</b>（tab 补全），具体逻辑下放到各 {@link SubCommand} 子类。
 *
 * <p>当前已注册子指令：
 * <ul>
 *   <li>{@code reload} —— 热重载配置与网关；</li>
 *   <li>{@code key <subject>} —— 为指定主体下发最高权限凭证；</li>
 *   <li>{@code reconnect} —— 主 Bot 重新连接；</li>
 *   <li>{@code send <url|/page> [显示文字] [玩家]} —— 向玩家发送可点击链接；</li>
 *   <li>{@code pages} —— 查看当前已注册的全部网页（含内置）；</li>
 *   <li>{@code tokens} —— 查询所有已颁发的会话令牌；</li>
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
        register(new ReloadSubCommand(plugin));
        register(new KeySubCommand(plugin));
        register(new ReconnectSubCommand(plugin));
        register(new SendSubCommand(plugin));
        register(new PagesSubCommand(plugin));
        register(new TokensSubCommand(plugin));
    }

    /** 注册一个子指令（name 自动转小写作为匹配键）。 */
    private void register(SubCommand sub) {
        subs.put(sub.name().toLowerCase(), sub);
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
            sendUsage(sender);
            return true;
        }
        SubCommand sub = subs.get(args[0].toLowerCase());
        if (sub == null) {
            msg(sender, "§c未知子指令: " + args[0]);
            sendUsage(sender);
            return true;
        }
        if (sub.requireOp() && !sender.isOp()) {
            msg(sender, "§c无权限（需 op）");
            return true;
        }
        sub.execute(sender, label, args);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase();
        if (args.length <= 1) {
            // 一级：子指令名（过滤 op 权限 + 前缀）
            List<String> out = new ArrayList<>();
            for (Map.Entry<String, SubCommand> e : subs.entrySet()) {
                SubCommand sub = e.getValue();
                if (sub.requireOp() && !sender.isOp()) continue;
                if (e.getKey().startsWith(prefix)) out.add(e.getKey());
            }
            return out;
        }
        SubCommand sub = subs.get(args[0].toLowerCase());
        if (sub != null && !(sub.requireOp() && !sender.isOp())) {
            List<String> out = sub.tabComplete(sender, args);
            if (out != null) out.removeIf(s -> !s.toLowerCase().startsWith(prefix));
            return out == null ? Collections.emptyList() : out;
        }
        return Collections.emptyList();
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§a§l[SOYSHTTPOverMC] §f可用子指令：");
        for (SubCommand sub : subs.values()) {
            if (sub.requireOp() && !sender.isOp()) continue; // 非 op 不展示 op 指令
            String usage = sub.usage();
            String cmd = usage;
            String desc = "";
            int idx = usage.indexOf(" —— ");
            if (idx > 0) {
                cmd = usage.substring(0, idx);
                desc = usage.substring(idx + 4);
            }
            sender.sendMessage("  §e" + cmd + " §7" + desc);
        }
        sender.sendMessage("  §7（shttp 为简写命令）");
    }

    private void msg(CommandSender sender, String text) {
        sender.sendMessage("§a[SOYSHTTPOverMC]§r " + text);
    }
}
