package soys.thismyhomepages.spring.impl;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import soys.soyshttpovermc.orm.YAML;
import soys.soyshttpovermc.util.AjaxResult;
import soys.thismyhomepages.config.HomeConfigEntity;
import soys.thismyhomepages.config.IHomeConfigSource;
import soys.thismyhomepages.spring.entity.GiftClaimRecord;
import soys.thismyhomepages.spring.entity.GiftCommand;
import soys.thismyhomepages.spring.entity.GiftConfigEntity;
import soys.thismyhomepages.spring.entity.GiftItem;
import soys.thismyhomepages.spring.service.IConfigReader;
import soys.thismyhomepages.spring.service.IGiftService;

import java.util.Calendar;

/**
 * 礼包领取服务实现：依据逻辑配置（enabled）与内容配置（home.yml 的 gift 段）处理领取。
 * <ul>
 *   <li>未登录玩家 → 401；功能禁用 / 无礼包 → 403；已领取 → 409；</li>
 *   <li>领取记录落 YAML ORM（{@code data/homepage_gift_claim.yml}）。</li>
 * </ul>
 */
public class GiftServiceImpl implements IGiftService {

    private final IConfigReader config;
    private final IHomeConfigSource home;
    private final JavaPlugin plugin;

    public GiftServiceImpl(IConfigReader config, IHomeConfigSource home, JavaPlugin plugin) {
        this.config = config;
        this.home = home;
        this.plugin = plugin;
    }

    @Override
    public AjaxResult claim(Player player) {
        if (player == null) {
            return AjaxResult.unauthorized("请先进入游戏并登录后再领取礼包");
        }
        if (!config.isEnabled()) {
            return AjaxResult.error(403, "自定义主页功能未启用");
        }
        HomeConfigEntity hc = home.get();
        if (hc == null) {
            return AjaxResult.error(500, "主页配置未加载");
        }
        GiftConfigEntity gift = hc.getGift();
        if (gift == null || !gift.isEnabled()) {
            return AjaxResult.error(403, "当前没有可领取的礼包");
        }
        String mode = gift.getPeriodMode();
        String uuid = player.getUniqueId().toString();
        GiftClaimRecord rec = YAML.Pojo.get(GiftClaimRecord.class, uuid);
        if (rec != null && alreadyClaimed(rec, mode)) {
            return AjaxResult.error(409, "你已经领取过礼包啦");
        }
        // 发放物品 + 执行控制台指令
        grant(player, gift);
        // 落领取记录
        GiftClaimRecord nr = new GiftClaimRecord(uuid, player.getName(),
                String.valueOf(System.currentTimeMillis()), mode);
        YAML.Pojo.insert(nr);
        return AjaxResult.success("领取成功，礼包已发放到你的背包");
    }

    private boolean alreadyClaimed(GiftClaimRecord rec, String mode) {
        if (!mode.equals(rec.getPeriod())) {
            return false; // 周期模式变化视为新的一轮
        }
        long claimed = parseLong(rec.getClaimedAt());
        if (claimed <= 0) {
            return true;
        }
        long now = System.currentTimeMillis();
        if ("daily".equals(mode)) {
            return sameDay(claimed, now);
        } else if ("weekly".equals(mode)) {
            return sameWeek(claimed, now);
        }
        return true; // once
    }

    private void grant(Player p, GiftConfigEntity g) {
        if (g.getItems() != null) {
            for (GiftItem it : g.getItems()) {
                Material m = Material.matchMaterial(it.getMaterial());
                if (m != null && m != Material.AIR) {
                    p.getInventory().addItem(new ItemStack(m, Math.max(1, it.getAmount())));
                }
            }
        }
        if (g.getCommands() != null) {
            for (GiftCommand c : g.getCommands()) {
                String cmd = c.getCmd().replace("{player}", p.getName());
                if (!cmd.isEmpty()) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                }
            }
        }
    }

    private static long parseLong(String s) {
        if (s == null || s.isEmpty()) {
            return 0;
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static boolean sameDay(long a, long b) {
        Calendar ca = Calendar.getInstance();
        ca.setTimeInMillis(a);
        Calendar cb = Calendar.getInstance();
        cb.setTimeInMillis(b);
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR)
                && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR);
    }

    private static boolean sameWeek(long a, long b) {
        Calendar ca = Calendar.getInstance();
        ca.setTimeInMillis(a);
        Calendar cb = Calendar.getInstance();
        cb.setTimeInMillis(b);
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR)
                && ca.get(Calendar.WEEK_OF_YEAR) == cb.get(Calendar.WEEK_OF_YEAR);
    }
}
