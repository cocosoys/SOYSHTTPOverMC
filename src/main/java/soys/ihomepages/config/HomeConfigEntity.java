package soys.ihomepages.config;

import org.bukkit.configuration.file.YamlConfiguration;
import soys.ihomepages.spring.entity.GiftConfigEntity;

import java.util.List;
import java.util.Map;

/**
 * 主页内容配置实体（由 home.yml 解析而来）。
 * 通用展示段（page/server/qq/social/...）以原始 {@link YamlConfiguration} 透出供导出器构建 JSON；
 * 礼包段单独解析为类型安全的 {@link GiftConfigEntity}。
 */
public class HomeConfigEntity {

    private final YamlConfiguration config;
    private final GiftConfigEntity gift;

    public HomeConfigEntity(YamlConfiguration config) {
        this.config = config;
        this.gift = GiftConfigEntity.parse(config.getConfigurationSection("gift"));
    }

    public YamlConfiguration raw() {
        return config;
    }

    public GiftConfigEntity getGift() {
        return gift;
    }

    /** 整份配置（含通用展示段）的深拷贝 Map，供导出器序列化为前端 JSON。 */
    public Map<String, Object> toDisplayMap() {
        return config.getValues(true);
    }

    // ===== 类型化便捷访问（导出器 / 控制器复用） =====

    public boolean getUIEnable(String key, boolean def) {
        return config.getBoolean("ui." + key, def);
    }

    public String getString(String path, String def) {
        return config.getString(path, def);
    }

    public int getInt(String path, int def) {
        return config.getInt(path, def);
    }

    public List<String> getStringList(String path) {
        return config.getStringList(path);
    }
}
