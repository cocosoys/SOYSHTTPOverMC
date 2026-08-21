package soys.ihomepages.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/**
 * 配置源抽象基类：通用「从插件数据目录加载 YAML；缺失时从 jar 资源复制默认模板」逻辑。
 * 子类只需提供数据目录相对路径与 jar 内默认模板资源路径。
 */
public abstract class AbstractConfigSource {

    protected final JavaPlugin plugin;
    protected final String dataRelative;    // 数据目录下的相对路径，如 ihomepages/home.yml
    protected final String defaultResource;  // jar 内默认模板资源路径，如 ihomepages/home.yml
    protected YamlConfiguration config;

    protected AbstractConfigSource(JavaPlugin plugin, String dataRelative, String defaultResource) {
        this.plugin = plugin;
        this.dataRelative = dataRelative;
        this.defaultResource = defaultResource;
    }

    /** 解析数据文件；若不存在则从 jar 资源复制默认模板（仅首次）。 */
    protected File resolveDataFile() {
        File f = new File(plugin.getDataFolder(), dataRelative);
        if (!f.exists()) {
            f.getParentFile().mkdirs();
            String res = defaultResource.startsWith("/") ? defaultResource.substring(1) : defaultResource;
            try (InputStream in = plugin.getClass().getClassLoader().getResourceAsStream(res)) {
                if (in != null) {
                    Files.copy(in, f.toPath());
                } else {
                    f.createNewFile(); // 无默认模板：建空文件，避免反复复制失败
                }
            } catch (IOException e) {
                throw new RuntimeException("无法初始化配置 " + dataRelative, e);
            }
        }
        return f;
    }

    /** 加载（或重载）YamlConfiguration，返回并缓存。 */
    protected YamlConfiguration loadConfig() {
        config = YamlConfiguration.loadConfiguration(resolveDataFile());
        return config;
    }

    public YamlConfiguration getConfig() {
        return config;
    }
}
