package soys.ihomepages.config;


import org.bukkit.plugin.java.JavaPlugin;
import soys.ihomepages.spring.service.IConfigReader;

/**
 * 逻辑配置读取实现：读取 {@code ihomepages/config.yml}。
 * 仅承载后端逻辑开关（enabled / cache-seconds），不含任何展示内容。
 */
public class MainConfigReader extends AbstractConfigSource implements IConfigReader {

    public MainConfigReader(JavaPlugin plugin) {
        super(plugin, "ihomepages/config.yml", "ihomepages/config.yml");
    }

    /** 加载（或重载）逻辑配置。 */
    public void load() {
        loadConfig();
    }

    @Override
    public boolean isEnabled() {
        return config != null && config.getBoolean("enabled", true);
    }

    @Override
    public int cacheSeconds() {
        return config == null ? 0 : config.getInt("cache-seconds", 0);
    }
}
