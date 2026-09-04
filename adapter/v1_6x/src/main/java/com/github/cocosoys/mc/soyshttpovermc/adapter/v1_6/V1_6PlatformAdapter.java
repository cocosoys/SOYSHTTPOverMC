package com.github.cocosoys.mc.soyshttpovermc.adapter.v1_6;

import com.github.cocosoys.mc.soyshttpovermc.platform.BukkitConfigSection;
import com.github.cocosoys.mc.soyshttpovermc.platform.PlatformBukkitImpl;
import com.github.cocosoys.mc.soyshttpovermc.spi.ConfigSection;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

/**
 * 1.6.x 平台的 {@link com.github.cocosoys.mc.soyshttpovermc.spi.Platform} 覆盖实现
 * （继承 core {@link PlatformBukkitImpl}，仅覆写需要差异化的方法）。
 *
 * <p><b>YAML UTF-8 兼容</b>：1.6.4 的 Bukkit YAML 读写用平台默认编码（Windows 为 GBK），
 * 中文配置乱码（比 1.7.10 更缺 Reader/Writer 显式编码入口）。本模块自实现
 * {@link V1_6YamlIo}（只走全版本安全的 {@code loadFromString}/{@code saveToString} +
 * 显式 UTF-8），并在此覆写 {@code loadYaml(File)} / {@code saveYaml(...)} 接入——
 * 经 core 统一入口 {@code PlatformYaml} 下发的所有 YAML 文件读写（pages.yml /
 * language.yml / EULA.yml / gateway/*.yml 等）都会落在本实现上。</p>
 *
 * <p>返回类型保持 core 的 {@link BukkitConfigSection}（包装 1.6.4 的 YamlConfiguration），
 * 使 core 侧 {@code PlatformYaml.load} 可还原回 Bukkit {@link YamlConfiguration}。</p>
 *
 * <p><b>构造约定</b>：无参构造供 ServiceLoader 实例化（{@code Platforms.find()} 会优先采用本实现，
 * 其插件实例来自基类静态 {@code currentPlugin}，由 core onEnable 写入）。</p>
 */
public class V1_6PlatformAdapter extends PlatformBukkitImpl {

    /**
     * ServiceLoader 无参构造（插件实例经基类 currentPlugin 获取）。
     */
    public V1_6PlatformAdapter() {
        super();
        V1_6JdbcCompat.install();
    }

    public V1_6PlatformAdapter(JavaPlugin plugin) {
        super(plugin);
        V1_6JdbcCompat.install();
    }

    @Override
    public ConfigSection loadYaml(File file) {
        return new BukkitConfigSection(V1_6YamlIo.loadUtf8(file));
    }

    @Override
    public void saveYaml(ConfigSection cfg, File file) throws IOException {
        if (!(cfg instanceof BukkitConfigSection)) {
            throw new IOException("非 Bukkit 配置实现，无法保存: "
                    + (cfg == null ? "null" : cfg.getClass().getName()));
        }
        ConfigurationSection d = ((BukkitConfigSection) cfg).delegate();
        if (d instanceof FileConfiguration) {
            V1_6YamlIo.saveUtf8((YamlConfiguration) d, file);
        } else {
            throw new IOException("底层配置非 FileConfiguration，无法保存: " + d.getClass().getName());
        }
    }
}
