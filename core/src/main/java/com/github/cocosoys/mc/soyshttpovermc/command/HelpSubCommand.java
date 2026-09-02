package com.github.cocosoys.mc.soyshttpovermc.command;

import com.github.cocosoys.mc.soyshttpovermc.HttpOverMcPlugin;
import com.github.cocosoys.mc.soyshttpovermc.i18n.I18n;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * /soyshttp help —— 帮助子指令（从 {@link SoysHttpCommand} 内联逻辑抽离，便于单独维护）。
 *
 * <p>职责：
 * <ul>
 *   <li>{@code /soyshttp help}          —— 列出全部可见子指令总览（分页）；</li>
 *   <li>{@code /soyshttp help <页码>}   —— 翻页（如 {@code /soyshttp help 2}）；</li>
 *   <li>{@code /soyshttp help <子指令>} —— 展示该子指令的 {@link SubCommand#detail()} 详细用法。</li>
 * </ul>
 *
 * <p>隐藏子指令（{@link SubCommand#isHide()}）既不出现在总览，也无法通过本子指令按名查询；
 * 但仍可被显式执行（{@code /soyshttp <name>}）。本类复用 {@link SoysHttpCommand#sendUsage} 完成分页渲染。
 */
public class HelpSubCommand extends SubCommand {

    /**
     * 宿主命令执行器（取子指令注册表 + 复用 sendUsage 分页渲染）。
     */
    private final SoysHttpCommand command;

    public HelpSubCommand(HttpOverMcPlugin plugin, SoysHttpCommand command) {
        super(plugin);
        this.command = command;
    }

    @Override
    public String name() {
        return "help";
    }

    /**
     * help 对所有人均可见（含非 op）。
     */
    @Override
    public boolean requireOp() {
        return false;
    }

    @Override
    public String usage() {
        return I18n.t("command.help.usage", "/soyshttp help [子指令|页码] —— 显示本帮助页面");
    }

    @Override
    public String detail() {
        return I18n.t("command.help.detail",
                "显示全部子指令总览（分页），或某子指令的详细用法。\n"
                        + "用法：\n"
                        + "  /soyshttp help            列出全部可用子指令（第 1 页）\n"
                        + "  /soyshttp help <页码>     翻页查看（如 /soyshttp help 2）\n"
                        + "  /soyshttp help <子指令>   查看该子指令的参数 / 示例 / 注意事项\n"
                        + "提示：隐藏子指令（仅注册）不会出现在本列表中，但仍可直接 /soyshttp <子指令> 执行。");
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (args.length > 1) {
            // /soyshttp help <页码> 或 <子指令>
            if (isNumeric(args[1])) {
                command.sendUsage(sender, label, parseIntSafe(args[1]));
                return;
            }
            SubCommand target = command.getSubCommands().get(args[1].toLowerCase());
            if (target == null || target.isHide()) {
                msgT(sender, "command.common.unknown-child", "§c未知子指令: {0}", args[1]);
                command.sendUsage(sender, label, 1);
                return;
            }
            if (target.requireOp() && !sender.isOp()) {
                msgT(sender, "command.common.no-op", "§c无权限（需 op）");
                return;
            }
            SubCommand.sendColored(sender, I18n.t("command.common.detail-title",
                    "§a§l[SOYSHTTPOverMC] §e/soyshttp {0} §f详细用法：", target.name()));
            for (String line : target.detail().split("\n")) {
                SubCommand.sendColored(sender, "  §7" + line);
            }
            return;
        }
        command.sendUsage(sender, label, 1);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        // 仅补全二级参数（子指令名）；前缀过滤交给调度器统一处理
        if (args.length != 2) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, SubCommand> e : command.getSubCommands().entrySet()) {
            SubCommand sub = e.getValue();
            if (sub.requireOp() && !sender.isOp()) continue;
            if (sub.isHide()) continue;
            out.add(e.getKey());
        }
        return out;
    }

    private static boolean isNumeric(String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return true;
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }
}
