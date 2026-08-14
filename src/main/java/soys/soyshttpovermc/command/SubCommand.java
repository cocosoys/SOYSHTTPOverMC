package soys.soyshttpovermc.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import soys.soyshttpovermc.HttpOverMcPlugin;

import java.util.List;

/**
 * /soyshttp（及简写 /shttp）子指令的规范化抽象基类。
 *
 * <h3>如何新增一个子指令</h3>
 * <ol>
 *   <li>新建一个类继承 {@code SubCommand}，实现以下 4 个方法：
 *     <ul>
 *       <li>{@link #name()} —— 子指令名（小写，如 {@code "reload"}）；</li>
 *       <li>{@link #requireOp()} —— 是否需要 op（默认 true，可覆写）；</li>
 *       <li>{@link #usage()} —— 单行用法提示（如 {@code "/soyshttp reload —— 热重载配置与网关"}）；</li>
 *       <li>{@link #execute(CommandSender, String, String[])} —— 业务逻辑；
 *           {@code args} 为完整命令参数（{@code args[0]} 即子指令名本身），与旧 handler 一致。</li>
 *     </ul>
 *   </li>
 *   <li>在 {@link SoysHttpCommand} 构造器中调用 {@code register(new YourSubCommand(plugin))} 即可，
 *       调度、op 校验、help 聚合均自动完成，无需改动调度器其它代码。</li>
 * </ol>
 *
 * <h3>可用辅助</h3>
 * <ul>
 *   <li>{@link #msg(CommandSender, String)} —— 统一加 {@code [SOYSHTTPOverMC]} 前缀发送消息；</li>
 *   <li>{@link #isTargetToken(String)} / {@link #join(List)} —— 玩家选择器解析与空格拼接（send 子指令用）。</li>
 * </ul>
 */
public abstract class SubCommand {

    /** 插件实例（子类按需取 BotManager / WebRegistry / getMcHost 等）。 */
    protected final HttpOverMcPlugin plugin;

    protected SubCommand(HttpOverMcPlugin plugin) {
        this.plugin = plugin;
    }

    /** 子指令名（小写，用于匹配 {@code args[0]}）。 */
    public abstract String name();

    /** 是否需要 op 权限（默认 true）。 */
    public boolean requireOp() {
        return true;
    }

    /** 单行用法提示（展示在 /soyshttp 无参 help 中）。 */
    public abstract String usage();

    /** 执行逻辑；args 为完整命令参数（args[0] 即子指令名本身）。 */
    public abstract void execute(CommandSender sender, String label, String[] args);

    /**
     * tab 补全（可选覆写）：返回本子指令下一级参数的候选列表（不含 args[0] 前的匹配过滤，
     * 由调度器统一按当前输入前缀过滤）。默认无候选。
     */
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return java.util.Collections.emptyList();
    }

    /** 统一前缀发送消息（[SOYSHTTPOverMC] 前缀绿色）。 */
    protected void msg(CommandSender sender, String text) {
        sender.sendMessage("§a[SOYSHTTPOverMC]§r " + text);
    }

    /** 该 token 是否像“目标玩家”（@ 选择器或在线玩家名）。 */
    protected static boolean isTargetToken(String s) {
        if (s == null || s.isEmpty()) return false;
        if (s.charAt(0) == '@') return true;
        return Bukkit.getPlayerExact(s) != null || Bukkit.getPlayer(s) != null;
    }

    /** 用空格拼接参数列表。 */
    protected static String join(List<String> list) {
        return String.join(" ", list);
    }
}
