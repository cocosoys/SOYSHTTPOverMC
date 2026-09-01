package com.github.cocosoys.mc.soyshttpovermc.command;

import com.github.cocosoys.mc.soyshttpovermc.HttpOverMcPlugin;
import com.github.cocosoys.mc.soyshttpovermc.i18n.I18n;
import lombok.CustomLog;
import org.bukkit.command.CommandSender;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * /soyshttp eula —— 逐行展示当前 EULA.yml 的使用/开发协议内容（含 eula 同意标志）。
 */
@CustomLog
public class EulaSubCommand extends SubCommand {

    public EulaSubCommand(HttpOverMcPlugin plugin) {
        super(plugin);
        hide = true;
    }

    @Override
    public String name() {
        return "eula";
    }

    @Override
    public String usage() {
        return I18n.t("command.eula.usage", "/soyshttp eula —— 显示EULA协议内容");
    }

    @Override
    public String detail() {
        return I18n.t("command.eula.detail",
                "/soyshttp eula —— 显示EULA协议内容\n"
                        + "逐行展示 plugins/SOYSHTTPOverMC/EULA.yml 中的《使用与开发协议》条款全文；\n"
                        + "协议同意后需在 EULA.yml 中填写 eula: true 并重启服务器生效。");
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        File eulaFile = new File(plugin.getDataFolder(), "EULA.yml");
        if (!eulaFile.isFile()) {
            msgT(sender, "command.eula.not-found", "§c未找到 EULA.yml（首次启动会自动生成）");
            return;
        }
        sender.sendMessage(I18n.t("command.eula.title", "§a§l[SOYSHTTPOverMC] §7EULA.yml 协议内容："));
        boolean accepted = false;
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(new FileInputStream(eulaFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                sender.sendMessage("  §7" + line.replace("§", "&"));
            }
        } catch (Throwable t) {
            log.warnT("log.command.eula-read-fail", "读取 EULA.yml 失败: {0}", String.valueOf(t));
            msgT(sender, "command.eula.read-fail", "§c读取 EULA.yml 失败: {0}", String.valueOf(t.getMessage()));
            return;
        }
        try {
            accepted = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(eulaFile)
                    .getBoolean("eula", false);
        } catch (Throwable ignored) {
        }
        sender.sendMessage(accepted
                ? I18n.t("command.eula.accepted", "§a当前已同意协议（eula: true）")
                : I18n.t("command.eula.not-accepted", "§e当前尚未同意（eula: false），需改为 true 并重启服务器"));
    }
}