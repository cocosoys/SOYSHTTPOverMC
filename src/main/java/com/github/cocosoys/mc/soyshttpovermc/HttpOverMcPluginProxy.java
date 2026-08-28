package com.github.cocosoys.mc.soyshttpovermc;

import com.github.cocosoys.mc.soyshttpovermc.api.event.SoysReadyEvent;
import com.github.cocosoys.mc.soyshttpovermc.config.EulaConfig;
import com.github.cocosoys.mc.soyshttpovermc.config.LanguageConfig;
import com.github.cocosoys.mc.soyshttpovermc.orm.YAML;
import com.github.cocosoys.mc.soyshttpovermc.orm.executor.SqlBackendExecutor;
import com.github.cocosoys.mc.soyshttpovermc.storage.RecordSyncStorage;
import com.github.cocosoys.mc.soyshttpovermc.storage.StorageManager;
import com.github.cocosoys.mc.soyshttpovermc.storage.SyncStorage;
import com.github.cocosoys.mc.soyshttpovermc.storage.impl.YamlStorage;
import com.github.cocosoys.mc.soyshttpovermc.config.PagesConfig;
import com.github.cocosoys.mc.soyshttpovermc.enums.ProxyPlatform;
import com.github.cocosoys.mc.soyshttpovermc.web.*;
import lombok.CustomLog;

import com.github.cocosoys.mc.soyshttpovermc.log.LogKit;

import com.github.cocosoys.mc.soyshttpovermc.command.SoysHttpCommand;
import com.github.cocosoys.mc.soyshttpovermc.config.ConfigManager;
import com.github.cocosoys.mc.soyshttpovermc.event.ApiLifecycleListener;
import com.github.cocosoys.mc.soyshttpovermc.event.GatewayEventListener;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.Bukkit;

import com.github.cocosoys.mc.soyshttpovermc.api.ReloadHttpConfigHandler;
import com.github.cocosoys.mc.soyshttpovermc.api.event.HttpConfigReloadEvent;
import com.github.cocosoys.mc.soyshttpovermc.api.impl.SoysHttpOverMcApiImpl;
import com.github.cocosoys.mc.soyshttpovermc.web.http.handler.bot.manager.BotGuardian;
import com.github.cocosoys.mc.soyshttpovermc.web.http.handler.bot.manager.BotManager;
import com.github.cocosoys.mc.soyshttpovermc.spring.impl.StatusServiceImpl;
import com.github.cocosoys.mc.soyshttpovermc.spring.impl.SystemServiceImpl;
import com.github.cocosoys.mc.soyshttpovermc.spring.controller.StatusController;
import com.github.cocosoys.mc.soyshttpovermc.spring.controller.SystemController;
import com.github.cocosoys.mc.soyshttpovermc.spring.controller.AuthController;
import com.github.cocosoys.mc.soyshttpovermc.spring.impl.AuthServiceImpl;
import com.github.cocosoys.mc.soyshttpovermc.spring.service.IStatusService;
import com.github.cocosoys.mc.soyshttpovermc.spring.service.ISystemService;
import com.github.cocosoys.mc.soyshttpovermc.web.http.handler.bot.manager.InternalBot;
import com.github.cocosoys.mc.soyshttpovermc.web.http.handler.bot.manager.ApiKeyBotRule;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.GatewayConfig;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.GatewayFilter;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.tls.TlsContextFactory;
import com.github.cocosoys.mc.soyshttpovermc.web.http.HttpMcTranslator;
import com.github.cocosoys.mc.soyshttpovermc.web.http.handler.bot.manager.link.McLink;
import com.github.cocosoys.mc.soyshttpovermc.web.http.handler.bot.manager.mc.McMessageHandler;
import com.github.cocosoys.mc.soyshttpovermc.web.http.handler.bot.manager.mc.RequestScheduler;
import com.github.cocosoys.mc.soyshttpovermc.web.http.handler.bot.manager.mc.SocketSniffer;
import com.github.cocosoys.mc.soyshttpovermc.proxy.ProxyDetector;
import com.github.cocosoys.mc.soyshttpovermc.web.http.HttpBackendMode;
import com.github.cocosoys.mc.soyshttpovermc.web.http.HttpRequestHandler;
import com.github.cocosoys.mc.soyshttpovermc.web.http.handler.direct.DirectRequestHandler;
import com.github.cocosoys.mc.soyshttpovermc.web.http.handler.netty.NettyEventLoopRequestHandler;
import com.github.cocosoys.mc.soyshttpovermc.web.http.handler.memory.MemoryQueueRequestHandler;
import com.github.cocosoys.mc.soyshttpovermc.web.http.handler.bot.BotTunnelRequestHandler;
import com.github.cocosoys.mc.soyshttpovermc.web.http.handler.standalone.StandaloneHttpServer;

import com.github.cocosoys.mc.soyshttpovermc.proxy.ServerTag;
import com.github.cocosoys.mc.soyshttpovermc.i18n.I18n;
import com.github.cocosoys.mc.soyshttpovermc.enums.BotHideMode;
import com.github.cocosoys.mc.soyshttpovermc.proxy.cross.CrossServerHub;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.bridge.AuthLoginBridge;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.bridge.provider.AuthMeLoginProvider;
import com.github.cocosoys.mc.soyshttpovermc.permission.PlayerPermissionService;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.bridge.spi.LoginProviderContext;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.bridge.spi.LoginProviderFactory;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.issuer.CredentialIssuer;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.issuer.SessionTokenIssuer;

import javax.net.ssl.SSLEngine;
import java.io.File;
import java.util.function.Supplier;

/**
 * HTTP-Over-MC 上帝代理类：承载原 {@link HttpOverMcPlugin} 的全部业务逻辑（onEnable/onDisable 流程 +
 * 所有初始化 / 配置 / 状态查询方法）。上帝类仅保留字段属性与生命周期入口，
 * 通过 {@link HttpOverMcPlugin#getDelegate()} 获取本代理类实例后委托调用。
 *
 * <p>字段访问约定：所有原直接字段访问统一改为 {@code plugin.getXxx()} / {@code plugin.setXxx()}，
 * 由 {@link HttpOverMcPlugin} 上的 {@code @Getter} + {@code @Setter(AccessLevel.PACKAGE)} 生成。</p>
 */
@CustomLog
public class HttpOverMcPluginProxy {

    private final HttpOverMcPlugin plugin;

    public HttpOverMcPluginProxy(HttpOverMcPlugin plugin) {
        this.plugin = plugin;
    }

    // ===== 生命周期入口（由上帝类 onEnable/onDisable 委托调用） =====

    /** onEnable 业务流程（不含 instance 赋值，由上帝类处理）。 */
    public void onEnable() {
        // 0) EULA 使用/开发协议校验
        plugin.setEulaConfig(ConfigManager.initEulaConfig(plugin));
        if (!plugin.getEulaConfig().isAccepted()) {
            EulaConfig.promptDisabled(plugin.getLogger());
            plugin.getServer().getPluginManager().disablePlugin(plugin);
            return;
        }

        plugin.saveDefaultConfig();

        // 0.5) 国际化：尽早加载语言包
        initLanguageConfig();

        // 1) 读取核心运行参数
        loadCoreConfig();
        // 1.25) pages.yml
        loadPagesConfig();
        // 1.5) 数据贡献
        handleUploadContribution();
        // 2) 生成 gateway/ 默认配置 + 解析前端资源目录
        File gatewayDir = ConfigManager.ensureGatewayFiles(plugin);
        plugin.setWebRootDir(ConfigManager.resolveWebRoot(plugin, plugin.getFileProxy(), webConfig("web.root", "")));
        File webRoot = plugin.getWebRootDir();
        // 2.5) 登录插件抽象工厂
        LoginProviderFactory.configure(new LoginProviderContext(plugin));
        if (plugin.getServer().getPluginManager().getPlugin("AuthMe") != null) {
            LoginProviderFactory.register(new AuthMeLoginProvider());
        }
        // 3) 日志门面 + 级别过滤
        LogKit.init(plugin.getLogger(), plugin.getConfig().getString("log.level", "INFO"));
        // 3.25) 国际化（日志就绪后再次装配）
        initLanguageConfig();
        // 3.5) Web 内容缓存
        initWebCache();
        // 3.6) 请求级拦截器 / CORS 声明注册中心
        plugin.setWebInterceptorRegistry(new WebInterceptorRegistry());
        plugin.setCorsRegistry(new CorsRegistry());
        // 3.7) 跨服同步存储
        initStorage();
        // 3.8) ORM（YAML 后端）装配
        File yamlOrmDir = resolveYamlOrmDir();
        YAML.Pojo.init(yamlOrmDir);
        log.infoT("log.plugin.orm-yaml-ready", "ORM(YAML) 已装配: dataDir={0}", yamlOrmDir);
        // 3.9) ORM（SQL 后端，二期）装配
        SqlBackendExecutor.init(plugin);
        // 4) 安全网关 + TLS 上下文
        rebuildGateway(gatewayDir);
        // 4.5) AuthMe 网页登录接入
        setupAuthIntegration();
        // 5) 注解式 API 框架 + 网页登记 + 事件监听 + 系统级 API
        initApiFramework(gatewayDir);
        // 5.5) 读取 HTTP 后端模式（决定是否初始化 Bot）
        HttpBackendMode backendMode = HttpBackendMode.from(plugin.getConfig().getString("http-backend.mode", "netty-eventloop"));
        // 6) 无头 Bot 回环连接本服 + McLink 隧道（仅 bot-tunnel 模式需要）
        if (backendMode.usesBot()) {
            initBot();
        }
        // 6.5) 对外集成门面
        initApiImpl();

        // 6.75) 静态可打开界面纳入【统一注册通道】
        PagesConfig.Manual.register(plugin, plugin.getWebRegistry());

        // 7) 统计 / 状态 API / 前端处理器 / 通道消息处理
        RequestStats stats = initFrontend(webRoot);
        // 8) 三协议嗅探器
        initSniffer(stats);
        // 9) 命令
        initCommand();
        // 10) 就绪事件
        try {
            plugin.getServer().getPluginManager().callEvent(new SoysReadyEvent(plugin.getApi()));
        } catch (Throwable ignored) {
        }

        logStartup(webRoot);
    }

    /** onDisable 业务流程（不含 instance 清空，由上帝类处理）。 */
    public void onDisable() {
        LoginProviderFactory.shutdownAll();
        plugin.setLoginProvider(null);
        if (plugin.getSniffer() != null) {
            plugin.getSniffer().uninstall();
        }
        if (plugin.getStandaloneServer() != null) {
            try {
                plugin.getStandaloneServer().shutdown();
            } catch (Throwable ignored) {}
        }
        if (plugin.getHttpBackend() != null) {
            try {
                plugin.getHttpBackend().shutdown();
            } catch (Throwable ignored) {}
        }
        HttpBackendMode mode = plugin.getHttpBackendMode();
        if (mode != null && mode.usesBot()) {
            if (plugin.getBotManager() != null) {
                try {
                    plugin.getBotManager().disconnectAll();
                } catch (Throwable t) {
                    log.warnT("log.plugin.disconnect-all-fail", "关闭全部 Bot 连接时出错: {0}", String.valueOf(t));
                }
            }
            if (plugin.getRequestScheduler() != null) {
                plugin.getRequestScheduler().shutdown();
            }
            if (plugin.getBot() != null) {
                try {
                    plugin.getBot().disconnect();
                } catch (Throwable t) {
                    log.warnT("log.plugin.bot-disconnect-fail", "主 Bot 断开时出错: {0}", String.valueOf(t));
                }
            }
        }
        if (plugin.getSyncStorage() != null) {
            try {
                plugin.getSyncStorage().shutdown();
            } catch (Throwable ignored) {
            }
            plugin.setSyncStorage(null);
        }
        if (plugin.getStorageManager() != null) {
            try {
                plugin.getStorageManager().shutdown();
            } catch (Throwable ignored) {
            }
            plugin.setStorageManager(null);
        }
        log.infoT("log.plugin.disabled", "HTTP-Over-MC 已关闭");
    }

    // ===== 配置 / 状态查询（public，外部经 getDelegate() 调用） =====

    /** language.yml 配置对象（国际化：current/rule/sources）；reload 时由 ConfigManager 重新装配。 */
    public org.bukkit.configuration.file.YamlConfiguration getLanguageConfig() {
        LanguageConfig cfg = plugin.getLanguageConfig();
        return cfg == null ? null : cfg.raw();
    }

    /** 持久化 language.yml（切换语言 / 修改语言源后调用）。 */
    public void saveLanguageConfig() {
        LanguageConfig cfg = plugin.getLanguageConfig();
        if (cfg != null) cfg.save(plugin);
    }

    /**
     * 重新装配 pages.yml（web.* 前端资源 + pages.page/auto 手动登记）。缺失时落内置默认。
     */
    public void loadPagesConfig() {
        plugin.setPagesConfig(ConfigManager.initPagesConfig(plugin));
    }

    /** pages.yml 配置对象（web.* 段 / pages 段统一在此；可能为 null=文件落盘失败）。 */
    public org.bukkit.configuration.file.YamlConfiguration getPagesConfig() {
        PagesConfig cfg = plugin.getPagesConfig();
        return cfg == null ? null : cfg.raw();
    }

    /** 读取 pages.yml web.* 配置项（如 web.home / web.root / web.cache.max-bytes）。缺省返回默认值。 */
    public String webConfig(String path, String def) {
        PagesConfig cfg = plugin.getPagesConfig();
        return cfg == null ? def : cfg.web(path, def);
    }

    /**
     * 写入 pages.yml 的 {@code web.home} 并持久化。
     */
    public void setWebHome(String value) {
        if (plugin.getPagesConfig() == null) loadPagesConfig();
        PagesConfig cfg = plugin.getPagesConfig();
        if (cfg != null) cfg.setWebHome(plugin, value);
    }

    /**
     * 注册热重载钩子。
     */
    public void registerReloadHook(ReloadHttpConfigHandler handler) {
        if (handler != null) plugin.getReloadHooks().add(handler);
    }

    /** HTTPS（TLS 引擎）是否可用 */
    public boolean isTlsEnabled() {
        return plugin.getTlsFactory() != null;
    }

    /** 主 Bot（内部回环隧道）是否就绪。 */
    public boolean isBotReady() {
        return plugin.getBot() != null && plugin.getBot().isReady();
    }

    /** 本服存储标识（群组服=server-name；独立服=standalone-&lt;host&gt;:&lt;port&gt;）。 */
    public String storageServerId() {
        if (plugin.getProxyPlatform() != ProxyPlatform.STANDALONE
                && plugin.getServerName() != null && !plugin.getServerName().isEmpty()) {
            return plugin.getServerName();
        }
        return "standalone-" + getMcHost() + ":" + getMcPort();
    }

    /** 手动上报入口（/soyshttp report）。 */
    public void reportContribution() {
        uploadContribution();
    }

    /** /soyshttp reload：热重载日志级别 + 网关策略与 TLS 配置 + 存储后端。 */
    public void reloadHttpConfig() {
        plugin.reloadConfig();
        initLanguageConfig();
        loadPagesConfig();
        String levelRaw = plugin.getConfig().getString("log.level", "INFO");
        LogKit.setLevel(levelRaw);
        initStorage();
        File gatewayDir = ConfigManager.ensureGatewayFiles(plugin);
        rebuildGateway(gatewayDir);
        if (plugin.getApiRegistry() != null) {
            PlayerPermissionService pps = new PlayerPermissionService(plugin.getGateway());
            plugin.getApiRegistry().setPermissionService(pps);
            plugin.getApiRegistry().setPlayerResolver(pps::subjectOf);
        }
        setupAuthIntegration();
        if (plugin.getAuthService() != null) {
            plugin.getAuthService().setBridge(plugin.getAuthLoginBridge());
        }
        if (plugin.getWebRegistry() != null) {
            PagesConfig.Manual.register(plugin, plugin.getWebRegistry());
        }
        if (plugin.getWebFrontend() != null) {
            plugin.getWebFrontend().setHomeSpec(webConfig("web.home", ""));
        }
        for (ReloadHttpConfigHandler h : new java.util.ArrayList<>(plugin.getReloadHooks())) {
            try {
                h.onReload();
            } catch (Throwable t) {
                log.warnT("log.plugin.reload-hook-fail", "热重载钩子执行失败，已跳过: {0}", t.getMessage());
            }
        }
        try {
            plugin.getServer().getPluginManager().callEvent(new HttpConfigReloadEvent());
        } catch (Throwable ignored) {
        }
        PagesConfig.Manual.register(plugin, plugin.getWebRegistry());
    }

    // ===== 本服地址 / 拓扑查询（原 public，有逻辑，移至代理类） =====

    /** 本服对外 host：优先 config.yml 的 {@code mc.public-host}，
     *  否则 {@code mc.host}，再回退 server.properties 的 server-ip → 127.0.0.1。 */
    public String getMcHost() {
        return ConfigManager.resolveMcPublicHost(plugin,
                plugin.getConfig().getString("mc.host", ""), plugin.getConfig().getString("mc.public-host", ""));
    }

    /** 本服对外 port：优先 config.yml 的 {@code mc.public-port}，
     *  否则 {@code mc.port}，再回退 server.properties 的 server-port → 运行期端口。 */
    public int getMcPort() {
        return ConfigManager.resolveMcPublicPort(plugin,
                plugin.getConfig().getInt("mc.port", 0), plugin.getConfig().getInt("mc.public-port", 0));
    }

    // ===== private 初始化方法 =====

    /** 解析 ORM（YAML 后端）实体数据存放目录。 */
    private File resolveYamlOrmDir() {
        String fileCfg = plugin.getConfig().getString("storage.backends.yaml.file", "data");
        File dir = YamlStorage.resolveDir(plugin, fileCfg);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /** 装配 Web 内容缓存（pages.yml web.cache.* / web.large-file-*）。 */
    private void initWebCache() {
        try {
            long cacheMaxBytes = Long.parseLong(webConfig("web.cache.max-bytes", null));
            int cacheMaxEntries = webConfig("web.cache.max-entries", null) == null
                    ? 1024 : Math.max(1, Integer.parseInt(webConfig("web.cache.max-entries", null)));
            long cacheTtlSeconds = Long.parseLong(webConfig("web.cache.ttl-seconds", null));
            java.util.Set<String> pinned = new java.util.LinkedHashSet<>(
                    plugin.getPagesConfig() == null ? java.util.Collections.emptyList()
                            : plugin.getPagesConfig().getStringList("web.cache.pinned"));
            long largeThreshold = webConfig("web.large-file-threshold", null) == null
                    ? cacheMaxBytes : Long.parseLong(webConfig("web.large-file-threshold", null));
            plugin.setLargeFileMaxBytes(webConfig("web.large-file-max-bytes", null) == null
                    ? 128L * 1024 * 1024 : Long.parseLong(webConfig("web.large-file-max-bytes", null)));
            plugin.setLargeFileLoaderRegistry(new LargeFileLoaderRegistry(largeThreshold));
            plugin.setWebContentCache(new WebContentCache(
                    cacheMaxEntries, cacheMaxBytes, cacheTtlSeconds, pinned, plugin.getLargeFileLoaderRegistry()));
            log.infoT("log.plugin.web-cache-ready",
                    "Web 内容缓存已装配: maxBytes={0} maxEntries={1} ttl={2}s pinned={3} largeThreshold={4} largeMax={5}",
                    cacheMaxBytes, cacheMaxEntries, cacheTtlSeconds, pinned, largeThreshold, plugin.getLargeFileMaxBytes());
        } catch (Throwable t) {
            log.warnT("log.plugin.web-cache-fail", "Web 内容缓存装配失败（将按无缓存运行）: {0}", t);
            plugin.setWebContentCache(null);
            plugin.setLargeFileLoaderRegistry(null);
        }
    }

    /** 装配多后端数据存储。 */
    private void initStorage() {
        StorageManager manager = null;
        try {
            manager = new StorageManager(plugin);
            manager.initialize();
        } catch (Throwable t) {
            log.warnT("log.plugin.storage-init-fail", "存储后端初始化失败，降级为内存模式: {0}", t.getMessage());
            manager = null;
        }
        plugin.setStorageManager(manager);
        plugin.setSyncStorage(manager == null ? null : new RecordSyncStorage(manager));

        if (plugin.getSyncStorage() != null) {
            plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
                try {
                    plugin.getSyncStorage().heartbeat(storageServerId(),
                            plugin.getServerName() == null || plugin.getServerName().isEmpty() ? plugin.getName() : plugin.getServerName(),
                            getMcHost(), getMcPort());
                } catch (Throwable ignored) {
                }
            }, 0L, 30L * 20L);
            log.infoT("log.plugin.sync-storage-ready", "跨服同步存储已装配: serverId={0} 主={1}", storageServerId(),
                    manager == null ? "-" : manager.getPrimary().getType().getDisplayName());
        } else {
            log.infoT("log.plugin.storage-memory", "数据存储未启用（内存模式）");
        }
    }

    /** 解析 JWT 密钥。 */
    private byte[] resolveJwtSecret() {
        byte[] local = ConfigManager.loadOrCreateTokenSecret(plugin);
        SyncStorage s = plugin.getSyncStorage();
        if (s != null && s.isAvailable()) {
            byte[] global = s.loadOrCreateJwtSecret(local);
            if (global != null) {
                log.infoT("log.plugin.jwt-secret-mysql", "JWT 密钥来源：MySQL 集中下发（跨服统一，serverId={0}）", storageServerId());
                return global;
            }
            log.warnT("log.plugin.jwt-secret-fallback", "从共享存储读取 JWT 密钥失败，回退本地文件密钥（跨服验签可能不一致）");
        }
        return local;
    }

    /** 数据贡献自动上报（受 upload.enabled 开关控制）。 */
    private void handleUploadContribution() {
        if (!plugin.getConfig().getBoolean("upload.enabled", false)) {
            return;
        }
        uploadContribution();
    }

    private void uploadContribution() {
        final String serverUrl = plugin.getConfig().getString("upload.server", "https://api.cocosoys.com/report");
        final String address = getMcHost() + ":" + getMcPort();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            java.net.HttpURLConnection conn = null;
            try {
                java.net.URL url = new java.net.URL(serverUrl);
                conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestProperty("Content-Type", MimeTypes.forExt("json"));
                String body = "{\"server\":\"" + address + "\"}";
                conn.getOutputStream().write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                int code = conn.getResponseCode();
                log.infoT("log.plugin.upload-done", "数据贡献已上报: {0} -> HTTP {1}", address, code);
            } catch (Throwable t) {
                log.warnT("log.plugin.upload-fail", "数据贡献上报失败（不影响插件运行）: {0}", t.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    /** 装配国际化环境（language.yml）。 */
    private void initLanguageConfig() {
        plugin.setLanguageConfig(ConfigManager.initLanguageConfig(plugin));
    }

    /** 从 config.yml 读取核心运行参数。 */
    private void loadCoreConfig() {
        // 读取 HTTP 后端模式（决定是否加载 Bot 配置）
        HttpBackendMode backendMode = HttpBackendMode.from(plugin.getConfig().getString("http-backend.mode", "netty-eventloop"));
        boolean usesBot = backendMode.usesBot();

        if (usesBot) {
            // bot-tunnel 模式：加载 bot.yml（Bot 配置已从 config.yml 移出）
            org.bukkit.configuration.file.YamlConfiguration botCfg = loadBotConfig();

            plugin.setBotUsername(botCfg.getString("username", "__http_proxy__"));
            plugin.setBotNamePrefix(botCfg.getString("name-prefix", "__bot__"));
            java.util.List<String> ips = botCfg.getStringList("allowed-login-ips");
            plugin.setAllowedLoginIps((ips == null || ips.isEmpty())
                    ? java.util.Collections.singleton("127.0.0.1")
                    : new java.util.LinkedHashSet<>(ips));
            plugin.setBotHideMode(BotHideMode.from(botCfg.getString("hide-mode", BotHideMode.HIDEPLAYER.configName())));
            if (!plugin.getBotUsername().startsWith(plugin.getBotNamePrefix())) {
                log.warnT("log.plugin.bot-name-prefix",
                        "bot.username 不以 bot.name-prefix({0}) 开头：{1}（建议使用前缀命名 bot 专属账号；当前名称仍受 IP 白名单保护，但新账号请使用前缀）",
                        plugin.getBotNamePrefix(), plugin.getBotUsername());
            }
        } else {
            // 非 bot-tunnel 模式：不加载 Bot 配置，设置空值占位
            plugin.setBotUsername("");
            plugin.setBotNamePrefix("");
            plugin.setAllowedLoginIps(java.util.Collections.emptySet());
            plugin.setBotHideMode(BotHideMode.HIDEPLAYER);
        }

        plugin.setChannel(plugin.getConfig().getString("channel", "httpproxy:main"));
        plugin.setProxyPlatform(ProxyDetector.detect(plugin));
        if (usesBot) {
            log.infoT("log.plugin.proxy-topology", "运行拓扑探测: {0}{1}", plugin.getProxyPlatform(),
                    plugin.getProxyPlatform() == ProxyPlatform.STANDALONE
                            ? I18n.t("log.plugin.proxy-standalone-suffix", "（独立服，Bot 直连）")
                            : I18n.t("log.plugin.proxy-bungee-suffix", "（群组服，Bot 握手将附加转发兼容）"));
        } else {
            log.infoT("log.plugin.proxy-topology", "运行拓扑探测: {0}", plugin.getProxyPlatform());
        }
        plugin.setServerName(plugin.getConfig().getString("proxy.server-name", ""));
        if (usesBot && plugin.getProxyPlatform() != ProxyPlatform.STANDALONE
                && plugin.getServerName() != null && !plugin.getServerName().isEmpty()) {
            String sName = sanitizeName(plugin.getServerName());
            int budget = 16 - plugin.getBotNamePrefix().length() - 1;
            if (budget < 1) {
                plugin.setBotUsername(plugin.getBotNamePrefix());
            } else {
                if (sName.length() > budget) sName = sName.substring(0, budget);
                plugin.setBotUsername(plugin.getBotNamePrefix() + "_" + sName);
            }
            log.infoT("log.plugin.bot-unique-name", "群组服模式：Bot 采用全局唯一名 {0}（本服={1}）",
                    plugin.getBotUsername(), plugin.getServerName());
        }
        plugin.setProxyAddress(plugin.getConfig().getString("proxy.proxy-address", ""));
        plugin.setMcHost(getMcHost());
        plugin.setMcPort(getMcPort());
        plugin.setSnifferEnabled(plugin.getConfig().getBoolean("sniffer.enabled", true));
        plugin.setMaxBody(plugin.getConfig().getInt("sniffer.max-body-bytes", 8 * 1024 * 1024));
    }

    /** 把任意字符串洗成 MC 合法用户名片段：仅保留 [a-zA-Z0-9_]，其余替换为 _。 */
    static String sanitizeName(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            sb.append((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' ? c : '_');
        }
        return sb.toString();
    }

    /**
     * 加载 bot.yml 配置文件。如果插件数据目录中不存在，则从 jar 资源中复制默认配置。
     * @return bot.yml 的 YamlConfiguration 对象
     */
    private org.bukkit.configuration.file.YamlConfiguration loadBotConfig() {
        try {
            File botFile = new File(plugin.getDataFolder(), "bot.yml");
            if (!botFile.exists()) {
                plugin.saveResource("bot.yml", false);
            }
            return org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(botFile);
        } catch (Throwable t) {
            log.warnT("log.plugin.bot-config-fail", "加载 bot.yml 失败，使用默认值: {0}", t.getMessage());
            return new org.bukkit.configuration.file.YamlConfiguration();
        }
    }

    /** 初始化注解式 API 框架。 */
    private void initApiFramework(File gatewayDir) {
        ConfigurationSection gwCfg = GatewayConfig.loadYml(new File(gatewayDir, "config.yml"));
        String apiPrefix = gwCfg == null ? "/api" : gwCfg.getString("api-prefix", "/api");

        plugin.setApiRegistry(new ApiRegistry(plugin));
        plugin.getApiRegistry().setPathPrefix(apiPrefix);
        PlayerPermissionService pps = new PlayerPermissionService(plugin.getGateway());
        plugin.getApiRegistry().setPermissionService(pps);
        plugin.getApiRegistry().setPlayerResolver(pps::subjectOf);
        if (plugin.getAuthLoginBridge() != null) {
            plugin.getApiRegistry().setTokenUpgrader(plugin.getAuthLoginBridge()::upgradeHeadersIfOnline);
        }

        plugin.setWebRegistry(new WebRegistry(plugin.getName()));

        plugin.setGatewayEventListener(new GatewayEventListener());
        plugin.getGatewayEventListener().setDebugEnabled(plugin.isDebugEventsEnabled());
        plugin.getServer().getPluginManager().registerEvents(plugin.getGatewayEventListener(), plugin);
        plugin.getServer().getPluginManager().registerEvents(
                new ApiLifecycleListener(plugin.getApiRegistry(), plugin.getWebRegistry(), plugin), plugin);

        ISystemService systemService = new SystemServiceImpl(plugin.getMcPort());
        plugin.getApiRegistry().register(new SystemController(systemService));

        plugin.setAuthService(new AuthServiceImpl(plugin.getAuthLoginBridge()));
        plugin.getApiRegistry().register(new AuthController(plugin.getAuthService()));

        plugin.setBotRuleController(new ApiKeyBotRule());
    }

    /** 装配网页登录接入（登录插件 SPI）。 */
    private void setupAuthIntegration() {
        plugin.setAuthLoginBridge(null);
        if (plugin.getApiRegistry() != null) {
            plugin.getApiRegistry().setTokenUpgrader(null);
        }
        if (plugin.getGateway() == null) return;
        SessionTokenIssuer issuer = null;
        for (CredentialIssuer i : plugin.getGateway().getIssuers()) {
            if (i instanceof SessionTokenIssuer) {
                issuer = (SessionTokenIssuer) i;
                break;
            }
        }
        if (issuer == null) {
            log.infoT("log.plugin.session-token-disabled",
                    "未启用 session-token 颁发器，网页登录流程未启用（如需启用，请在 gateway/issuers/session-token.yml 设 enabled: true）");
            return;
        }
        issuer.setSecret(resolveJwtSecret());
        issuer.setSyncStorage(plugin.getSyncStorage());
        issuer.setServerId(storageServerId());
        plugin.setAuthLoginBridge(new AuthLoginBridge(issuer));
        if (plugin.getApiRegistry() != null) {
            plugin.getApiRegistry().setTokenUpgrader(plugin.getAuthLoginBridge()::upgradeHeadersIfOnline);
        }

        String want = plugin.getGateway() == null ? "" : plugin.getGateway().getLoginProviderName();
        plugin.setLoginProvider((want == null || want.trim().isEmpty())
                ? LoginProviderFactory.active()
                : LoginProviderFactory.get(want.trim()));
        if (plugin.getLoginProvider() == null || !plugin.getLoginProvider().isAvailable()) {
            log.warnT("log.plugin.login-provider-unavailable",
                    "登录插件提供者不可用{0}：网页登录将进入【免密码模式】（仅输入用户名即可签发令牌，令牌权限受 PlayerPermissionService 约束；建议接入 AuthMe 等登录插件）",
                    want == null || want.trim().isEmpty() ? "" : "（配置 login-provider=" + want + "）");
            plugin.setLoginProvider(null);
            return;
        }
        LoginProviderContext ctx = LoginProviderFactory.context();
        if (ctx != null) {
            plugin.getLoginProvider().init(ctx);
        }
        plugin.getLoginProvider().bind(plugin.getAuthLoginBridge());
        log.infoT("log.plugin.login-provider-ready", "登录插件已接入: {0}（{1}），网页登录密码校验可用",
                plugin.getLoginProvider().name(), plugin.getLoginProvider().displayName());
    }

    /** 启动无头 Bot 回环连接本服，并装配 McLink 隧道。 */
    private void initBot() {
        plugin.setBot(new InternalBot(plugin, plugin.getBotUsername(), plugin.getChannel(),
                plugin.getMcHost(), plugin.getMcPort()));
        plugin.setMcLink(new McLink(plugin.getBot(), plugin.getChannel()));
        plugin.getBot().setProxyForwarding(plugin.getProxyPlatform() != ProxyPlatform.STANDALONE);
        plugin.getBot().setServerName(plugin.getServerName());
        if (plugin.getProxyPlatform() != ProxyPlatform.STANDALONE) {
            plugin.getBot().setProxyAddress(plugin.getProxyAddress());
            plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, InternalBot.CHANNEL_BOT_CTL,
                    (ch, player, message) -> handleBotConnectRequest(player, message));
        }
        plugin.setBotManager(new BotManager(plugin, plugin.getBot(), plugin.getMcLink(), plugin.getChannel(),
                plugin.getMcHost(), plugin.getMcPort(), plugin.getProxyPlatform() != ProxyPlatform.STANDALONE));
        plugin.getBot().setRawMessageListener(plugin.getBotManager()::dispatch);
        plugin.getServer().getPluginManager().registerEvents(
                new BotGuardian(plugin.getBotManager(), plugin.getBotNamePrefix(),
                        plugin.getAllowedLoginIps(), plugin.getBotHideMode().configName()), plugin);
        plugin.getBot().setMaxReconnectAttempts(loadBotConfig().getInt("reconnect-attempts", 3));
        int cfgProtocolVersion = loadBotConfig().getInt("protocol-version", -1);
        if (cfgProtocolVersion > 0) {
            plugin.getBot().setProtocolVersion(cfgProtocolVersion);
            log.infoT("log.plugin.bot-protocol-fixed", "Bot 协议版本按配置固定为 {0}（跳过自动探测）", cfgProtocolVersion);
        }
        plugin.getBot().connect();
    }

    /** 收到 Bot 经代理落在当前服后发来的 botctl 请求：代其向 BungeeCord 发 Connect 切到目标服。 */
    private void handleBotConnectRequest(org.bukkit.entity.Player player, byte[] message) {
        try {
            boolean botDedicated = plugin.getBotManager() != null && plugin.getBotManager().isManagedBot(player.getName());
            if (!botDedicated && !player.getName().startsWith(plugin.getBotNamePrefix())) {
                log.warnT("log.plugin.ignore-botctl", "忽略非 Bot 的 botctl 请求: {0}", player.getName());
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
            Bukkit.getScheduler().runTask(plugin, () -> player.sendPluginMessage(plugin, "BungeeCord", pkt));
            log.infoT("log.plugin.bot-connect", "已代 Bot({0}) 发 Connect 切到: {1}", player.getName(), target);
            forceBotListen(player, CrossServerHub.CHANNEL_FWD_REQ);
            forceBotListen(player, CrossServerHub.CHANNEL_FWD_RESP);
            forceBotListen(player, CrossServerHub.CHANNEL_DISCOVERY);
        } catch (Throwable t) {
            log.warnT("log.plugin.botctl-fail", "处理 botctl 失败: {0}", t);
        }
    }

    /** 服务端侧强制把 Bot 加入某通道监听。 */
    private void forceBotListen(org.bukkit.entity.Player player, String ch) {
        try {
            java.lang.reflect.Method m;
            try {
                m = org.bukkit.entity.Player.class.getMethod("addListeningPluginChannel", String.class);
            } catch (NoSuchMethodException e) {
                m = player.getClass().getMethod("addChannel", String.class);
            }
            m.invoke(player, ch);
            log.infoT("log.plugin.force-listen", "已为 Bot 强制登记监听通道 {0} -> {1}", ch, player.getListeningPluginChannels());
        } catch (Throwable t) {
            log.warnT("log.plugin.force-listen-fail", "无法为 Bot 强制登记通道 {0}: {1}", ch, t);
        }
    }

    /** 构造对外集成门面。 */
    private void initApiImpl() {
        plugin.setApi(new SoysHttpOverMcApiImpl(plugin, plugin.getApiRegistry(), plugin.getWebRegistry(),
                plugin.getGateway(), plugin.getBotManager(),
                plugin.getLargeFileLoaderRegistry(), plugin.getCorsRegistry()));
    }

    /** 装配统计 / 状态 API / 前端处理器 / 通道消息处理；返回统计实例供嗅探器复用。 */
    private RequestStats initFrontend(File webRoot) {
        RequestStats stats = new RequestStats();
        IStatusService statusService = new StatusServiceImpl(stats, plugin.getMcPort(), plugin.getBotUsername());
        plugin.getApiRegistry().register(new StatusController(statusService));

        WebFrontendHandler web = new WebFrontendHandler(
                webRoot == null ? null : webRoot.getAbsolutePath(),
                webConfig("web.home", ""),
                plugin.getApiRegistry(), plugin.getWebRegistry(),
                plugin.getWebContentCache(), plugin.getLargeFileMaxBytes(),
                plugin.getCorsRegistry(), plugin.getWebInterceptorRegistry());
        plugin.setWebFrontend(web);

        HttpBackendMode backendMode = HttpBackendMode.from(plugin.getConfig().getString("http-backend.mode", "netty-eventloop"));
        boolean usesBot = backendMode.usesBot();

        if (usesBot) {
            // bot-tunnel 模式：创建 RequestScheduler、CrossServerHub、McMessageHandler
            plugin.setRequestScheduler(new RequestScheduler(plugin, plugin.getChannel(), web, 512, 128, 4));
            if (plugin.getProxyPlatform() != ProxyPlatform.STANDALONE
                    && plugin.getServerName() != null && !plugin.getServerName().isEmpty()) {
                plugin.setCrossHub(new CrossServerHub(plugin, plugin.getMcLink(), web,
                        plugin.getBotUsername(), plugin.getServerRegistry()));
                plugin.getCrossHub().setLocalServerName(plugin.getServerName());
                plugin.getServerRegistry().register(new ServerTag(plugin.getServerName(),
                        plugin.getMcHost(), plugin.getMcPort(), plugin.getBotUsername()));
                plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CrossServerHub.BUNGEECORD_CHANNEL);
                plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin,
                        CrossServerHub.BUNGEECORD_CHANNEL, plugin.getCrossHub().listener());
                plugin.getBot().addExtraChannel(CrossServerHub.BUNGEECORD_CHANNEL);
                final String botName = plugin.getBotUsername();
                plugin.getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
                    @org.bukkit.event.EventHandler
                    public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) {
                        if (botName.equals(e.getPlayer().getName())) {
                            forceBotListen(e.getPlayer(), CrossServerHub.BUNGEECORD_CHANNEL);
                            log.infoT("log.plugin.force-listen-bungee",
                                    "已为 Bot 强制登记 BungeeCord 监听通道(服务端兜底): {0}", botName);
                        }
                    }
                }, plugin);
                log.infoT("log.plugin.cross-hub-ready", "跨服枢纽已启用: 本服={0} host={1}:{2} bot={3}",
                        plugin.getServerName(), plugin.getMcHost(), plugin.getMcPort(), plugin.getBotUsername());
                startCrossServerDiscovery();
            }
            McMessageHandler handler = new McMessageHandler(plugin, plugin.getBotUsername(),
                    plugin.getChannel(), plugin.getRequestScheduler(), plugin.getCrossHub());
            plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, plugin.getChannel());
            plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, plugin.getChannel(), handler);
        } else {
            // 非 bot-tunnel 模式：仅自注册 ServerTag（用于跨服 HTTP 转发的目标地址查找）
            if (plugin.getProxyPlatform() != ProxyPlatform.STANDALONE
                    && plugin.getServerName() != null && !plugin.getServerName().isEmpty()) {
                plugin.getServerRegistry().register(new ServerTag(plugin.getServerName(),
                        plugin.getMcHost(), plugin.getMcPort(), ""));
                log.infoT("log.plugin.cross-http-ready", "跨服 HTTP 转发已启用: 本服={0} host={1}:{2}",
                        plugin.getServerName(), plugin.getMcHost(), plugin.getMcPort());
            }
        }
        return stats;
    }

    /** 启动群组服发现。 */
    private void startCrossServerDiscovery() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (plugin.getCrossHub() == null) return;
            ServerTag self = new ServerTag(plugin.getServerName(),
                    plugin.getMcHost(), plugin.getMcPort(), plugin.getBotUsername());
            plugin.getServerRegistry().register(self);
            plugin.getCrossHub().broadcastDiscovery(self);
            plugin.getServerRegistry().sweep();
        }, 100L, 600L);
    }

    /** 启动 HTTP 服务：同端口嗅探模式安装 SocketSniffer，独立服务器模式启动 StandaloneHttpServer。 */
    private void initSniffer(RequestStats stats) {
        // 读取 HTTP 后端模式
        String modeStr = plugin.getConfig().getString("http-backend.mode", "netty-eventloop");
        HttpBackendMode mode = HttpBackendMode.from(modeStr);
        plugin.setHttpBackendMode(mode);
        log.infoT("log.sniffer.backend-mode", "HTTP 后端模式: {0}", mode.configName());

        // 根据模式创建对应的 HTTP 后端处理器
        HttpRequestHandler handler = createHttpBackend(mode);
        plugin.setHttpBackend(handler);

        if (mode == HttpBackendMode.STANDALONE_SERVER) {
            // 独立服务器模式：启动 StandaloneHttpServer，不安装嗅探器
            initStandaloneServer(handler, stats);
            return;
        }

        if (!plugin.isSnifferEnabled()) {
            return;
        }

        boolean trustProxy = plugin.getConfig().getBoolean("mc.trust-proxy", true);
        int httpConcurrency = Math.max(1, plugin.getConfig().getInt("sniffer.http-concurrency", 4));
        int httpQueue = Math.max(1, plugin.getConfig().getInt("sniffer.http-queue-size", 8));
        int keepAliveIdleSeconds = Math.max(1, plugin.getConfig().getInt("sniffer.keep-alive-idle-seconds", 30));

        // bot-tunnel 模式需要等待 Bot 就绪；其他模式直接就绪
        java.util.function.BooleanSupplier ready = (mode == HttpBackendMode.BOT_TUNNEL)
                ? () -> plugin.getBot() != null && plugin.getBot().isReady()
                : () -> true;

        plugin.setSniffer(new SocketSniffer(plugin, handler,
                ready, plugin.getMaxBody(), stats, plugin.getGateway(),
                getTlsEngineSupplier(), trustProxy, httpConcurrency, httpQueue, keepAliveIdleSeconds));
        plugin.getSniffer().install();
    }

    /** 启动独立 HTTP 服务器（standalone-server 模式）。 */
    private void initStandaloneServer(HttpRequestHandler handler, RequestStats stats) {
        String host = getBackendString("standalone-server", "host", "standalone-host", "0.0.0.0");
        int port = getBackendInt("standalone-server", "port", "standalone-port", 25565);
        int maxBody = Math.max(1024, plugin.getMaxBody());

        StandaloneHttpServer server = new StandaloneHttpServer(
                plugin, handler, plugin.getGateway(), stats,
                host, port, getTlsEngineSupplier(), maxBody);
        plugin.setStandaloneServer(server);
        try {
            server.start();
        } catch (Exception e) {
            log.errorT("log.standalone.start-fail", "独立 HTTP 服务器启动失败: {0}", String.valueOf(e), e);
        }
    }

    /** 根据模式创建对应的 HTTP 后端处理器。配置结构：http-backend.<mode>.<key>（向后兼容旧的扁平结构）。 */
    private HttpRequestHandler createHttpBackend(HttpBackendMode mode) {
        switch (mode) {
            case NETTY_EVENTLOOP: {
                int threads = getBackendInt("netty-eventloop", "threads", "netty-threads", 2);
                return new NettyEventLoopRequestHandler(plugin.getWebFrontend(),
                        plugin.getServerRegistry(), plugin.getServerName(), threads);
            }
            case MEMORY_QUEUE: {
                int capacity = getBackendInt("memory-queue", "capacity", "queue-capacity", 1024);
                int workers = getBackendInt("memory-queue", "workers", "queue-workers", 4);
                return new MemoryQueueRequestHandler(plugin.getWebFrontend(),
                        plugin.getServerRegistry(), plugin.getServerName(), capacity, workers);
            }
            case BOT_TUNNEL: {
                HttpMcTranslator translator = new HttpMcTranslator(plugin.getMcLink(), plugin.getBotRuleController());
                translator.setLocalServerName(plugin.getServerName());
                return new BotTunnelRequestHandler(translator);
            }
            case STANDALONE_SERVER:
            case DIRECT:
            default:
                return new DirectRequestHandler(plugin.getWebFrontend(),
                        plugin.getServerRegistry(), plugin.getServerName());
        }
    }

    /**
     * 读取 HTTP 后端配置，优先使用分层结构 http-backend.<mode>.<key>，
     * 回退到旧的扁平结构 http-backend.<legacyKey>，最后使用默认值。
     */
    private int getBackendInt(String mode, String key, String legacyKey, int def) {
        String layered = "http-backend." + mode + "." + key;
        if (plugin.getConfig().contains(layered)) {
            return plugin.getConfig().getInt(layered, def);
        }
        String legacy = "http-backend." + legacyKey;
        return plugin.getConfig().getInt(legacy, def);
    }

    /** 读取 HTTP 后端字符串配置（分层结构优先，回退旧结构）。 */
    private String getBackendString(String mode, String key, String legacyKey, String def) {
        String layered = "http-backend." + mode + "." + key;
        if (plugin.getConfig().contains(layered)) {
            return plugin.getConfig().getString(layered, def);
        }
        String legacy = "http-backend." + legacyKey;
        return plugin.getConfig().getString(legacy, def);
    }

    /** TLS 服务端引擎供应器（无 TLS 工厂时返回 null）。 */
    private Supplier<SSLEngine> getTlsEngineSupplier() {
        return plugin.getTlsFactory() == null ? null : plugin.getTlsFactory()::newServerEngine;
    }

    /** 装配 /soyshttp 与简写 /shttp 命令。 */
    private void initCommand() {
        SoysHttpCommand cmd = new SoysHttpCommand(plugin);
        plugin.setCommand(cmd);
        if (plugin.getCommand("soyshttp") != null) {
            plugin.getCommand("soyshttp").setExecutor(cmd);
            plugin.getCommand("soyshttp").setTabCompleter(cmd);
        }
        if (plugin.getCommand("shttp") != null) {
            plugin.getCommand("shttp").setExecutor(cmd);
            plugin.getCommand("shttp").setTabCompleter(cmd);
        }
    }

    /** 启动 Banner。 */
    private void printStartupBanner() {
        String[] lines = {
                "█   █ █████ █████ ████       ███  █   █ ████  ████      █   █  ███",
                "█   █   █     █  █   █      █   █ █   █ █     █   █     ██ ██ █",
                "█████   █     █  █████  ─── █   █ █   █ █████ ████  ─── █ █ █ █",
                "█   █   █     █  █          █   █  █ █  █     █ █       █   █ █",
                "█   █   █     █  █           ███    █   █████ █  █      █   █  ███"
        };
        for (String line : lines) {
            log.info(line);
        }
    }

    /** 启动完成日志。 */
    private void logStartup(File webRoot) {
        printStartupBanner();
        log.infoT("log.plugin.startup",
                "HTTP-Over-MC 已启动（同端口嗅探 + 前端服务 + 安全网关 + 注解式API）: mc={0}:{1} 通道={2} 嗅探器={3} 网关={4} HTTPS={5} API注册数={6} webroot={7} | {8} 三协议端口：MC / 明文 HTTP / HTTPS",
                plugin.getMcHost(), plugin.getMcPort(), plugin.getChannel(),
                plugin.isSnifferEnabled() ? I18n.t("log.plugin.on", "开") : I18n.t("log.plugin.off", "关"),
                plugin.getGateway() == null ? I18n.t("log.plugin.off", "关") : I18n.t("log.plugin.on", "开"),
                getTlsEngineSupplier() == null ? I18n.t("log.plugin.off", "关") : I18n.t("log.plugin.on", "开"),
                plugin.getApiRegistry() == null ? 0 : plugin.getApiRegistry().getRoutes().size(),
                webRoot == null ? I18n.t("log.plugin.webroot-builtin", "(jar 内置)") : webRoot.getAbsolutePath(),
                plugin.getMcPort());
    }

    /** 从 gateway/ 目录重建网关（策略链 + TLS + debug-events 开关）。 */
    private void rebuildGateway(File gatewayDir) {
        plugin.setGateway(null);
        plugin.setTlsFactory(null);
        ConfigurationSection gwCfg = GatewayConfig.loadYml(new File(gatewayDir, "config.yml"));
        plugin.setDebugEventsEnabled(gwCfg != null && gwCfg.getBoolean("debug-events", false));
        if (plugin.getGatewayEventListener() != null) {
            plugin.getGatewayEventListener().setDebugEnabled(plugin.isDebugEventsEnabled());
        }
        boolean gatewayEnabled = gwCfg != null && gwCfg.getBoolean("enabled", true);
        if (gatewayEnabled) {
            plugin.setGateway(new GatewayFilter());
            plugin.getGateway().reload(gatewayDir);
            ConfigurationSection https = GatewayConfig.loadYml(new File(gatewayDir, "https.yml"));
            if (https != null && https.getBoolean("enabled", true)) {
                try {
                    plugin.setTlsFactory(new TlsContextFactory(plugin.getDataFolder(), https));
                    plugin.getTlsFactory().init();
                } catch (Exception e) {
                    log.errorT(e, "log.plugin.tls-init-fail",
                            "TLS 初始化失败，已禁用 HTTPS（客户端经 HTTPS 访问将被当作 MC 流量断开）。原因: {0}，详见堆栈",
                            e.getMessage());
                    plugin.setTlsFactory(null);
                }
            }
        }
        final Supplier<SSLEngine> tlsEngines = plugin.getTlsFactory() == null ? null : plugin.getTlsFactory()::newServerEngine;
        if (plugin.getSniffer() != null) {
            plugin.getSniffer().setGateway(plugin.getGateway());
            plugin.getSniffer().setTlsEngineSupplier(tlsEngines);
        }
    }
}
