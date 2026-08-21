package soys.ihomepages.spring.controller;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import soys.soyshttpovermc.annotations.*;
import soys.soyshttpovermc.api.ApiRequestContext;
import soys.soyshttpovermc.util.AjaxResult;
import soys.ihomepages.config.HomeConfigEntity;
import soys.ihomepages.spring.service.IGiftService;
import soys.ihomepages.config.IHomeConfigExporter;
import soys.ihomepages.config.IHomeConfigSource;
import soys.ihomepages.spring.entity.LiveData;

/**
 * 自定义主页接口控制器（仿 Spring MVC 注解式）：
 * <ul>
 *   <li>{@code GET /api/homepage/config} —— 返回 home.yml 的「仅展示」安全 JSON（前端渲染用）；</li>
 *   <li>{@code GET /api/homepage/live} —— 返回实时数据（在线人数等，独立端点可缓存）；</li>
 *   <li>{@code POST /api/homepage/gift/claim} —— 领取礼包（需登录凭证）；</li>
 *   <li>{@code GET /api/homepage/gift/status} —— 查询礼包领取状态（需登录凭证）。</li>
 * </ul>
 * config / live 标注 {@code @ApiPublic} 且已在 auth.yml exempt 中豁免，浏览器无需凭证即可访问；
 * gift/claim / gift/status 受网关保护（需玩家会话凭证）。
 */
@RequestMapping("/homepage")
public class HomeApiController {

    private final IHomeConfigSource home;
    private final IHomeConfigExporter exporter;
    private final IGiftService gift;
    private final JavaPlugin plugin;

    public HomeApiController(IHomeConfigSource home, IHomeConfigExporter exporter,
                             IGiftService gift, JavaPlugin plugin) {
        this.home = home;
        this.exporter = exporter;
        this.gift = gift;
        this.plugin = plugin;
    }

    @ApiName("首页配置")
    @ApiPublic
    @GetMapping("/config")
    public AjaxResult config() {
        HomeConfigEntity hc = home.get();
        if (hc == null) {
            return AjaxResult.errorT(500, "gift.config.no-config", "主页配置未加载");
        }
        return AjaxResult.success(exporter.export(hc));
    }

    @ApiName("首页实时数据")
    @ApiPublic
    @GetMapping("/live")
    public AjaxResult live() {
        int online = Bukkit.getOnlinePlayers().size();
        return AjaxResult.success(new LiveData(online, Bukkit.getMaxPlayers()));
    }

    @ApiName("首页礼包领取")
    @ApiPublic
    @PostMapping("/gift/claim")
    public AjaxResult claim(ApiRequestContext ctx) {
        return gift.claim(ctx.getSyncPlayer());
    }

    @ApiName("礼包状态查询")
    @ApiPublic
    @GetMapping("/gift/status")
    public AjaxResult giftStatus(ApiRequestContext ctx) {
        return gift.status(ctx.getSyncPlayer());
    }
}
