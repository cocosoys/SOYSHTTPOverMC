package soys.soyshttpovermc.config;

import lombok.CustomLog;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.logging.Logger;

/**
 * EULA 使用/开发协议操作封装。
 *
 * <p>职责：确保 EULA.yml 已复制到配置目录并读取是否已同意协议、输出未同意时的禁用提示。
 * <b>初始化</b>由 {@link ConfigManager#initEulaConfig} 统一书写，最终在 {@link soys.soyshttpovermc.HttpOverMcPlugin#onEnable}
 * 最早阶段装配调用（未同意则禁用插件）。</p>
 */
@CustomLog
public final class EulaConfig {

    private final boolean accepted;

    private EulaConfig(boolean accepted) {
        this.accepted = accepted;
    }

    /**
     * 确保 EULA.yml 已复制到配置目录并读取是否已同意协议。
     * 首次运行经 {@code saveResource("EULA.yml", false)} 把内置协议（简繁英日韩）复制到配置目录；
     * 之后读取其中的 {@code eula} 标志：{@code eula: true} 视为已同意。读取失败一律按未同意处理。
     */
    public static EulaConfig load(JavaPlugin plugin) {
        try {
            File eulaFile = new File(plugin.getDataFolder(), "EULA.yml");
            if (!eulaFile.isFile() && plugin.getResource("EULA.yml") != null) {
                plugin.saveResource("EULA.yml", false);
            }
            return new EulaConfig(YamlConfiguration.loadConfiguration(eulaFile).getBoolean("eula", false));
        } catch (Throwable t) {
            log.warnT("log.plugin.eula-read-fail", "读取 EULA.yml 失败，按未同意处理: {0}", t.getMessage());
            return new EulaConfig(false);
        }
    }

    /** 是否已同意协议（eula: true）。 */
    public boolean isAccepted() {
        return accepted;
    }

    /** 输出 EULA 未同意时的禁用提示（服务器启动阶段打印，引导用户阅读 EULA.yml 并填写 eula: true）。 */
    public static void promptDisabled(Logger logger) {
        logger.severe("==============================SOYSHTTPOverMC==============================");
        logger.severe("【简体中文】SOYSHTTPOverMC 尚未启用！");
        logger.severe("【简体中文】您尚未同意《使用与开发协议》（EULA）。");
        logger.severe("【简体中文】请阅读 plugins/SOYSHTTPOverMC/EULA.yml 中的协议条款");
        logger.severe("【简体中文】并将 eula: false 改为 eula: true 后重启服务器。");
        logger.severe("【简体中文】郑重提示：禁止使用本插件从事违法犯罪活动，包括但不限于");
        logger.severe("【简体中文】建设/跳转黄赌毒网址、投放恐怖分子言论、破坏他人计算机系统等。");

        logger.severe("【English】SOYSHTTPOverMC is not enabled!");
        logger.severe("【English】You have not agreed to the End‑User License Agreement (EULA).");
        logger.severe("【English】Please read the agreement in plugins/SOYSHTTPOverMC/EULA.yml");
        logger.severe("【English】change eula: false to eula: true then restart your server.");
        logger.severe("【English】Important Notice: Do NOT use this plugin for illegal or criminal activities, including but not limited to");
        logger.severe("【English】hosting/redirecting porn,gambling,drug sites, spreading terrorist content, damaging third‑party computer systems.");

        logger.severe("【繁體中文】SOYSHTTPOverMC 尚未啟用！");
        logger.severe("【繁體中文】您尚未同意《使用與開發協議》（EULA）。");
        logger.severe("【繁體中文】請閱讀 plugins/SOYSHTTPOverMC/EULA.yml 中的協議條款");
        logger.severe("【繁體中文】並將 eula: false 改為 eula: true 後重啟伺服器。");
        logger.severe("【繁體中文】鄭重提示：禁止使用本外掛從事違法犯罪活動，包括但不限於");
        logger.severe("【繁體中文】建立/跳轉黃賭毒網址、發布恐怖主義言論、破壞他人電腦系統等。");

        logger.severe("==============================SOYSHTTPOverMC==============================");
    }
}