package soys.soyshttpovermc.config;

import lombok.CustomLog;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

/**
 * language.yml 配置文件（独立于 config.yml 的国际化配置：current/rule/sources）操作封装。
 *
 * <p>职责：装载 language.yml（缺失时落内置默认）、提供读取 current/rule/sources、持久化。
 * 仅承载「读/写」操作；把语言源注册进 {@link soys.soyshttpovermc.i18n.I18n} 与加载语言包的<b>初始化</b>
 * 由 {@link ConfigManager#initLanguageConfig} 统一书写，最终在 {@link soys.soyshttpovermc.HttpOverMcPlugin} 装配调用。</p>
 */
@CustomLog
public final class LanguageConfig {

    private final YamlConfiguration cfg;

    private LanguageConfig(YamlConfiguration cfg) {
        this.cfg = cfg;
    }

    /** 装载 language.yml（文件缺失时落内置默认），由 ConfigManager 初始化时调用。 */
    public static LanguageConfig load(JavaPlugin plugin) {
        File f = new File(plugin.getDataFolder(), "language.yml");
        if (!f.isFile() && plugin.getResource("language.yml") != null) {
            plugin.saveResource("language.yml", false);
        }
        return new LanguageConfig(YamlConfiguration.loadConfiguration(f));
    }

    /** 原始配置对象（lang 命令等按路径直接读取）。 */
    public YamlConfiguration raw() {
        return cfg;
    }

    /** 当前语言代码（优先 current，兼容旧键 language；缺省 zh_cn）。 */
    public String current() {
        return cfg.getString("language.current", cfg.getString("language", "zh_cn"));
    }

    /** 语言加载策略（clear / overlay / internationalization；空=按 I18n 默认）。 */
    public String rule() {
        return cfg.getString("language.rule", "");
    }

    /** 额外语言源列表（name/description/language/source 规则列表；可能为 null）。 */
    public java.util.List<?> sources() {
        return cfg.getList("language.sources");
    }

    /** 持久化 language.yml（切换语言 / 修改语言源后调用）。 */
    public void save(JavaPlugin plugin) {
        try {
            cfg.save(new File(plugin.getDataFolder(), "language.yml"));
        } catch (IOException e) {
            log.warnT("log.i18n.save-config-fail", "保存 language.yml 失败: {0}", e.getMessage());
        }
    }
}