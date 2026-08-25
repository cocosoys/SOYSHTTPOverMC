package com.github.cocosoys.mc.soyshttpovermc.command;
import lombok.CustomLog;

import org.bukkit.command.CommandSender;

import com.github.cocosoys.mc.soyshttpovermc.HttpOverMcPlugin;
import com.github.cocosoys.mc.soyshttpovermc.i18n.I18n;
import com.github.cocosoys.mc.soyshttpovermc.i18n.I18n.LanguageSourceInfo;

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
                + "/soyshttp lang sources —— 列出当前全部语言源及其提供的翻译条数。\n"
                + "/soyshttp lang sources on/off <索引> —— 启用/停用指定来源的翻译。\n"
                + "/soyshttp lang sources download <索引> —— 将网络来源翻译下载到本地缓存（离线可用）。\n"
                + "/soyshttp lang sources update <索引> —— 更新本地网络翻译（本地不存在则自动下载）。\n"
                + "/soyshttp lang sources remove <索引> —— 删除本地网络翻译并从内存卸载。\n"
                + "/soyshttp lang sources info <索引> —— 查看来源详细信息。\n"
                + "例子：/soyshttp lang en_us");
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        List<String> langs = I18n.availableLanguages();
        String current = I18n.languageCode();

        // /soyshttp lang sources [on|off <index>]：语言源管理
        if (args.length >= 2 && "sources".equalsIgnoreCase(args[1])) {
            handleSources(sender, args);
            return;
        }

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
            plugin.getLanguageConfig().set("language.current", code);
            plugin.saveLanguageConfig(); // 持久化语言切换到 language.yml，重启后仍按此语言加载
            log.infoT("command.lang.switched-log", "[lang] 已切换语言为: {0}", code);
            msgT(sender, "command.lang.switched", "已切换语言为：{0}", code);
        } else {
            msgT(sender, "command.lang.fail", "§c切换语言失败: {0}", code);
        }
    }

    /** 处理语言源子参数：列出来源或对指定索引执行 on/off/download/update/remove/info。 */
    private void handleSources(CommandSender sender, String[] args) {
        if (args.length < 3) {
            List<LanguageSourceInfo> infos = I18n.languageSourcesInfo();
            if (infos.isEmpty()) {
                msgT(sender, "command.lang.sources-empty", "当前没有已注册的额外语言源");
                return;
            }
            msg(sender, "== " + I18n.t("command.lang.sources-header", "语言源（索引/名称/状态/语言/条数）") + " ==");
        for (LanguageSourceInfo i : infos) {
            String status = i.enabled()
                    ? I18n.t("command.lang.sources-enable-label", "启用")
                    : I18n.t("command.lang.sources-disable-label", "停用");
            String langDisp = i.language().isEmpty() ? "*" : i.language();
            msg(sender, I18n.t("command.lang.sources-line",
                    "[#{0}] {1} {2}（语言:{3}，{4} 条）", i.index(), status, i.name(), langDisp, i.count()));
        }
            msg(sender, I18n.t("command.lang.sources-hint",
                    "使用 /soyshttp lang sources on/off/download/update/remove/info <索引> 管理来源"));
            return;
        }
        String action = args[2].toLowerCase();
        // 无需索引的操作（暂无）—— 所有操作均需索引
        if (args.length < 4) {
            msgT(sender, "command.lang.sources-index", "§c请指定来源索引：/soyshttp lang sources {0} <索引>", action);
            return;
        }
        int idx;
        try {
            idx = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            msgT(sender, "command.lang.sources-bad-index", "§c无效索引: {0}", args[3]);
            return;
        }
        switch (action) {
            case "on": {
                if (I18n.setLanguageSourceEnabled(idx, true)) {
                    msgT(sender, "command.lang.sources-enabled", "已启用来源 #{0}", idx);
                } else {
                    msgT(sender, "command.lang.sources-out-of-range", "§c索引越界（当前共 {0} 个来源，有效索引 0~{1}）",
                            I18n.languageSourceCount(), Math.max(0, I18n.languageSourceCount() - 1));
                }
                break;
            }
            case "off": {
                if (I18n.setLanguageSourceEnabled(idx, false)) {
                    msgT(sender, "command.lang.sources-disabled", "已停用来源 #{0}", idx);
                } else {
                    msgT(sender, "command.lang.sources-out-of-range", "§c索引越界（当前共 {0} 个来源，有效索引 0~{1}）",
                            I18n.languageSourceCount(), Math.max(0, I18n.languageSourceCount() - 1));
                }
                break;
            }
            case "download": {
                String result = I18n.downloadNetworkSource(idx);
                msg(sender, result);
                break;
            }
            case "update": {
                String result = I18n.updateNetworkSource(idx);
                msg(sender, result);
                break;
            }
            case "remove": {
                String result = I18n.removeNetworkSourceLocal(idx);
                msg(sender, result);
                break;
            }
            case "info": {
                String result = I18n.networkSourceInfo(idx);
                if (result == null) {
                    msgT(sender, "command.lang.sources-out-of-range", "§c索引越界（当前共 {0} 个来源，有效索引 0~{1}）",
                            I18n.languageSourceCount(), Math.max(0, I18n.languageSourceCount() - 1));
                } else {
                    for (String line : result.split("\n")) {
                        msg(sender, line);
                    }
                }
                break;
            }
            default:
                msgT(sender, "command.lang.sources-unknown",
                        "§c未知操作: {0}（可用 on/off/download/update/remove/info）", args[2]);
                break;
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            List<String> c = new java.util.ArrayList<>(I18n.availableLanguages());
            c.add("sources");
            return c;
        }
        if (args.length == 3 && "sources".equalsIgnoreCase(args[1])) {
            return java.util.Arrays.asList("on", "off", "download", "update", "remove", "info");
        }
        return Collections.emptyList();
    }
}