package com.github.cocosoys.mc.soyshttpovermc.platform;

import com.github.cocosoys.mc.soyshttpovermc.spi.ConfigSection;
import com.github.cocosoys.mc.soyshttpovermc.spi.Platforms;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

/**
 * 业务侧 YAML 文件读写的统一平台入口。
 *
 * <p>core 的业务类不直接调用 Bukkit 的 {@code YamlConfiguration.loadConfiguration(File)}
 * / {@code save(File)}，而是统一经本类走 {@link Platforms#get()} 的
 * {@code Platform.loadYaml/saveYaml} SPI——这样<b>编码差异（如低版本 UTF-8 乱码）由版本模块
 * 的 Platform 实现接管</b>，core 内不做任何版本判断、不含任何版本兼容逻辑。</p>
 *
 * <p><b>解包约定</b>：版本模块（v1_6x / v1_7x）覆写 {@code loadYaml} 时仍需返回
 * {@link BukkitConfigSection}（core 的实现，包装其版本自读出的 YamlConfiguration），
 * 以保证本类可还原回业务侧使用的 Bukkit {@link YamlConfiguration} 类型。</p>
 */
public final class PlatformYaml {

    private PlatformYaml() {
    }

    /**
     * 经当前平台读取 YAML 文件，返回 Bukkit {@link YamlConfiguration}。
     *
     * <p>文件不存在 / 解析失败时的容错语义由平台实现决定；core 默认实现
     * （PlatformBukkitImpl）与 {@code YamlConfiguration.loadConfiguration(File)} 一致。</p>
     */
    public static YamlConfiguration load(File file) {
        ConfigSection sec = Platforms.get().loadYaml(file);
        if (sec instanceof BukkitConfigSection) {
            ConfigurationSection d = ((BukkitConfigSection) sec).delegate();
            if (d instanceof YamlConfiguration) {
                return (YamlConfiguration) d;
            }
        }
        throw new IllegalStateException("Platform.loadYaml 未返回可解包的 Bukkit 配置: "
                + (sec == null ? "null" : sec.getClass().getName()));
    }

    /**
     * 经当前平台将 Bukkit {@link YamlConfiguration} 保存到文件。
     *
     * @throws IOException 保存失败（与 YamlConfiguration#save(File) 契约一致）
     */
    public static void save(YamlConfiguration cfg, File file) throws IOException {
        Platforms.get().saveYaml(new BukkitConfigSection(cfg), file);
    }
}
