package com.github.cocosoys.mc.soyshttpovermc.web.gateway;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

/**
 * 网关配置加载工具：从 gateway/ 目录下独立的 yml 文件读取配置段。
 * 布局：gateway/config.yml（总开关）、gateway/https.yml（HTTPS 设置）、
 * gateway/policies/&lt;name&gt;.yml（每个策略一个文件，文件名即策略名）。
 */
public final class GatewayConfig {

    private GatewayConfig() {
    }

    /** 读取 yml 文件为 ConfigurationSection；文件不存在或内容为空返回 null。 */
    public static ConfigurationSection loadYml(File file) {
        if (file == null || !file.isFile()) return null;
        try {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            return cfg.getKeys(false).isEmpty() ? null : cfg;
        } catch (Exception e) {
            return null;
        }
    }
}
