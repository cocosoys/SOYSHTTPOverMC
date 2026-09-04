package com.github.cocosoys.mc.soyshttpovermc.command;

import com.github.cocosoys.mc.soyshttpovermc.HttpOverMcPlugin;
import com.github.cocosoys.mc.soyshttpovermc.i18n.I18n;
import com.github.cocosoys.mc.soyshttpovermc.log.LogKit;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.List;

/**
 * /soyshttp log [级别] —— 查看 / 修改当前日志打印等级。
 *
 * <p>无参数展示当前级别；带参数直接切换运行时等级（并持久化到 config.yml 的 log.level，
 * 使 {@code /soyshttp reload} 与重启后保持一致）。等级（由高到低过滤）：
 * {@code OFF > ERROR > WARN > INFO > DEBUG > TRACE}。</p>
 */
public class LogLevelSubCommand extends SubCommand {

    /**
     * 合法等级列表（与 {@link LogKit} 一致，忽略大小写）。
     */
    private static final List<String> LEVELS = Arrays.asList("OFF", "ERROR", "WARN", "INFO", "DEBUG", "TRACE");

    public LogLevelSubCommand(HttpOverMcPlugin plugin) {
        super(plugin);
    }

    @Override
    public String name() {
        return "log";
    }

    @Override
    public String usage() {
        return I18n.t("command.log.usage",
                "/soyshttp log [级别] —— 查看/修改日志打印等级 (OFF/ERROR/WARN/INFO/DEBUG/TRACE)");
    }

    @Override
    public String detail() {
        return I18n.t("command.log.detail",
                "/soyshttp log [级别] —— 查看或修改日志打印等级。\n"
                        + "  无参数        仅展示当前等级。\n"
                        + "  log <级别>    立即切换并持久化到 config.yml 的 log.level（reload 后仍生效）。\n"
                        + "等级: OFF > ERROR > WARN > INFO > DEBUG > TRACE（默认 INFO）。");
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            msgT(sender, "command.log.current", "当前日志等级: §e{0}", LogKit.levelName());
            return;
        }
        String want = args[1].toUpperCase();
        if (!LEVELS.contains(want)) {
            msgT(sender, "command.log.invalid", "§c未知等级: {0}§7，可用: {1}；当前为: §e{2}",
                    args[1], String.join(" / ", LEVELS), LogKit.levelName());
            return;
        }
        LogKit.setLevel(want);
        // 持久化：使 /soyshttp reload 与重启后用同一级别
        plugin.getDelegate().coreConfig().set("log.level", want);
        plugin.getDelegate().saveCoreConfig();
        msgT(sender, "command.log.changed", "日志等级已切换为: §e{0}", want);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) return LEVELS;
        return java.util.Collections.emptyList();
    }
}