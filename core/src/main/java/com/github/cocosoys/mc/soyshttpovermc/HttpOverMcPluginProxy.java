package com.github.cocosoys.mc.soyshttpovermc;

import com.github.cocosoys.mc.soyshttpovermc.api.ReloadHttpConfigHandler;
import com.github.cocosoys.mc.soyshttpovermc.api.event.HttpConfigReloadEvent;
import com.github.cocosoys.mc.soyshttpovermc.api.event.SoysReadyEvent;
import com.github.cocosoys.mc.soyshttpovermc.api.impl.SoysHttpOverMcApiImpl;
import com.github.cocosoys.mc.soyshttpovermc.command.SoysHttpCommand;
import com.github.cocosoys.mc.soyshttpovermc.config.ConfigManager;
import com.github.cocosoys.mc.soyshttpovermc.config.EulaConfig;
import com.github.cocosoys.mc.soyshttpovermc.config.LanguageConfig;
import com.github.cocosoys.mc.soyshttpovermc.config.PagesConfig;
import com.github.cocosoys.mc.soyshttpovermc.enums.ProxyPlatform;
import com.github.cocosoys.mc.soyshttpovermc.event.ApiLifecycleListener;
import com.github.cocosoys.mc.soyshttpovermc.event.GatewayEventListener;
import com.github.cocosoys.mc.soyshttpovermc.i18n.I18n;
import com.github.cocosoys.mc.soyshttpovermc.log.LogKit;
import com.github.cocosoys.mc.soyshttpovermc.orm.YAML;
import com.github.cocosoys.mc.soyshttpovermc.orm.executor.SqlBackendExecutor;
import com.github.cocosoys.mc.soyshttpovermc.platform.PlatformBukkitImpl;
import com.github.cocosoys.mc.soyshttpovermc.permission.CombinedPermissionService;
import com.github.cocosoys.mc.soyshttpovermc.proxy.ProxyDetector;
import com.github.cocosoys.mc.soyshttpovermc.spi.Platforms;
import com.github.cocosoys.mc.soyshttpovermc.spring.controller.AuthController;
import com.github.cocosoys.mc.soyshttpovermc.spring.controller.StatusController;
import com.github.cocosoys.mc.soyshttpovermc.spring.controller.SystemController;
import com.github.cocosoys.mc.soyshttpovermc.spring.impl.AuthServiceImpl;
import com.github.cocosoys.mc.soyshttpovermc.spring.impl.StatusServiceImpl;
import com.github.cocosoys.mc.soyshttpovermc.spring.impl.SystemServiceImpl;
import com.github.cocosoys.mc.soyshttpovermc.spring.service.IStatusService;
import com.github.cocosoys.mc.soyshttpovermc.spring.service.ISystemService;
import com.github.cocosoys.mc.soyshttpovermc.storage.RecordSyncStorage;
import com.github.cocosoys.mc.soyshttpovermc.storage.StorageManager;
import com.github.cocosoys.mc.soyshttpovermc.storage.SyncStorage;
import com.github.cocosoys.mc.soyshttpovermc.storage.impl.YamlStorage;
import com.github.cocosoys.mc.soyshttpovermc.web.*;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.GatewayConfig;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.GatewayFilter;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.AuthPolicy;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.bridge.AuthLoginBridge;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.bridge.provider.AuthMeLoginProvider;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.bridge.spi.LoginProviderContext;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.bridge.spi.LoginProviderFactory;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.issuer.CredentialIssuer;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.issuer.SessionTokenIssuer;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.tls.TlsContextFactory;
import com.github.cocosoys.mc.soyshttpovermc.web.http.HttpBackendMode;
import com.github.cocosoys.mc.soyshttpovermc.web.http.HttpRequestHandler;
import com.github.cocosoys.mc.soyshttpovermc.web.http.handler.direct.DirectRequestHandler;
import com.github.cocosoys.mc.soyshttpovermc.web.http.handler.memory.MemoryQueueRequestHandler;
import com.github.cocosoys.mc.soyshttpovermc.web.http.handler.netty.NettyEventLoopRequestHandler;
import com.github.cocosoys.mc.soyshttpovermc.web.http.handler.standalone.StandaloneHttpServer;
import com.github.cocosoys.mc.soyshttpovermc.web.http.sniffer.HttpSnifferDeps;
import com.github.cocosoys.mc.soyshttpovermc.web.http.sniffer.HttpSnifferInstaller;
import com.github.cocosoys.mc.soyshttpovermc.web.http.sniffer.HttpSnifferInstallers;
import com.github.cocosoys.mc.soyshttpovermc.web.http.sniffer.SocketSniffer;
import lombok.CustomLog;
import org.bukkit.configuration.ConfigurationSection;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLContext;
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
        plugin.setPlatform(new PlatformBukkitImpl(plugin));
    }

    // ===== 主配置 config.yml（UTF-8 兼容读取缓存；经 PlatformYaml → adapter 版本实现） =====

    /**
     * config.yml 的 UTF-8 兼容读取缓存（经 PlatformYaml → 版本模块 Platform.loadYaml）。
     * 避免 Bukkit 1.7.x YamlConfiguration 读 UTF-8 中文报 "special characters are not allowed"。
     */
    private volatile org.bukkit.configuration.file.YamlConfiguration coreConfig;

    /**
     * 读取主配置 config.yml（UTF-8 兼容，经 PlatformYaml → adapter 版本实现）。
     */
    public org.bukkit.configuration.file.YamlConfiguration coreConfig() {
        org.bukkit.configuration.file.YamlConfiguration c = coreConfig;
        if (c == null) {
            synchronized (this) {
                c = coreConfig;
                if (c == null) {
                    c = com.github.cocosoys.mc.soyshttpovermc.platform.PlatformYaml.load(
                            new File(plugin.getDataFolder(), "config.yml"));
                    coreConfig = c;
                }
            }
        }
        return c;
    }

    /**
     * 清空 config.yml 读取缓存（/soyshttp reload 时调用）。
     */
    public void reloadCoreConfig() {
        synchronized (this) {
            coreConfig = null;
        }
    }

    /**
     * 将主配置保存回 config.yml（UTF-8 兼容，经 PlatformYaml → adapter 版本实现）。
     */
    public void saveCoreConfig() {
        try {
            com.github.cocosoys.mc.soyshttpovermc.platform.PlatformYaml.save(coreConfig(),
                    new File(plugin.getDataFolder(), "config.yml"));
        } catch (java.io.IOException e) {
            plugin.getLogger().warning("保存 config.yml 失败: " + e);
        }
    }

    // ===== 生命周期入口（由上帝类 onEnable/onDisable 委托调用） =====

    /**
     * onEnable 业务流程（不含 instance 赋值，由上帝类处理）。
     */
    public void onEnable() {
        // 0) EULA 使用/开发协议校验
        plugin.setEulaConfig(ConfigManager.initEulaConfig(plugin));
        if (!plugin.getEulaConfig().isAccepted()) {
            EulaConfig.promptDisabled(plugin.getLogger());
            plugin.getServer().getPluginManager().disablePlugin(plugin);
            return;
        }

        plugin.saveDefaultConfig();

        // 0.1) 加载版本适配器
        // 注册 Platform 默认实现（common 各包经 Platforms.get() 访问宿主能力；版本模块可经 ServiceLoader 覆盖）
        PlatformBukkitImpl.setCurrentPlugin(plugin);
        Platforms.bind(new PlatformBukkitImpl(plugin));

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
        LogKit.init(plugin.getLogger(), coreConfig().getString("log.level", "INFO"));
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
        SqlBackendExecutor.init(plugin.getPlatform());
        // 4) 安全网关 + TLS 上下文
        rebuildGateway(gatewayDir);
        // 4.5) AuthMe 网页登录接入
        setupAuthIntegration();
        // 5) 注解式 API 框架 + 网页登记 + 事件监听 + 系统级 API
        initApiFramework(gatewayDir);
        // 5.5) 读取 HTTP 后端模式
        HttpBackendMode backendMode = HttpBackendMode.from(coreConfig().getString("http-backend.mode", "netty-eventloop"));
        // 6.5) 对外集成门面
        initApiImpl();

        // 6.75) 静态可打开界面纳入【统一注册通道】+ 网页访问权限检查器
        PagesConfig.Manual.register(plugin, plugin.getWebRegistry());
        plugin.setPagePermissionChecker(PagesConfig.Manual.buildPermissionChecker(plugin));

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

    /**
     * onDisable 业务流程（不含 instance 清空，由上帝类处理）。
     */
    public void onDisable() {
        LoginProviderFactory.shutdownAll();
        plugin.setLoginProvider(null);
        if (plugin.getSniffer() != null) {
            plugin.getSniffer().uninstall();
        }
        if (plugin.getStandaloneServer() != null) {
            try {
                plugin.getStandaloneServer().shutdown();
            } catch (Throwable ignored) {
            }
        }
        if (plugin.getHttpBackend() != null) {
            try {
                plugin.getHttpBackend().shutdown();
            } catch (Throwable ignored) {
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

    /**
     * language.yml 配置对象（国际化：current/rule/sources）；reload 时由 ConfigManager 重新装配。
     */
    public org.bukkit.configuration.file.YamlConfiguration getLanguageConfig() {
        LanguageConfig cfg = plugin.getLanguageConfig();
        return cfg == null ? null : cfg.raw();
    }

    /**
     * 持久化 language.yml（切换语言 / 修改语言源后调用）。
     */
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

    /**
     * pages.yml 配置对象（web.* 段 / pages 段统一在此；可能为 null=文件落盘失败）。
     */
    public org.bukkit.configuration.file.YamlConfiguration getPagesConfig() {
        PagesConfig cfg = plugin.getPagesConfig();
        return cfg == null ? null : cfg.raw();
    }

    /**
     * 读取 pages.yml web.* 配置项（如 web.home / web.root / web.cache.max-bytes）。缺省返回默认值。
     */
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

    /**
     * HTTPS（TLS 引擎）是否可用
     */
    public boolean isTlsEnabled() {
        return plugin.getTlsFactory() != null;
    }

    /**
     * 本服存储标识（群组服=server-name；独立服=standalone-&lt;host&gt;:&lt;port&gt;）。
     */
    public String storageServerId() {
        if (plugin.getProxyPlatform() != ProxyPlatform.STANDALONE
                && plugin.getServerName() != null && !plugin.getServerName().isEmpty()) {
            return plugin.getServerName();
        }
        return "standalone-" + getMcHost() + ":" + getMcPort();
    }

    /**
     * 手动上报入口（/soyshttp report）。
     */
    public void reportContribution() {
        uploadContribution();
    }

    /**
     * /soyshttp reload：热重载日志级别 + 网关策略与 TLS 配置 + 存储后端。
     */
    public void reloadHttpConfig() {
        reloadCoreConfig();
        initLanguageConfig();
        loadPagesConfig();
        // 重建网页访问权限检查器（pages.yml 权限配置可能已修改）
        plugin.setPagePermissionChecker(PagesConfig.Manual.buildPermissionChecker(plugin));
        String levelRaw = coreConfig().getString("log.level", "INFO");
        LogKit.setLevel(levelRaw);
        initStorage();
        File gatewayDir = ConfigManager.ensureGatewayFiles(plugin);
        rebuildGateway(gatewayDir);
        if (plugin.getApiRegistry() != null) {
            CombinedPermissionService cps = new CombinedPermissionService(plugin, plugin.getGateway());
            plugin.setCombinedPermissionService(cps);
            plugin.getApiRegistry().setPermissionService(cps);
            plugin.getApiRegistry().setPlayerResolver(cps::subjectOf);
        }
        setupAuthIntegration();
        if (plugin.getAuthService() != null) {
            plugin.getAuthService().setBridge(plugin.getAuthLoginBridge());
        }
        if (plugin.getWebRegistry() != null) {
            // 先卸载上次 pages.yml 注册的页面（tag 标记），再重新注册，避免已删除路径/昵称残留内存
            plugin.getWebRegistry().unregisterByTag(PagesConfig.NAME);
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
    }

    // ===== 本服地址 / 拓扑查询（原 public，有逻辑，移至代理类） =====

    /**
     * 本服对外 host：优先 config.yml 的 {@code mc.public-host}，
     * 否则 {@code mc.host}，再回退 server.properties 的 server-ip → 127.0.0.1。
     */
    public String getMcHost() {
        return ConfigManager.resolveMcPublicHost(plugin,
                coreConfig().getString("mc.host", ""), coreConfig().getString("mc.public-host", ""));
    }

    /**
     * 本服对外 port：优先 config.yml 的 {@code mc.public-port}，
     * 否则 {@code mc.port}，再回退 server.properties 的 server-port → 运行期端口。
     */
    public int getMcPort() {
        return ConfigManager.resolveMcPublicPort(plugin,
                coreConfig().getInt("mc.port", 0), coreConfig().getInt("mc.public-port", 0));
    }

    // ===== private 初始化方法 =====

    /**
     * 解析 ORM（YAML 后端）实体数据存放目录。
     */
    private File resolveYamlOrmDir() {
        String fileCfg = coreConfig().getString("storage.backends.yaml.file", "data");
        File dir = YamlStorage.resolveDir(plugin.getPlatform(), fileCfg);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /**
     * 装配 Web 内容缓存（pages.yml web.cache.* / web.large-file-*）。
     */
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

    /**
     * 装配多后端数据存储。
     */
    private void initStorage() {
        StorageManager manager = null;
        try {
            manager = new StorageManager(plugin.getPlatform());
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

    /**
     * 解析 JWT 密钥。
     */
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

    /**
     * 数据贡献自动上报（受 upload.enabled 开关控制）。
     */
    private void handleUploadContribution() {
        if (!coreConfig().getBoolean("upload.enabled", false)) {
            return;
        }
        uploadContribution();
    }

    private void uploadContribution() {
        final String serverUrl = coreConfig().getString("upload.server", "https://api.cocosoys.com/report");
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

    /**
     * 装配国际化环境（language.yml）。
     */
    private void initLanguageConfig() {
        plugin.setLanguageConfig(ConfigManager.initLanguageConfig(plugin));
    }

    /**
     * 从 config.yml 读取核心运行参数。
     */
    private void loadCoreConfig() {
        // 读取 HTTP 后端模式
        HttpBackendMode backendMode = HttpBackendMode.from(coreConfig().getString("http-backend.mode", "netty-eventloop"));
        plugin.setHttpBackendMode(backendMode);

        plugin.setProxyPlatform(ProxyDetector.detect(plugin.getPlatform()));
        log.infoT("log.plugin.proxy-topology", "运行拓扑探测: {0}", plugin.getProxyPlatform());
        plugin.setServerName(coreConfig().getString("proxy.server-name", ""));
        plugin.setProxyAddress(coreConfig().getString("proxy.proxy-address", ""));
        plugin.setMcHost(getMcHost());
        plugin.setMcPort(getMcPort());
        plugin.setSnifferEnabled(coreConfig().getBoolean("sniffer.enabled", true));
        plugin.setMaxBody(coreConfig().getInt("sniffer.max-body-bytes", 8 * 1024 * 1024));
    }

    /**
     * 初始化注解式 API 框架。
     */
    private void initApiFramework(File gatewayDir) {
        ConfigurationSection gwCfg = GatewayConfig.loadYml(new File(gatewayDir, "config.yml"));
        String apiPrefix = gwCfg == null ? "/api" : gwCfg.getString("api-prefix", "/api");

        plugin.setApiRegistry(new ApiRegistry(plugin));
        plugin.getApiRegistry().setPathPrefix(apiPrefix);
        CombinedPermissionService cps = new CombinedPermissionService(plugin, plugin.getGateway());
        plugin.setCombinedPermissionService(cps);
        plugin.getApiRegistry().setPermissionService(cps);
        plugin.getApiRegistry().setPlayerResolver(cps::subjectOf);
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
    }

    /**
     * 装配网页登录接入（登录插件 SPI）。
     */
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
        // 注入自动登录配置（记住我 / IP 匹配开关，gateway/policies/auth.yml auto.login.*）
        AuthPolicy authPolicy = plugin.getGateway() == null ? null : plugin.getGateway().getAuthPolicy();
        boolean ttlEnable = authPolicy == null ? true : authPolicy.isRememberEnabled();
        long ttlDays = Math.max(1, authPolicy == null ? 7 : authPolicy.getRememberTtlDays());
        boolean ipEnabled = authPolicy != null && authPolicy.isIpEnabled();
        plugin.getAuthLoginBridge().setAutoLoginConfig(ttlEnable, ttlDays * 86400_000L, ipEnabled);
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

    /**
     * 构造对外集成门面。
     */
    private void initApiImpl() {
        plugin.setApi(new SoysHttpOverMcApiImpl(plugin, plugin.getApiRegistry(), plugin.getWebRegistry(),
                plugin.getGateway(), plugin.getLargeFileLoaderRegistry(), plugin.getCorsRegistry()));
    }

    /**
     * 装配统计 / 状态 API / 前端处理器；返回统计实例供嗅探器复用。
     */
    private RequestStats initFrontend(File webRoot) {
        RequestStats stats = new RequestStats();
        IStatusService statusService = new StatusServiceImpl(stats, plugin.getMcPort());
        plugin.getApiRegistry().register(new StatusController(statusService));

        WebFrontendHandler web = new WebFrontendHandler(
                webRoot == null ? null : webRoot.getAbsolutePath(),
                webConfig("web.home", ""),
                plugin.getApiRegistry(), plugin.getWebRegistry(),
                plugin.getWebContentCache(), plugin.getLargeFileMaxBytes(),
                plugin.getCorsRegistry(), plugin.getWebInterceptorRegistry(),
                () -> plugin.getPagePermissionChecker(),
                () -> plugin.getCombinedPermissionService());
        plugin.setWebFrontend(web);

        return stats;
    }

    /**
     * 启动 HTTP 服务：同端口嗅探模式安装 SocketSniffer，独立服务器模式启动 StandaloneHttpServer。
     */
    private void initSniffer(RequestStats stats) {
        // 读取 HTTP 后端模式
        String modeStr = coreConfig().getString("http-backend.mode", "netty-eventloop");
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

        boolean trustProxy = coreConfig().getBoolean("mc.trust-proxy", true);
        int httpConcurrency = Math.max(1, coreConfig().getInt("sniffer.http-concurrency", 4));
        int httpQueue = Math.max(1, coreConfig().getInt("sniffer.http-queue-size", 8));
        int keepAliveIdleSeconds = Math.max(1, coreConfig().getInt("sniffer.keep-alive-idle-seconds", 30));

        // 1) 优先尝试版本兼容嗅探器（adapter v1_7x / v1_6x 经 SPI 注册；版本判断在 adapter 侧）
        HttpSnifferInstaller installer = HttpSnifferInstallers.find();
        if (installer.supported()) {
            HttpSnifferDeps deps = new HttpSnifferDeps(plugin, handler, () -> true,
                    plugin.getMaxBody(), stats, plugin.getGateway(),
                    getTlsEngineSupplier(), getTlsSslContextSupplier(), trustProxy, httpConcurrency, httpQueue, keepAliveIdleSeconds);
            try {
                Object handle = installer.install(deps);
                if (handle != null) {
                    plugin.setSnifferHandle(handle);
                    log.infoT("log.sniffer.adapter-installed", "已安装版本兼容嗅探器: {0}", installer.id());
                    return;
                }
                log.warnT("log.sniffer.adapter-install-empty",
                        "版本兼容嗅探器({0})安装返回空句柄，回退内置嗅探器", installer.id());
            } catch (Exception e) {
                log.warnT("log.sniffer.adapter-install-fail",
                        "版本兼容嗅探器({0})安装失败，回退内置嗅探器: {1}", installer.id(), String.valueOf(e));
            }
        }

        // 2) 回退 core 内置 SocketSniffer
        plugin.setSniffer(new SocketSniffer(plugin, handler,
                () -> true, plugin.getMaxBody(), stats, plugin.getGateway(),
                getTlsEngineSupplier(), trustProxy, httpConcurrency, httpQueue, keepAliveIdleSeconds));
        plugin.getSniffer().install();
    }

    /**
     * 启动独立 HTTP 服务器（standalone-server 模式）。
     */
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

    /**
     * 根据模式创建对应的 HTTP 后端处理器。配置结构：http-backend.<mode>.<key>（向后兼容旧的扁平结构）。
     */
    private HttpRequestHandler createHttpBackend(HttpBackendMode mode) {
        switch (mode) {
            case NETTY_EVENTLOOP: {
                int threads = getBackendInt("netty-eventloop", "threads", "netty-threads", 2);
                return new NettyEventLoopRequestHandler(plugin.getWebFrontend(), threads);
            }
            case MEMORY_QUEUE: {
                int capacity = getBackendInt("memory-queue", "capacity", "queue-capacity", 1024);
                int workers = getBackendInt("memory-queue", "workers", "queue-workers", 4);
                return new MemoryQueueRequestHandler(plugin.getWebFrontend(), capacity, workers);
            }
            case STANDALONE_SERVER:
            case DIRECT:
            default:
                return new DirectRequestHandler(plugin.getWebFrontend());
        }
    }

    /**
     * 读取 HTTP 后端配置，优先使用分层结构 http-backend.<mode>.<key>，
     * 回退到旧的扁平结构 http-backend.<legacyKey>，最后使用默认值。
     */
    private int getBackendInt(String mode, String key, String legacyKey, int def) {
        String layered = "http-backend." + mode + "." + key;
        if (coreConfig().contains(layered)) {
            return coreConfig().getInt(layered, def);
        }
        String legacy = "http-backend." + legacyKey;
        return coreConfig().getInt(legacy, def);
    }

    /**
     * 读取 HTTP 后端字符串配置（分层结构优先，回退旧结构）。
     */
    private String getBackendString(String mode, String key, String legacyKey, String def) {
        String layered = "http-backend." + mode + "." + key;
        if (coreConfig().contains(layered)) {
            return coreConfig().getString(layered, def);
        }
        String legacy = "http-backend." + legacyKey;
        return coreConfig().getString(legacy, def);
    }

    /**
     * TLS 服务端引擎供应器（无 TLS 工厂时返回 null）。
     */
    private Supplier<SSLEngine> getTlsEngineSupplier() {
        return plugin.getTlsFactory() == null ? null : plugin.getTlsFactory()::newServerEngine;
    }

    /**
     * TLS 上下文供应器（无 TLS 工厂时返回 null）：供 1.6.x 嗅探器在原生 Socket 上就地终止 TLS。
     */
    private Supplier<SSLContext> getTlsSslContextSupplier() {
        return plugin.getTlsFactory() == null ? null : plugin.getTlsFactory()::getSSLContext;
    }

    /**
     * 装配 /soyshttp 与简写 /shttp 命令。
     */
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

    /**
     * 启动 Banner。
     */
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

    /**
     * 启动完成日志。
     */
    private void logStartup(File webRoot) {
        printStartupBanner();
        log.infoT("log.plugin.startup",
                "HTTP-Over-MC 已启动（同端口嗅探 + 前端服务 + 安全网关 + 注解式API）: mc={0}:{1} 嗅探器={2} 网关={3} HTTPS={4} API注册数={5} webroot={6} | {7} 三协议端口：MC / 明文 HTTP / HTTPS",
                plugin.getMcHost(), plugin.getMcPort(),
                plugin.isSnifferEnabled() ? I18n.t("log.plugin.on", "开") : I18n.t("log.plugin.off", "关"),
                plugin.getGateway() == null ? I18n.t("log.plugin.off", "关") : I18n.t("log.plugin.on", "开"),
                getTlsEngineSupplier() == null ? I18n.t("log.plugin.off", "关") : I18n.t("log.plugin.on", "开"),
                plugin.getApiRegistry() == null ? 0 : plugin.getApiRegistry().getRoutes().size(),
                webRoot == null ? I18n.t("log.plugin.webroot-builtin", "(jar 内置)") : webRoot.getAbsolutePath(),
                plugin.getMcPort());
    }

    /**
     * 从 gateway/ 目录重建网关（策略链 + TLS + debug-events 开关）。
     */
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
