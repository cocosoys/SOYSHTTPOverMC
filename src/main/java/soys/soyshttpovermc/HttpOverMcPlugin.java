package soys.soyshttpovermc;

import soys.soyshttpovermc.log.LogKit;

import soys.soyshttpovermc.command.SoysHttpCommand;
import soys.soyshttpovermc.config.ConfigManager;
import soys.soyshttpovermc.event.ApiLifecycleListener;
import soys.soyshttpovermc.event.GatewayEventListener;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.Bukkit;
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
import soys.soyshttpovermc.proxy.ProxyDetector;
import soys.soyshttpovermc.proxy.ProxyPlatform;
import soys.soyshttpovermc.proxy.ServerRegistry;
import soys.soyshttpovermc.proxy.ServerTag;
import soys.soyshttpovermc.cross.CrossServerHub;
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
    /** /soyshttp 命令执行器（第三方插件经门面注册子指令用；onEnable 完成前可能为 null）。 */
    private SoysHttpCommand command;
    /** 当前激活的登录插件提供者（AuthMe 等，经 LoginProviderFactory 选取；null=未接入） */
    private volatile LoginProvider loginProvider;
    /** 前端处理器（/soyshttp reload 后向其热替换登录桥） */
    private WebFrontendHandler webFrontend;
    /** 登录窗口认证服务（/soyshttp reload 后向其热替换登录桥） */
    private AuthServiceImpl authService;
    /** 当前运行拓扑：独立服 / BungeeCord(Waterfall) / Velocity（群组服探测结果，影响 Bot 握手转发兼容与对外地址） */
    private ProxyPlatform proxyPlatform = ProxyPlatform.STANDALONE;
    /** 群组服服务器名（config.yml proxy.server-name；独立服为空） */
    private String serverName = "";
    /** 群组服下 Bot 经代理连接的地址（config.yml proxy.proxy-address，host:port；独立服为空） */
    private String proxyAddress = "";
    /** 群组服服务器标签注册表（本服自注册 + 经 discovery 收集其他子服） */
    private final ServerRegistry serverRegistry = new ServerRegistry();
    /** 跨服枢纽（独立服为 null；群组服下承载中继/服务/响应关联/发现） */
    private CrossServerHub crossHub = null;

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

    /** 主 Bot 隧道（跨服 API 调用经其回环到本服 McMessageHandler 触发中继） */
    public McLink getMcLink() {
        return mcLink;
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
        // 群组服探测：经反射读 spigot.yml/paper.yml 判断是否位于 BungeeCord / Waterfall / Velocity 之后。
        // 影响无头 Bot 的握手转发兼容（后端 bungee:true / Velocity legacy 转发下需附加 host\0ip\0uuid 转发数据）与对外地址选择。
        this.proxyPlatform = ProxyDetector.detect(this);
        LogKit.info("[HTTP-Over-MC] 运行拓扑探测: " + proxyPlatform
                + (proxyPlatform == ProxyPlatform.STANDALONE ? "（独立服，Bot 直连）" : "（群组服，Bot 握手将附加转发兼容）"));
        // 群组服服务器名（config.yml proxy.server-name；仅群组服下用于跨服路由/发现，独立服留空）
        this.serverName = getConfig().getString("proxy.server-name", "");
        // 群组服下 Bot 用户名须全局唯一（BungeeCord 共享单一玩家命名空间，且限制 ≤16 字符、仅 [a-zA-Z0-9_]），
        // 否则多服共用默认 __http_proxy__ 会撞名 → Bot 登录被拒 / 隧道错乱。追加本服名区分（前缀 hpb_ = http-proxy-bot）。
        if (proxyPlatform != ProxyPlatform.STANDALONE && serverName != null && !serverName.isEmpty()) {
            String sName = sanitizeName(serverName);
            String candidate = "hpb_" + sName;
            if (candidate.length() > 16) {
                candidate = candidate.substring(0, 16);
            }
            botUsername = candidate;
            LogKit.info("[HTTP-Over-MC] 群组服模式：Bot 采用全局唯一名 " + botUsername + "（本服=" + serverName + "）");
        }
        // 群组服下 Bot 须经代理(BungeeCord / Velocity)连接，其 BungeeCord 频道 Forward 才能被代理跨服中继；
        // 直连后端的 Bot 不在代理玩家命名空间内，Forward 会被静默丢弃，导致跨服请求/发现全部失效。
        this.proxyAddress = getConfig().getString("proxy.proxy-address", "");
        // Bot 回连的本服地址 = Spigot 的 server-port（同端口方案核心：访问端口 == 服务器端口）
        // mc.host / mc.port 留空时自动从 server.properties 取（见 getMcHost/getMcPort）
        mcHost = getMcHost();
        mcPort = getMcPort();
        snifferEnabled = getConfig().getBoolean("sniffer.enabled", true);
        maxBody = getConfig().getInt("sniffer.max-body-bytes", 8 * 1024 * 1024);
    }

    /** 把任意字符串洗成 MC 合法用户名片段：仅保留 [a-zA-Z0-9_]，其余替换为 _（避免 @ / - / . 等非法字符）。 */
    private static String sanitizeName(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            sb.append((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' ? c : '_');
        }
        return sb.toString();
    }

    /** 本服对外 host：优先 config.yml 的 {@code mc.public-host}（群组服公网覆盖），
     *  否则 {@code mc.host}，再回退 server.properties 的 server-ip → 127.0.0.1。 */
    public String getMcHost() {
        return ConfigManager.resolveMcPublicHost(this,
                getConfig().getString("mc.host", ""), getConfig().getString("mc.public-host", ""));
    }

    /** 本服对外 port：优先 config.yml 的 {@code mc.public-port}（群组服公网覆盖），
     *  否则 {@code mc.port}，再回退 server.properties 的 server-port → 运行期端口。 */
    public int getMcPort() {
        return ConfigManager.resolveMcPublicPort(this,
                getConfig().getInt("mc.port", 0), getConfig().getInt("mc.public-port", 0));
    }

    /** 当前运行拓扑（供其他模块/调试使用）。 */
    public ProxyPlatform getProxyPlatform() {
        return proxyPlatform;
    }

    /** 本服在群组服中的服务器名（独立服为空）。 */
    public String getServerName() {
        return serverName;
    }

    /** 群组服服务器标签注册表（供跨服调用/发现查询）。 */
    public ServerRegistry getServerRegistry() {
        return serverRegistry;
    }

    /** 跨服枢纽（独立服为 null）。 */
    public CrossServerHub getCrossServerHub() {
        return crossHub;
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
        PlayerPermissionService pps = new PlayerPermissionService(this.gateway);
        apiRegistry.setPermissionService(pps);
        // API 访问事件（ApiAccessEvent）的玩家解析器：token/cookie → 玩家名 → 玩家实体（离线 null）
        apiRegistry.setPlayerResolver(pps::subjectOf);
        // 离线 cookie 自动升级（启动路径 setupAuthIntegration 先于本方法执行，bridge 已创建）：
        // 玩家用离线 cookie 登录网页后进游戏，任意 API 请求响应自动附带新在线令牌，无需二次登录
        if (authLoginBridge != null) {
            apiRegistry.setTokenUpgrader(authLoginBridge::upgradeHeadersIfOnline);
        }

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
        if (apiRegistry != null) {
            apiRegistry.setTokenUpgrader(null); // 先清空旧引用（bridge 可能重建/未启用）
        }
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
        // JWT 签名密钥（持久化于 data/token-secret.key）：reload 复用同一密钥 → 已签发令牌不失效
        issuer.setSecret(ConfigManager.loadOrCreateTokenSecret(this));
        authLoginBridge = new AuthLoginBridge(issuer);
        // 离线 cookie 自动升级：浏览器带离线令牌的任意 API 请求，若玩家已进游戏在线，
        // 响应自动附带 Set-Cookie(新在线令牌)+X-Soys-New-Token，无需二次登录
        if (apiRegistry != null) {
            apiRegistry.setTokenUpgrader(authLoginBridge::upgradeHeadersIfOnline);
        }

        // 登录插件抽象工厂：优先用 config.yml 的 auth.login-provider 指定提供者（留空=自动取第一个可用）
        String want = getConfig().getString("auth.login-provider", "");
        loginProvider = (want == null || want.trim().isEmpty())
                ? LoginProviderFactory.active()
                : LoginProviderFactory.get(want.trim());
        if (loginProvider == null || !loginProvider.isAvailable()) {
            LogKit.info("[HTTP-Over-MC] 登录插件提供者不可用"
                    + (want == null || want.trim().isEmpty() ? "" : "（配置 auth.login-provider=" + want + "）")
                    + "：网页登录密码校验不可用；session-token 仍可经 /soyshttp key <subject> 下发");
            loginProvider = null;
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
        // 群组服下 Bot 握手附加转发兼容（bungee:true / Velocity 转发）：让 Bot 能正常进服并注册隧道通道
        bot.setProxyForwarding(proxyPlatform != ProxyPlatform.STANDALONE);
        // 群组服下 Bot 携带所属服务器名（于 ServerTag 中向其他子服广播）
        bot.setServerName(serverName);
        // 群组服下 Bot 经代理连接（hostname\0 转发由代理自行附加），使其 Forward 能被正确跨服中继
        if (proxyPlatform != ProxyPlatform.STANDALONE) {
            bot.setProxyAddress(proxyAddress);
            // 注册 Bot 控制通道：Bot 经代理落在默认服后，由当前服务端代发 BungeeCord Connect 切到本服
            // （BungeeCord 1.x 丢弃客户端直发 Connect，服务端侧 player.sendPluginMessage 可靠）。
            getServer().getMessenger().registerIncomingPluginChannel(this, InternalBot.CHANNEL_BOT_CTL,
                    (ch, player, message) -> handleBotConnectRequest(player, message));
        }
        botManager = new BotManager(this, bot, mcLink, channel, mcHost, mcPort, proxyPlatform != ProxyPlatform.STANDALONE);
        bot.setRawMessageListener(botManager::dispatch);
        bot.connect();
    }

    /**
     * 收到 Bot 经代理落在当前服后发来的 botctl 请求：代其向 BungeeCord 发 Connect 切到目标服。
     * <p>BungeeCord 1.x 会丢弃<b>客户端直发</b>的 BungeeCord 通道 Connect，因此由“当前服务端”侧
     * {@code player.sendPluginMessage(plugin, "BungeeCord", Connect)} 代发（服务端→BungeeCord 透传可靠）。
     * 授权：仅处理本插件 Bot（名称以 {@code hpb_} 开头），避免任意玩家滥用 Connect 切服。</p>
     */
    private void handleBotConnectRequest(org.bukkit.entity.Player player, byte[] message) {
        try {
            if (!player.getName().startsWith("hpb_")) {
                LogKit.warn("[HTTP-Over-MC] 忽略非 Bot 的 botctl 请求: " + player.getName());
                return;
            }
            java.io.DataInputStream in = new java.io.DataInputStream(new java.io.ByteArrayInputStream(message));
            String target = in.readUTF();
            if (target == null || target.isEmpty()) return;
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream out = new java.io.DataOutputStream(bos);
            out.writeUTF("Connect");
            out.writeUTF(target);
            out.flush();
            final byte[] pkt = bos.toByteArray();
            Bukkit.getScheduler().runTask(this, () -> player.sendPluginMessage(this, "BungeeCord", pkt));
            LogKit.info("[HTTP-Over-MC] 已代 Bot(" + player.getName() + ") 发 Connect 切到: " + target);
            // 服务端侧兜底登记跨服监听通道（Connect 切服后本服即目标服，立即生效）
            forceBotListen(player, CrossServerHub.CHANNEL_FWD_REQ);
            forceBotListen(player, CrossServerHub.CHANNEL_FWD_RESP);
            forceBotListen(player, CrossServerHub.CHANNEL_DISCOVERY);
        } catch (Throwable t) {
            LogKit.warn("[HTTP-Over-MC] 处理 botctl 失败: " + t);
        }
    }

    /** 服务端侧强制把 Bot 加入某通道监听（1.12.2 仅 CraftPlayer 提供 addChannel，反射兼容）。
     *  等同 McMessageHandler.ensureListening，但覆盖 fwd/发现通道，确保 BungeeCord 的 Forward 可达本服。 */
    private void forceBotListen(org.bukkit.entity.Player player, String ch) {
        try {
            java.lang.reflect.Method m;
            try {
                m = org.bukkit.entity.Player.class.getMethod("addListeningPluginChannel", String.class);
            } catch (NoSuchMethodException e) {
                m = player.getClass().getMethod("addChannel", String.class);
            }
            m.invoke(player, ch);
            LogKit.info("[HTTP-Over-MC] 已为 Bot 强制登记监听通道 " + ch + " -> " + player.getListeningPluginChannels());
        } catch (Throwable t) {
            LogKit.warn("[HTTP-Over-MC] 无法为 Bot 强制登记通道 " + ch + ": " + t);
        }
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
        // 群组服下创建跨服枢纽（中继/服务/响应关联/发现），并注册转发与发现通道
        if (proxyPlatform != ProxyPlatform.STANDALONE && serverName != null && !serverName.isEmpty()) {
            crossHub = new CrossServerHub(this, mcLink, web, botUsername, serverRegistry);
            crossHub.setLocalServerName(serverName);
            // 自注册本服标签（单服即可验证）
            serverRegistry.register(new ServerTag(serverName, mcHost, mcPort, botUsername));
            getServer().getMessenger().registerOutgoingPluginChannel(this, CrossServerHub.BUNGEECORD_CHANNEL);
            // 关键：BungeeCord 收到 Forward 后把内层载荷重包为 innerChannel+len+data，并经由目标服的
            // “BungeeCord” 通道投递；Spigot(bungeecord:true) 不自动解 Forward，仅把 BungeeCord 通道消息
            // 透传给已注册 listener。故跨服 listener 必须注册在 BungeeCord 通道上（而非 httpproxy:fwd-*），
            // 早期注册在 fwd 通道导致 survival 侧零日志、跨服全部失效。
            getServer().getMessenger().registerIncomingPluginChannel(this, CrossServerHub.BUNGEECORD_CHANNEL, crossHub.listener());
            // 让本服 Bot 监听 BungeeCord 通道（接收端）：BungeeCord 的 Forward 经此通道投递，
            // 不登记则消息被静默丢弃。bot 自身已在握手后 REGISTER BungeeCord，这里服务端兜底确保万无一失。
            bot.addExtraChannel(CrossServerHub.BUNGEECORD_CHANNEL);
            final String botName = botUsername;
            getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
                @org.bukkit.event.EventHandler
                public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) {
                    if (botName.equals(e.getPlayer().getName())) {
                        forceBotListen(e.getPlayer(), CrossServerHub.BUNGEECORD_CHANNEL);
                        LogKit.info("[HTTP-Over-MC] 已为 Bot 强制登记 BungeeCord 监听通道(服务端兜底): " + botName);
                    }
                }
            }, this);
            LogKit.info("[HTTP-Over-MC] 跨服枢纽已启用: 本服=" + serverName + " host=" + mcHost + ":" + mcPort
                    + " bot=" + botUsername);
            startCrossServerDiscovery();
        }
        McMessageHandler handler = new McMessageHandler(this, botUsername, channel, requestScheduler, crossHub);
        getServer().getMessenger().registerOutgoingPluginChannel(this, channel);
        getServer().getMessenger().registerIncomingPluginChannel(this, channel, handler);
        return stats;
    }

    /** 启动群组服发现：自注册 + 经 BungeeCord Forward(ALL) 广播本服标签，并周期性清理过期标签。 */
    private void startCrossServerDiscovery() {
        // 5s 后首发，之后每 30s 续播（覆盖后端陆续上线）
        getServer().getScheduler().runTaskTimer(this, () -> {
            if (crossHub == null) return;
            ServerTag self = new ServerTag(serverName, mcHost, mcPort, botUsername);
            serverRegistry.register(self);
            crossHub.broadcastDiscovery(self);
            serverRegistry.sweep();
        }, 100L, 600L);
    }

    /** 在 Spigot 自身监听端口安装三协议嗅探器（MC / 明文 HTTP / HTTPS）；未启用则跳过。 */
    private void initSniffer(RequestStats stats) {
        if (!snifferEnabled) {
            return;
        }
        HttpMcTranslator translator = new HttpMcTranslator(mcLink, botRuleController);
        translator.setLocalServerName(serverName);
        sniffer = new SocketSniffer(this, translator,
                () -> bot.isReady(), maxBody, stats, gateway, getTlsEngineSupplier());
        sniffer.install();
    }

    /** TLS 服务端引擎供应器（无 TLS 工厂时返回 null）。 */
    private Supplier<SSLEngine> getTlsEngineSupplier() {
        return tlsFactory == null ? null : tlsFactory::newServerEngine;
    }

    /** 装配 /soyshttp 与简写 /shttp 命令（同一执行器 + tab 补全）。 */
    private void initCommand() {
        SoysHttpCommand cmd = new SoysHttpCommand(this);
        this.command = cmd;
        if (getCommand("soyshttp") != null) {
            getCommand("soyshttp").setExecutor(cmd);
            getCommand("soyshttp").setTabCompleter(cmd);
        }
        if (getCommand("shttp") != null) {
            getCommand("shttp").setExecutor(cmd);
            getCommand("shttp").setTabCompleter(cmd);
        }
    }

    /** /soyshttp 命令执行器（第三方插件经门面注册子指令用；onEnable 完成前可能为 null）。 */
    public SoysHttpCommand getCommandExecutor() {
        return command;
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
            PlayerPermissionService pps = new PlayerPermissionService(gateway);
            apiRegistry.setPermissionService(pps);
            apiRegistry.setPlayerResolver(pps::subjectOf);
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
