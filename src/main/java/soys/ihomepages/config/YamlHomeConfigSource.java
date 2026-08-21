package soys.ihomepages.config;


import org.bukkit.plugin.java.JavaPlugin;

/**
 * 内容配置源实现：读取 {@code ihomepages/home.yml} → {@link HomeConfigEntity}。
 */
public class YamlHomeConfigSource extends AbstractConfigSource implements IHomeConfigSource {

    private HomeConfigEntity entity;

    public YamlHomeConfigSource(JavaPlugin plugin) {
        super(plugin, "ihomepages/home.yml", "ihomepages/home.yml");
    }

    @Override
    public void load() {
        entity = new HomeConfigEntity(loadConfig());
    }

    @Override
    public void refresh() {
        entity = new HomeConfigEntity(loadConfig());
    }

    @Override
    public HomeConfigEntity get() {
        if (entity == null) {
            load();
        }
        return entity;
    }
}
