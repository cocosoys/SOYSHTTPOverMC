package com.github.cocosoys.mc.soyshttpovermc;

import com.github.cocosoys.mc.soyshttpovermc.api.ReloadHttpConfigHandler;
import com.github.cocosoys.mc.soyshttpovermc.api.SoysHttpOverMcApi;
import com.github.cocosoys.mc.soyshttpovermc.command.SoysHttpCommand;
import com.github.cocosoys.mc.soyshttpovermc.config.EulaConfig;
import com.github.cocosoys.mc.soyshttpovermc.config.LanguageConfig;
import com.github.cocosoys.mc.soyshttpovermc.config.PagesConfig;
import com.github.cocosoys.mc.soyshttpovermc.enums.ProxyPlatform;
import com.github.cocosoys.mc.soyshttpovermc.event.GatewayEventListener;
import com.github.cocosoys.mc.soyshttpovermc.permission.CombinedPermissionService;
import com.github.cocosoys.mc.soyshttpovermc.spi.Platform;
import com.github.cocosoys.mc.soyshttpovermc.proxy.ServerRegistry;
import com.github.cocosoys.mc.soyshttpovermc.spring.impl.AuthServiceImpl;
import com.github.cocosoys.mc.soyshttpovermc.storage.StorageManager;
import com.github.cocosoys.mc.soyshttpovermc.storage.SyncStorage;
import com.github.cocosoys.mc.soyshttpovermc.web.*;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.GatewayFilter;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.bridge.AuthLoginBridge;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.bridge.spi.LoginProvider;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.tls.TlsContextFactory;
import com.github.cocosoys.mc.soyshttpovermc.web.http.HttpBackendMode;
import com.github.cocosoys.mc.soyshttpovermc.web.http.HttpRequestHandler;
import com.github.cocosoys.mc.soyshttpovermc.web.http.handler.standalone.StandaloneHttpServer;
import com.github.cocosoys.mc.soyshttpovermc.web.http.sniffer.SocketSniffer;
import lombok.AccessLevel;
import lombok.CustomLog;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * HTTP-Over-MC 主插件类（上帝类）：仅保留字段属性 + 生命周期入口（onEnable/onDisable）+ 单例访问。
 *
 * <p>所有业务逻辑（初始化 / 配置 / 状态查询 / 热重载等）已迁移至
 * {@link HttpOverMcPluginProxy} 代理类。外部调用者经 {@link #getDelegate()} 获取代理类实例后委托调用，
 * 例如 {@code plugin.getDelegate().reloadHttpConfig()}。</p>
 *
 * <p>字段访问约定：
 * <ul>
 *   <li>{@code @Getter}：公开读访问（含外部插件）；</li>
 *   <li>{@code @Setter(AccessLevel.PACKAGE)}：包级写访问（仅代理类同包可修改，外部不可写）。</li>
 * </ul>
 * final 字段（reloadHooks / serverRegistry）仅生成 getter，不生成 setter。</p>
 */
@CustomLog
@Getter
@Setter(AccessLevel.PACKAGE)
public class HttpOverMcPlugin extends JavaPlugin {

    /**
     * -- GETTER --
     * 供其他插件获取本插件实例（接入注解式 API / 监听网关事件 / 下发凭证）
     */
    private @Getter
    static HttpOverMcPlugin instance;

    /**
     * 上帝代理类：承载全部业务逻辑。构造期即创建，onEnable/onDisable 委托调用。
     * -- GETTER --
     * 获取上帝代理类实例（承载全部业务逻辑；外部经此调用迁移后的方法）。
     */
    private @Getter
    final HttpOverMcPluginProxy delegate = new HttpOverMcPluginProxy(this);

    private SocketSniffer sniffer;
    /**
     * 独立 HTTP 服务器（standalone-server 模式时使用，其他模式为 null）
     */
    private StandaloneHttpServer standaloneServer;
    /**
     * 当前 HTTP 后端处理器（direct/netty-eventloop/memory-queue 之一）
     */
    private HttpRequestHandler httpBackend;
    /**
     * 当前 HTTP 后端模式
     */
    private HttpBackendMode httpBackendMode;
    private GatewayFilter gateway;
    private TlsContextFactory tlsFactory;
    private ApiRegistry apiRegistry;
    private WebRegistry webRegistry;
    private SoysHttpOverMcApi api;
    private GatewayEventListener gatewayEventListener;
    private volatile boolean debugEventsEnabled = false;
    private String mcHost;
    private int mcPort;
    private boolean snifferEnabled;
    private int maxBody;
    /**
     * 网页登录桥（session-token 颁发器启用时创建；null=未启用）
     */
    private AuthLoginBridge authLoginBridge;
    /**
     * /soyshttp 命令执行器（第三方插件经门面注册子指令用；onEnable 完成前可能为 null）。
     */
    private SoysHttpCommand command;
    /**
     * 当前激活的登录插件提供者（AuthMe 等，经 LoginProviderFactory 选取；null=未接入）
     */
    private volatile LoginProvider loginProvider;
    /**
     * 前端处理器（/soyshttp reload 后向其热替换登录桥）
     */
    private WebFrontendHandler webFrontend;
    /**
     * 热重载钩子（其它插件经门面注册，/soyshttp reload 时随本插件一起刷新自身配置）
     */
    private List<ReloadHttpConfigHandler> reloadHooks = new ArrayList<>();
    /**
     * 前端磁盘根（web.root 解析结果；核心网页登记、reload 复用）
     */
    private File webRootDir;
    /**
     * 登录窗口认证服务（/soyshttp reload 后向其热替换登录桥）
     */
    private AuthServiceImpl authService;
    /**
     * 当前运行拓扑：独立服 / BungeeCord(Waterfall) / Velocity（群组服探测结果）
     */
    private ProxyPlatform proxyPlatform = ProxyPlatform.STANDALONE;
    /**
     * 群组服服务器名（config.yml proxy.server-name；独立服为空）
     */
    private String serverName = "";
    /**
     * 群组服下代理连接的地址（config.yml proxy.proxy-address，host:port；独立服为空）
     */
    private String proxyAddress = "";
    /**
     * 群组服服务器标签注册表（本服自注册 + 经 discovery 收集其他子服）
     */
    private ServerRegistry serverRegistry = new ServerRegistry();
    /**
     * Web 内容存活缓存（pinned 常驻 + LRU + TTL + 大文件加载器）；onEnable 装配，reload 不重建（配置改动重启生效）
     */
    private WebContentCache webContentCache = null;
    /**
     * 大文件加载器注册中心（默认流式加载器 + 开发者自定义）
     */
    private LargeFileLoaderRegistry largeFileLoaderRegistry = null;
    /**
     * 大文件安全上限（超过直接 413，防单文件打爆内存；默认 128MB）
     */
    private long largeFileMaxBytes = 128L * 1024 * 1024;
    /**
     * 请求级拦截器注册中心（WebInterceptor SPI；onEnable 装配）
     */
    private WebInterceptorRegistry webInterceptorRegistry = null;
    /**
     * CORS 声明注册中心（onEnable 装配）
     */
    private CorsRegistry corsRegistry = null;
    /**
     * 跨服同步存储（MySQL 等；null=内存模式）。
     */
    private SyncStorage syncStorage = null;
    /**
     * 多后端存储协调器（YAML/SQLite/MySQL 主辅+镜像；null=内存模式）。
     */
    private StorageManager storageManager = null;
    /**
     * language.yml 配置封装（current/rule/sources 读写；由 ConfigManager.initLanguageConfig 装配）。
     */
    private LanguageConfig languageConfig;
    /**
     * pages.yml 配置封装（web.* 读写；由 ConfigManager.initPagesConfig 装配）。
     */
    private PagesConfig pagesConfig;
    /**
     * EULA 协议配置封装（是否已同意；onEnable 最早阶段由 ConfigManager.initEulaConfig 装配）。
     */
    private EulaConfig eulaConfig;
    /**
     * 平台抽象（common 各包通过它访问宿主能力；Bukkit 默认实现，版本模块可覆写）。
     */
    private Platform platform;
    /**
     * 组合权限服务（多权限插件组合判断，含离线权限查询能力）。
     */
    private volatile CombinedPermissionService combinedPermissionService;
    /**
     * 网页访问权限检查器（pages.yml 的 pages.permissions / page 内联；reload 时重建）。
     */
    private volatile PagePermissionChecker pagePermissionChecker;
    /**
     * 版本兼容嗅探器安装句柄（由 adapter HttpSnifferInstaller 返回，仅供卸载凭据）。
     */
    private volatile Object snifferHandle;

    @Override
    public void onEnable() {
        instance = this;
        delegate.onEnable();
    }

    @Override
    public void onDisable() {
        delegate.onDisable();
        instance = null;
    }

    public File getFileProxy() {
        return getFile();
    }
}
