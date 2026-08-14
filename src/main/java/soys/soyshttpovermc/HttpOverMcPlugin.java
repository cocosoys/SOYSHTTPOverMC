package soys.soyshttpovermc;

import soys.soyshttpovermc.log.LogKit;

import soys.soyshttpovermc.command.SoysHttpCommand;
import soys.soyshttpovermc.config.ConfigManager;
import soys.soyshttpovermc.event.ApiLifecycleListener;
import soys.soyshttpovermc.event.GatewayEventListener;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import soys.soyshttpovermc.api.SoysHttpOverMcApi;
import soys.soyshttpovermc.api.impl.SoysHttpOverMcApiImpl;
import soys.soyshttpovermc.bot.BotManager;
import soys.soyshttpovermc.web.WebRegistry;
import soys.soyshttpovermc.spring.controller.StatusController;
import soys.soyshttpovermc.spring.controller.SystemController;
import soys.soyshttpovermc.spring.impl.StatusServiceImpl;
import soys.soyshttpovermc.spring.impl.SystemServiceImpl;
import soys.soyshttpovermc.spring.service.IStatusService;
import soys.soyshttpovermc.spring.service.ISystemService;
import soys.soyshttpovermc.bot.InternalBot;
import soys.soyshttpovermc.bot.BotRuleController;
import soys.soyshttpovermc.bot.ApiKeyBotRule;
import soys.soyshttpovermc.gateway.GatewayConfig;
import soys.soyshttpovermc.gateway.GatewayFilter;
import soys.soyshttpovermc.gateway.policy.tls.TlsContextFactory;
import soys.soyshttpovermc.http.HttpMcTranslator;
import soys.soyshttpovermc.link.McLink;
import soys.soyshttpovermc.mc.McMessageHandler;
import soys.soyshttpovermc.mc.RequestScheduler;
import soys.soyshttpovermc.mc.SocketSniffer;
import soys.soyshttpovermc.web.RequestStats;
import soys.soyshttpovermc.web.WebFrontendHandler;

import javax.net.ssl.SSLEngine;
import java.io.File;
import java.util.function.Supplier;
import java.util.logging.Logger;

public class HttpOverMcPlugin extends JavaPlugin {

    private static HttpOverMcPlugin instance;

    private InternalBot bot;
    private McLink mcLink;
    private BotManager botManager;
    private SocketSniffer sniffer;
    private GatewayFilter gateway;
    private TlsContextFactory tlsFactory;
    private ApiRegistry apiRegistry;
    private WebRegistry webRegistry;
    private SoysHttpOverMcApi api;
    private GatewayEventListener gatewayEventListener;
    /** 请求分配规则控制器：决定请求进入哪个逻辑队列（tier） */
    private BotRuleController botRuleController;
    /** 按 Bot 队列优先级处理 API 请求（单物理 Bot 隧道 + 多逻辑队列 + 背压） */
    private RequestScheduler requestScheduler;
    private volatile boolean debugEventsEnabled = false;
    private String channel;
    private String botUsername;
    private String mcHost;
    private int mcPort;
    private boolean snifferEnabled;
    private int maxBody;

    /** 供其他插件获取本插件实例（接入注解式 API / 监听网关事件 / 下发凭证） */
    public static HttpOverMcPlugin getInstance() {
        return instance;
    }

    /** 注解式 API 注册表：其他插件注册 @GetMapping 等注解处理器；非主插件自动加 /plugins/<插件名> 前缀，registerProxy 可强制无前缀 */
    public ApiRegistry getApiRegistry() {
        return apiRegistry;
    }

    /** 网页登记处：其他插件登记新网页（默认 /plugins/<插件名> 前缀，registerProxy* 可强制无前缀） */
    public WebRegistry getWebRegistry() {
        return webRegistry;
    }

    /** 网关策略链（含已启用的凭证颁发器） */
    public GatewayFilter getGateway() {
        return gateway;
    }

    /** 对外集成门面（Facade）：第三方插件接入 HTTP-Over-MC 的统一入口（注册 API / 登记网页 / 凭证 / Bot / HTTP 等） */
    public SoysHttpOverMcApi getApi() {
        return api;
    }

    /** Bot 生命周期与通道调度管理器（门面 Bot 组后端；主 Bot 也由其接管通道分发） */
    public BotManager getBotManager() {
        return botManager;
    }

    /** HTTPS（TLS 引擎）是否可用 */
    public boolean isTlsEnabled() {
        return tlsFactory != null;
    }

    /** 网关事件调试日志是否开启（gateway/config.yml 的 debug-events） */
    public boolean isDebugEventsEnabled() {
        return debugEventsEnabled;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        Logger log = getLogger();

        // 1) 读取核心运行参数（Bot 用户名 / 通道 / 本服地址 / 嗅探开关等）
        loadCoreConfig();
        // 2) 生成 gateway/ 默认配置 + 解析前端资源目录（留空解压 jar 内置面板到配置目录 web/）
        File gatewayDir = ConfigManager.ensureGatewayFiles(this);
        File webRoot = ConfigManager.resolveWebRoot(this, getFile(), getConfig().getString("web.root", ""));
        // 3) 日志门面 + 级别过滤（config.yml 的 log.level，/soyshttp reload 热重载）
        LogKit.init(log, getConfig().getString("log.level", "INFO"));
        // 4) 安全网关（独立配置目录 gateway/）+ TLS 上下文（25564 就地升级，无独立端口）
        rebuildGateway(gatewayDir, log);
        // 5) 注解式 API 框架 + 网页登记 + 事件监听 + 系统级 API
        initApiFramework(gatewayDir, log);
        // 6) 无头 Bot 回环连接本服 + McLink 隧道
        initBot();
        // 6.5) 对外集成门面（聚合 API 注册 / 网页登记 / 凭证 / 日志 / Bot / HTTP，供第三方插件接入）
        initApiImpl();
        // 7) 统计 / 状态 API / 前端处理器 / 通道消息处理（返回统计实例供嗅探器复用）
        RequestStats stats = initFrontend(webRoot);
        // 8) 在 Spigot 自身监听端口安装三协议嗅探器（MC / 明文 HTTP / HTTPS）
        initSniffer(stats);
        // 9) 命令：/soyshttp reload | /soyshttp key <subject>
        initCommand();

        logStartup(webRoot);
    }

    /** 从 config.yml 读取核心运行参数（Bot 用户名 / 通道 / 本服地址 / 嗅探开关与上限）。 */
    private void loadCoreConfig() {
        botUsername = getConfig().getString("bot.username", "__http_proxy__");
        channel = getConfig().getString("channel", "httpproxy:main");
        // Bot 回连的本服地址 = Spigot 的 server-port（同端口方案核心：访问端口 == 服务器端口）
        // mc.host / mc.port 留空时自动从 server.properties 取（见 getMcHost/getMcPort）
        mcHost = getMcHost();
        mcPort = getMcPort();
        snifferEnabled = getConfig().getBoolean("sniffer.enabled", true);
        maxBody = getConfig().getInt("sniffer.max-body-bytes", 8 * 1024 * 1024);
    }

    /** 本服对外 host：config.yml 的 {@code mc.host} 为空时自动取 server.properties 的 server-ip（再回退 127.0.0.1）。 */
    public String getMcHost() {
        return ConfigManager.resolveMcHost(this, getConfig().getString("mc.host", ""));
    }

    /** 本服对外 port：config.yml 的 {@code mc.port} 为空(<=0)时自动取 server.properties 的 server-port（再回退运行期端口）。 */
    public int getMcPort() {
        return ConfigManager.resolveMcPort(this, getConfig().getInt("mc.port", 0));
    }

    /**
     * 初始化注解式 API 框架（仿 Spring：@GetMapping/@ApiName/@ApiPermission + AjaxResult）。
     * 全局前缀 api-prefix 始终生效（与 auth 是否启用解耦），保证 API 地址恒定（如 /api/ping）；
     * 控制器类可写 @RequestMapping("/xxx") 为其下方法统一加类级前缀。同时登记网页登记处、
     * 事件监听器（调试日志 + 插件卸载自动卸载其名下 API/网页）与系统级 API。
     */
    private void initApiFramework(File gatewayDir, Logger log) {
        ConfigurationSection gwCfg = GatewayConfig.loadYml(new File(gatewayDir, "config.yml"));
        String apiPrefix = gwCfg == null ? "/api" : gwCfg.getString("api-prefix", "/api");

        apiRegistry = new ApiRegistry(this, log);
        apiRegistry.setPathPrefix(apiPrefix);

        // 网页登记处：第三方插件登记新网页（默认 /plugins/<插件名> 前缀）
        webRegistry = new WebRegistry(this.getName());

        // 事件监听器：网关事件调试日志 + 插件卸载自动卸载其名下全部注解式 API / 网页
        gatewayEventListener = new GatewayEventListener();
        gatewayEventListener.setDebugEnabled(debugEventsEnabled);
        getServer().getPluginManager().registerEvents(gatewayEventListener, this);
        getServer().getPluginManager().registerEvents(new ApiLifecycleListener(apiRegistry, webRegistry, this), this);

        // 系统 API：装配 SystemServiceImpl → SystemController
        ISystemService systemService = new SystemServiceImpl(mcPort);
        apiRegistry.register(new SystemController(systemService));

        // 请求分配规则控制器（默认按 X-API-Key 分流 admin/common 逻辑队列）
        botRuleController = new ApiKeyBotRule();
    }

    /** 启动无头 Bot 回环连接本服（目标即 Spigot 监听端口），并装配 McLink 隧道。
     *  Bot 的通道分发交由 BotManager 接管（主通道→McLink，其余→自定义监听器）。 */
    private void initBot() {
        bot = new InternalBot(this, botUsername, channel, mcHost, mcPort);
        mcLink = new McLink(bot, channel);
        botManager = new BotManager(this, bot, mcLink, channel, mcHost, mcPort);
        bot.setRawMessageListener(botManager::dispatch);
        bot.connect();
    }

    /** 构造对外集成门面（注入各 registry 与 BotManager），仅在 onEnable 调用一次。 */
    private void initApiImpl() {
        api = new SoysHttpOverMcApiImpl(this, apiRegistry, webRegistry, gateway, botManager);
    }

    /** 装配统计 / 状态 API / 前端处理器 / 通道消息处理；返回统计实例供嗅探器复用。 */
    private RequestStats initFrontend(File webRoot) {
        RequestStats stats = new RequestStats();
        // 状态 API：装配 StatusServiceImpl（持有统计来源）→ StatusController（/api/status 注解式重写）
        IStatusService statusService = new StatusServiceImpl(stats, mcPort, botUsername);
        apiRegistry.register(new StatusController(statusService));

        WebFrontendHandler web = new WebFrontendHandler(
                webRoot == null ? null : webRoot.getAbsolutePath(), apiRegistry, webRegistry);
        // 请求调度器：单物理 Bot + 多逻辑队列（common 512 / admin 128 容量，4 个 worker，admin 优先）
        requestScheduler = new RequestScheduler(this, channel, web, 512, 128, 4);
        McMessageHandler handler = new McMessageHandler(this, botUsername, channel, requestScheduler);
        getServer().getMessenger().registerOutgoingPluginChannel(this, channel);
        getServer().getMessenger().registerIncomingPluginChannel(this, channel, handler);
        return stats;
    }

    /** 在 Spigot 自身监听端口安装三协议嗅探器（MC / 明文 HTTP / HTTPS）；未启用则跳过。 */
    private void initSniffer(RequestStats stats) {
        if (!snifferEnabled) {
            return;
        }
        sniffer = new SocketSniffer(this, new HttpMcTranslator(mcLink, botRuleController),
                () -> bot.isReady(), maxBody, stats, gateway, getTlsEngineSupplier());
        sniffer.install();
    }

    /** TLS 服务端引擎供应器（无 TLS 工厂时返回 null）。 */
    private Supplier<SSLEngine> getTlsEngineSupplier() {
        return tlsFactory == null ? null : tlsFactory::newServerEngine;
    }

    /** 装配 /soyshttp 与简写 /shttp 命令（同一执行器）。 */
    private void initCommand() {
        SoysHttpCommand cmd = new SoysHttpCommand(this);
        if (getCommand("soyshttp") != null) {
            getCommand("soyshttp").setExecutor(cmd);
        }
        if (getCommand("shttp") != null) {
            getCommand("shttp").setExecutor(cmd);
        }
    }

    /** 启动完成日志（汇总端口 / 通道 / 各模块状态）。 */
    private void logStartup(File webRoot) {
        LogKit.info("HTTP-Over-MC 已启动（同端口嗅探 + 前端服务 + 安全网关 + 注解式API）: mc=" + mcHost + ":" + mcPort
                + " 通道=" + channel + " 嗅探器=" + (snifferEnabled ? "开" : "关")
                + " 网关=" + (gateway == null ? "关" : "开")
                + " HTTPS=" + (getTlsEngineSupplier() == null ? "关" : "开")
                + " API注册数=" + (apiRegistry == null ? 0 : apiRegistry.getRoutes().size())
                + " webroot=" + (webRoot == null ? "(jar 内置)" : webRoot.getAbsolutePath())
                + " | 25564 三协议端口：MC / 明文 HTTP / HTTPS");
    }

    /**
     * 从 gateway/ 目录重建网关（策略链 + TLS + debug-events 开关）。
     * 布局：gateway/config.yml（总开关）、gateway/https.yml（HTTPS 设置）、
     * gateway/policies/&lt;name&gt;.yml（每个策略一个文件）。
     */
    private void rebuildGateway(File gatewayDir, Logger log) {
        gateway = null;
        tlsFactory = null;
        ConfigurationSection gwCfg = GatewayConfig.loadYml(new File(gatewayDir, "config.yml"));
        debugEventsEnabled = gwCfg != null && gwCfg.getBoolean("debug-events", false);
        if (gatewayEventListener != null) {
            gatewayEventListener.setDebugEnabled(debugEventsEnabled);
        }
        boolean gatewayEnabled = gwCfg != null && gwCfg.getBoolean("enabled", true);
        if (gatewayEnabled) {
            gateway = new GatewayFilter(log);
            gateway.reload(gatewayDir);
            ConfigurationSection https = GatewayConfig.loadYml(new File(gatewayDir, "https.yml"));
            if (https != null && https.getBoolean("enabled", true)) {
                try {
                    tlsFactory = new TlsContextFactory(log, getDataFolder(), https);
                    tlsFactory.init();
                } catch (Exception e) {
                    LogKit.warn("[HTTP-Over-MC] TLS 初始化失败，HTTPS 功能禁用: " + e.getMessage());
                    tlsFactory = null;
                }
            }
        }
        final Supplier<SSLEngine> tlsEngines = tlsFactory == null ? null : tlsFactory::newServerEngine;
        if (sniffer != null) {
            sniffer.setGateway(gateway);
            sniffer.setTlsEngineSupplier(tlsEngines);
        }
    }

    /** /soyshttp reload：热重载日志级别 + 网关策略与 TLS 配置（gateway/ 目录），无需重启服务器 */
    public void reloadHttpConfig() {
        reloadConfig();
        String levelRaw = getConfig().getString("log.level", "INFO");
        LogKit.setLevel(levelRaw); // 日志级别热重载
        File gatewayDir = ConfigManager.ensureGatewayFiles(this);
        rebuildGateway(gatewayDir, getLogger());
    }

    @Override
    public void onDisable() {
        if (sniffer != null) {
            sniffer.uninstall();
        }
        if (botManager != null) {
            botManager.disconnectAll();
        }
        if (requestScheduler != null) {
            requestScheduler.shutdown();
        }
        if (bot != null) {
            bot.disconnect();
        }
        instance = null;
        LogKit.info("HTTP-Over-MC 已关闭");
    }
}
