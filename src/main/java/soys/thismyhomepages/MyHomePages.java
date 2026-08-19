package soys.thismyhomepages;

import org.bukkit.plugin.java.JavaPlugin;
import soys.soyshttpovermc.HttpOverMcPlugin;
import soys.soyshttpovermc.api.SoysHttpOverMcApi;
import soys.soyshttpovermc.log.LogKit;
import soys.thismyhomepages.config.IHomeConfigExporter;
import soys.thismyhomepages.config.JsonHomeConfigExporter;
import soys.thismyhomepages.config.MainConfigReader;
import soys.thismyhomepages.config.YamlHomeConfigSource;
import soys.thismyhomepages.spring.controller.HomeApiController;
import soys.thismyhomepages.spring.impl.GiftServiceImpl;
import soys.thismyhomepages.spring.service.IGiftService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 自定义主页插件（内嵌于 SOYSHTTPOverMC，未来可整体抽离为独立插件）。
 *
 * <p>单一入口 {@link #register(JavaPlugin)}：读逻辑配置 → 读内容配置 → 伺服前端 → 注册接口。
 * 所有与 SOYSHTTPOverMC 的耦合仅限本方法，便于未来整体抽取。</p>
 */
public final class MyHomePages {

    private MyHomePages() {
    }

    /** 一键注册自定义主页。host 即 SOYSHTTPOverMC 本体。 */
    public static void register(JavaPlugin host) {
        SoysHttpOverMcApi api = HttpOverMcPlugin.getInstance().getApi();

        // 1) 逻辑配置（仅 enabled 开关等）
        MainConfigReader cfgReader = new MainConfigReader(host);
        cfgReader.load();
        if (!cfgReader.isEnabled()) {
            LogKit.info("[thismyhomepages] 已在 config.yml 中禁用（enabled: false），跳过注册。");
            return;
        }

        // 2) 内容配置（home.yml）
        YamlHomeConfigSource home = new YamlHomeConfigSource(host);
        home.load();

        // 3) 静态前端（dist/index.html 强制覆盖站点首页 /）
        byte[] html = readResource(host, "thismyhomepages/dist/index.html");
        if (html.length > 0) {
            api.getWebPage().registerHome(host, html);
        }

        // 4) 接口（配置 JSON / 实时数据 / 礼包领取）
        IHomeConfigExporter exporter = new JsonHomeConfigExporter();
        IGiftService gift = new GiftServiceImpl(cfgReader, home, host);
        HomeApiController controller = new HomeApiController(home, exporter, gift, host);
        api.getApiRegistration().registerController(controller, host);

        LogKit.info("[thismyhomepages] 已注册自定义主页：/ + /api/homepage/{config,live,gift/claim}");
    }

    /** 从插件 jar 资源读取字节（用于伺服 dist/index.html）。 */
    public static byte[] readResource(JavaPlugin plugin, String path) {
        String res = path.startsWith("/") ? path.substring(1) : path;
        try (InputStream in = plugin.getClass().getClassLoader().getResourceAsStream(res)) {
            if (in == null) {
                return new byte[0];
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) {
                out.write(b, 0, n);
            }
            return out.toByteArray();
        } catch (IOException e) {
            return new byte[0];
        }
    }
}
