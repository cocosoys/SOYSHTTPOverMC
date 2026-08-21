package soys.soyshttpovermc.command;
import lombok.CustomLog;

import org.bukkit.command.CommandSender;

import soys.soyshttpovermc.HttpOverMcPlugin;
import soys.soyshttpovermc.i18n.I18n;

import java.util.Collections;
import java.util.List;

/**
 * /soyshttp lang [语言代码] —— 查看当前语言及可用列表，或切换到指定语言。
 * 语言包位于 {@code <dataFolder>/language/<代码>.yml}；切换经 {@link I18n#load} 生效，
 * 影响此后所有 i18n 文本（含日志、异常、Ajax 消息）。
 */
@CustomLog
public class LangSubCommand extends SubCommand {

    public LangSubCommand(HttpOverMcPlugin plugin) {
        super(plugin);
    }

    @Override
    public String name() {
        return "lang";
    }

    @Override
    public String usage() {
        return I18n.t("command.lang.usage", "/soyshttp lang [语言代码] —— 查看/切换语言");
    }

    @Override
    public String detail() {
        return I18n.t("command.lang.detail",
                "/soyshttp lang —— 显示当前语言及可用语言列表。\n"
                + "/soyshttp lang <代码> —— 切换到指定语言（需 language/<代码>.yml 存在）。\n"
                + "例子：/soyshttp lang en_us");
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        List<String> langs = I18n.availableLanguages();
        String current = I18n.languageCode();

        if (args.length < 2) {
            msgT(sender, "command.lang.current", "当前语言：{0}", current);
            msgT(sender, "command.lang.available", "可用语言：{0}", String.join(", ", langs));
            return;
        }
        String code = args[1].toLowerCase();
        if (!langs.contains(code)) {
            msgT(sender, "command.lang.invalid", "§c无效或未安装的语言: {0}（可用: {1}）", code, String.join(", ", langs));
            return;
        }
        if (code.equals(current)) {
            msgT(sender, "command.lang.already", "当前已在用语言: {0}", code);
            return;
        }
        if (I18n.load(code)) {
            plugin.getConfig().set("language", code);
            plugin.saveConfig(); // 持久化语言切换，重启后仍按此语言加载
            log.infoT("command.lang.switched-log", "[lang] 已切换语言为: {0}", code);
            msgT(sender, "command.lang.switched", "已切换语言为：{0}", code);
        } else {
            msgT(sender, "command.lang.fail", "§c切换语言失败: {0}", code);
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            return I18n.availableLanguages();
        }
        return Collections.emptyList();
    }
}