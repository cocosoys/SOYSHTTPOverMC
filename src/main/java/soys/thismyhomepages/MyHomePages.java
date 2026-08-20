package soys.thismyhomepages;
import lombok.CustomLog;

import org.bukkit.plugin.java.JavaPlugin;
import soys.soyshttpovermc.HttpOverMcPlugin;
import soys.soyshttpovermc.api.SoysHttpOverMcApi;
import soys.soyshttpovermc.i18n.I18n;
import soys.soyshttpovermc.log.LogKit;
import soys.soyshttpovermc.web.HomePageResolver;
import soys.soyshttpovermc.web.WebFrontendHandler;
import soys.thismyhomepages.api.HomeApi;
import soys.thismyhomepages.api.impl.HomeApiImpl;
import soys.thismyhomepages.config.IHomeConfigExporter;
import soys.thismyhomepages.config.JsonHomeConfigExporter;
import soys.thismyhomepages.config.MainConfigReader;
import soys.thismyhomepages.config.YamlHomeConfigSource;
import soys.thismyhomepages.homepage.HomepageRegistry;
import soys.thismyhomepages.homepage.HomepageState;
import soys.thismyhomepages.command.HomepageSubCommand;
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
 * 所有与 SOYSHTTPOverMC 的耦合仅限本方法（经 {@code webRegistry::setHomePage} 安装首页、
 * 经 {@code frontendHandler::resolveHomeSource} 复用框架的 web.home 解析语义），便于未来整体抽取。</p>
 *
 * <p>第三方插件经 {@link #getHomeApi()} 获取公开门面，注册/切换/注销首页。</p>
 */
@CustomLog
public final class MyHomePages {

    private MyHomePages() {
    }

    private static volatile HomeApi homeApi;

    /** 公开首页门面：初始化完成后方可用；未初始化或自定义主页被禁用时为 null。 */
    public static HomeApi getHomeApi() {
        return homeApi;
    }

    /** 一键注册自定义主页。host 即 SOYSHTTPOverMC 本体。 */
    public static void register(JavaPlugin host) {
        SoysHttpOverMcApi api = HttpOverMcPlugin.getInstance().getApi();

        // 1) 逻辑配置（仅 enabled 开关等）
        MainConfigReader cfgReader = new MainConfigReader(host);
        cfgReader.load();
        if (!cfgReader.isEnabled()) {
            homeApi = null;
            log.info(I18n.t("mhp.disabled",
                    "[thismyhomepages] 已在 config.yml 中禁用（enabled: false），跳过注册。"));
            return;
        }

        // 2) 内容配置（home.yml）
        YamlHomeConfigSource home = new YamlHomeConfigSource(host);
        home.load();

        // 3) 首页注册（thismyhomepages.homepage）：注册/切换/注销/持久化/指令均在主页模块内完成。
        //    base 仅提供两条原语：webRegistry::setHomePage（安装首页到 GET /）、frontendHandler::resolveHomeSource
        //    （把"相对/绝对/URL"来源解析为字节 = 框架的 web.home 解析语义）。来源型首页经后者复用。
        HttpOverMcPlugin plugin = (host instanceof HttpOverMcPlugin)
                ? (HttpOverMcPlugin) host
                : HttpOverMcPlugin.getInstance();
        WebFrontendHandler frontend = plugin.getFrontendHandler();
        HomepageRegistry.SourceResolver srcResolver = frontend == null ? null : spec -> {
            HomePageResolver.Result r = frontend.resolveHomeSource(spec);
            return r == null ? null : new HomepageRegistry.Resolved(r.bytes, r.contentType);
        };
        HomepageRegistry homepage = new HomepageRegistry(
                plugin.getWebRegistry() == null ? null : plugin.getWebRegistry()::setHomePage,
                srcResolver);

        // 3.0) 公开门面 + 持久化状态
        HomepageState hpState = new HomepageState(host);
        HomeApi apiFacade = new HomeApiImpl(homepage, hpState);
        homeApi = apiFacade;

        // 3.0.1) 内置默认首页（字节型，jar /dist/index.html）
        byte[] html = readResource(host, "thismyhomepages/dist/index.html");
        if (html.length > 0) {
            apiFacade.register("default", host.getName(), html, "text/html; charset=utf-8");
            apiFacade.switchTo("default");
        }

        // 3.1) 恢复持久化的首页选择（thismyhomepages/config.yml）
        String savedHomepage = hpState.readCurrent();
        if (savedHomepage != null && !savedHomepage.isEmpty()) {
            if (!apiFacade.switchTo(savedHomepage)) {
                log.info(I18n.t("log.homepage.persist-nonexistent",
                        "[thismyhomepages] 持久化首页 '{0}' 不存在，保留当前首页", savedHomepage));
            }
        }

        // 3.2) 注册 /soyshttp homepage 子指令（宿主 initCommand 之后再注入，命令方可生效）
        api.getExtension().registerSubCommand(new HomepageSubCommand(plugin, homepage));

        // 4) 接口（配置 JSON / 实时数据 / 礼包领取 / 状态查询）
        IHomeConfigExporter exporter = new JsonHomeConfigExporter(api.getHttpClient(), host.getDataFolder());
        IGiftService gift = new GiftServiceImpl(cfgReader, home, host);
        HomeApiController controller = new HomeApiController(home, exporter, gift, host);
        api.getApiRegistration().registerController(controller, host);

        log.info(I18n.t("mhp.registered",
                "[thismyhomepages] 已注册自定义主页：/ + /api/homepage/{config,live,gift/claim,gift/status}"));
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
