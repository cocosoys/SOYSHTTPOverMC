package soys.thismyhomepages.config;


import org.bukkit.plugin.java.JavaPlugin;

/**
 * 内容配置源实现：读取 {@code thismyhomepages/home.yml} → {@link HomeConfigEntity}。
 */
public class YamlHomeConfigSource extends AbstractConfigSource implements IHomeConfigSource {

    private HomeConfigEntity entity;

    public YamlHomeConfigSource(JavaPlugin plugin) {
        super(plugin, "thismyhomepages/home.yml", "thismyhomepages/home.yml");
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
