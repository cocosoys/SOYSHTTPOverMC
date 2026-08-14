package soys.soyshttpovermc.api.impl;

import org.bukkit.plugin.Plugin;

import soys.soyshttpovermc.ApiRegistry;
import soys.soyshttpovermc.api.ApiRegistrationApi;
import soys.soyshttpovermc.api.AuthCredentialApi;
import soys.soyshttpovermc.api.BotManagementApi;
import soys.soyshttpovermc.api.ExtensionApi;
import soys.soyshttpovermc.api.HttpClientApi;
import soys.soyshttpovermc.api.PluginLoggerApi;
import soys.soyshttpovermc.api.ApiToolkitApi;
import soys.soyshttpovermc.api.SoysHttpOverMcApi;
import soys.soyshttpovermc.api.WebPageApi;
import soys.soyshttpovermc.bot.BotManager;
import soys.soyshttpovermc.gateway.GatewayFilter;
import soys.soyshttpovermc.web.WebRegistry;

/**
 * {@link SoysHttpOverMcApi} 的包内实现（Holder 跳转）：
 * 构造注入各 registry 与 BotManager，按能力组组合 7 个独立实现类
 * （{@code soys.soyshttpovermc.api.impl.*}），每个分组 getter 返回对应实现类，
 * 自身不持有业务逻辑。由 HttpOverMcPlugin 在 onEnable 构造并暴露。
 */
public class SoysHttpOverMcApiImpl implements SoysHttpOverMcApi {

    private final ApiRegistrationImpl apiRegistration;
    private final WebPageImpl webPage;
    private final AuthCredentialImpl authCredential;
    private final ApiToolkitImpl toolkit;
    private final PluginLoggerImpl logger;
    private final BotManagementImpl botManagement;
    private final HttpClientImpl httpClient;
    private final ExtensionImpl extension;

    public SoysHttpOverMcApiImpl(Plugin hostPlugin, ApiRegistry apiRegistry,
                                 WebRegistry webRegistry, GatewayFilter gateway, BotManager botManager) {
        this.apiRegistration = new ApiRegistrationImpl(apiRegistry);
        this.webPage = new WebPageImpl(webRegistry);
        this.authCredential = new AuthCredentialImpl(gateway);
        this.toolkit = new ApiToolkitImpl(hostPlugin);
        this.logger = new PluginLoggerImpl(hostPlugin);
        this.botManagement = new BotManagementImpl(botManager);
        this.httpClient = new HttpClientImpl(apiRegistry);
        this.extension = new ExtensionImpl((soys.soyshttpovermc.HttpOverMcPlugin) hostPlugin);
    }

    @Override public ApiRegistrationApi getApiRegistration() { return apiRegistration; }

    @Override public WebPageApi getWebPage() { return webPage; }

    @Override public AuthCredentialApi getAuthCredential() { return authCredential; }

    @Override public ApiToolkitApi getToolkit() { return toolkit; }

    @Override public PluginLoggerApi getLogger() { return logger; }

    @Override public BotManagementApi getBotManagement() { return botManagement; }

    @Override public HttpClientApi getHttpClient() { return httpClient; }

    @Override public ExtensionApi getExtension() { return extension; }
}
