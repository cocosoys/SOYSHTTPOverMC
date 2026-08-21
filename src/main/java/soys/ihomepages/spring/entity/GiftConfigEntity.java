package soys.ihomepages.spring.entity;

import lombok.Data;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/**
 * 礼包配置实体（由 home.yml 的 {@code gift:} 段解析而来）。
 * 仅承载「逻辑 + 展示」所需字段；{@code material}/{@code cmd} 为服务端执行参数，不进前端 JSON。
 */
@Data
public class GiftConfigEntity {

    private boolean enabled;
    private String title = "";
    private String description = "";
    private boolean showButton = true;
    private String periodMode = "once";
    private String periodStart = "";
    private String periodEnd = "";
    private List<GiftItem> items = new ArrayList<>();
    private List<GiftCommand> commands = new ArrayList<>();

    public GiftConfigEntity(){}

    public static GiftConfigEntity parse(ConfigurationSection sec) {
        if (sec == null) {
            return null;
        }
        GiftConfigEntity e = new GiftConfigEntity();
        e.enabled = sec.getBoolean("enabled", false);
        e.title = sec.getString("title", "");
        e.description = sec.getString("description", "");
        e.showButton = sec.getBoolean("show-button", true);

        ConfigurationSection limit = sec.getConfigurationSection("claim-limit");
        if (limit != null) {
            e.periodMode = limit.getString("mode", "once");
            e.periodStart = limit.getString("start", "");
            e.periodEnd = limit.getString("end", "");
        } else {
            e.periodMode = "once";
        }

        ConfigurationSection itemsSec = sec.getConfigurationSection("items");
        if (itemsSec != null) {
            for (String key : itemsSec.getKeys(false)) {
                ConfigurationSection it = itemsSec.getConfigurationSection(key);
                if (it == null) {
                    continue;
                }
                GiftItem gi = new GiftItem();
                gi.setName(it.getString("name", ""));
                gi.setShow(it.getBoolean("show", true));
                gi.setEnable(it.getBoolean("enable", true));
                gi.setIcon(it.getString("icon", ""));
                gi.setMaterial(it.getString("material", ""));
                gi.setAmount(it.getInt("amount", 1));
                e.items.add(gi);
            }
        }

        ConfigurationSection cmdsSec = sec.getConfigurationSection("commands");
        if (cmdsSec != null) {
            for (String key : cmdsSec.getKeys(false)) {
                ConfigurationSection c = cmdsSec.getConfigurationSection(key);
                if (c == null) {
                    continue;
                }
                GiftCommand gc = new GiftCommand();
                gc.setName(c.getString("name", ""));
                gc.setShow(c.getBoolean("show", true));
                gc.setEnable(c.getBoolean("enable", true));
                gc.setCmd(c.getString("cmd", ""));
                e.commands.add(gc);
            }
        }
        return e;
    }
}
