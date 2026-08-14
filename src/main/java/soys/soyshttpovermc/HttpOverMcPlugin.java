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
import soys.soyshttpovermc.spring.controller.AuthController;
import soys.soyshttpovermc.spring.impl.AuthServiceImpl;
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
import soys.soyshttpovermc.gateway.policy.auth.bridge.AuthLoginBridge;
import soys.soyshttpovermc.gateway.policy.auth.bridge.provider.AuthMeLoginProvider;
import soys.soyshttpovermc.permission.PlayerPermissionService;
import soys.soyshttpovermc.gateway.policy.auth.bridge.spi.LoginProvider;
import soys.soyshttpovermc.gateway.policy.auth.bridge.spi.LoginProviderContext;
import soys.soyshttpovermc.gateway.policy.auth.bridge.spi.LoginProviderFactory;
import soys.soyshttpovermc.gateway.policy.auth.issuer.CredentialIssuer;
import soys.soyshttpovermc.gateway.policy.auth.issuer.SessionTokenIssuer;

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
    /** 网页登录桥（session-token 颁发器启用时创建；null=未启用） */
    private AuthLoginBridge authLoginBridge;
    /** 当前激活的登录插件提供者（AuthMe 等，经 LoginProviderFactory 选取；null=未接入） */
    private volatile LoginProvider loginProvider;
    /** 前端处理器（/soyshttp reload 后向其热替换登录桥） */
    private WebFrontendHandler webFrontend;
    /** 登录窗口认证服务（/soyshttp reload 后向其热替换登录桥） */
    private AuthServiceImpl authService;

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

    /** 当前网页登录桥（供登录插件提供者动态获取，null=未启用 session-token）。 */
    public AuthLoginBridge getAuthLoginBridge() {
        return authLoginBridge;
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
        // 2.5) 登录插件抽象工厂：配置上下文 + 注册软依赖提供者（AuthMe 存在才加载其 SPI 类，防 NoClassDefFoundError）
        LoginProviderFactory.configure(new LoginProviderContext(this));
        if (getServer().getPluginManager().getPlugin("AuthMe") != null) {
            LoginProviderFactory.register(new AuthMeLoginProvider());
        }
        // 3) 日志门面 + 级别过滤（config.yml 的 log.level，/soyshttp reload 热重载）
        LogKit.init(log, getConfig().getString("log.level", "INFO"));
        // 4) 安全网关（独立配置目录 gateway/）+ TLS 上下文（25564 就地升级，无独立端口）
        rebuildGateway(gatewayDir, log);
        // 4.5) AuthMe 网页登录接入（软依赖）：session-token 启用时建桥，AuthMe 在则建监听
        setupAuthIntegration();
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
        // 玩家权限映射服务：会话令牌 → 玩家 → 游戏内 Bukkit 权限（细粒度）；
        // 未启用会话颁发器时回退开放（见 PlayerPermissionService）。与 auth 开关解耦，始终生效。
        apiRegistry.setPermissionService(new PlayerPermissionService(this.gateway));

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

        // 登录窗口认证 API：登录 / 退出 / 登录信息（AuthServiceImpl 复用 AuthLoginBridge 的
        // AuthMe 密码校验 + session-token 签发/撤销；bridge 为 null 时返回明确的"未启用"错误；
        // 持引用以便 /soyshttp reload 后热替换 bridge）
        authService = new AuthServiceImpl(authLoginBridge);
        apiRegistry.register(new AuthController(authService));

        // 请求分配规则控制器（默认按 X-API-Key 分流 admin/common 逻辑队列）
        botRuleController = new ApiKeyBotRule();
    }

    /**
     * 装配网页登录接入（登录插件 SPI）：
     * <ul>
     *   <li>找到已启用的 session-token 颁发器；未启用则整条流程不启用（提示在 session-token.yml 开启）；</li>
     *   <li>创建 {@link AuthLoginBridge}（登录插件无关的密码校验桥）；</li>
     *   <li>经 {@link LoginProviderFactory#active()} 取当前可用的登录插件提供者（AuthMe 等），
     *       init 注册事件监听/初始化底层句柄，并立即 bind 到 bridge（离线网页登录不依赖真实玩家登录事件）；
     *       无提供者则仅提示（session-token 仍可经 /soyshttp key 下发）。</li>
     * </ul>
     * 本方法必须在 {@link #rebuildGateway} 之后调用（颁发器列表来自网关）。
     */
    private void setupAuthIntegration() {
        authLoginBridge = null;
        if (gateway == null) return;
        SessionTokenIssuer issuer = null;
        for (CredentialIssuer i : gateway.getIssuers()) {
            if (i instanceof SessionTokenIssuer) {
                issuer = (SessionTokenIssuer) i;
                break;
            }
        }
        if (issuer == null) {
            LogKit.info("[HTTP-Over-MC] 未启用 session-token 颁发器，网页登录流程未启用"
                    + "（如需启用，请在 gateway/issuers/session-token.yml 设 enabled: true）");
            return;
        }
        authLoginBridge = new AuthLoginBridge(issuer);

        // 登录插件抽象工厂：取当前可用的登录插件提供者（AuthMe 等；主线程调用，isAvailable 访问插件管理器）
        loginProvider = LoginProviderFactory.active();
        if (loginProvider == null) {
            LogKit.info("[HTTP-Over-MC] 未检测到已接入的登录插件（AuthMe 等）：网页登录密码校验不可用；"
                    + "session-token 仍可经 /soyshttp key <subject> 下发");
            return;
        }
        LoginProviderContext ctx = LoginProviderFactory.context();
        if (ctx != null) {
            loginProvider.init(ctx); // 幂等：注册事件监听 + 主线程初始化底层句柄（仅首次执行）
        }
        // 立即绑定校验器：离线网页登录不依赖真实玩家登录事件，必须随 bridge 创建/重建即绑定
        loginProvider.bind(authLoginBridge);
        LogKit.info("[HTTP-Over-MC] 登录插件已接入: " + loginProvider.name()
                + "（" + loginProvider.displayName() + "），网页登录密码校验可用");
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
                webRoot == null ? null : webRoot.getAbsolutePath(), apiRegistry, webRegistry, authLoginBridge);
        webFrontend = web;
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
        // 网关重建后颁发器实例已换新，重新装配玩家权限映射服务（跟踪最新网关）
        if (apiRegistry != null) {
            apiRegistry.setPermissionService(new PlayerPermissionService(gateway));
        }
        // AuthMe 登录桥重建（持有最新 session-token 颁发器），并热替换到前端处理器与登录窗口认证服务
        setupAuthIntegration();
        if (authService != null) {
            authService.setBridge(authLoginBridge);
        }
        if (webFrontend != null) {
            webFrontend.setAuthBridge(authLoginBridge);
        }
    }

    @Override
    public void onDisable() {
        // 关闭全部登录插件提供者（AuthMe 免登录名单内存清理等）
        LoginProviderFactory.shutdownAll();
        loginProvider = null;
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
