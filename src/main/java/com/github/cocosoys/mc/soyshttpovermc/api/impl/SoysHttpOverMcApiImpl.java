package com.github.cocosoys.mc.soyshttpovermc.api.impl;

import com.github.cocosoys.mc.soyshttpovermc.HttpOverMcPlugin;
import com.github.cocosoys.mc.soyshttpovermc.web.CorsRegistry;
import com.github.cocosoys.mc.soyshttpovermc.web.LargeFileLoaderRegistry;
import org.bukkit.plugin.Plugin;

import com.github.cocosoys.mc.soyshttpovermc.ApiRegistry;
import com.github.cocosoys.mc.soyshttpovermc.api.ApiRegistrationApi;
import com.github.cocosoys.mc.soyshttpovermc.api.AuthCredentialApi;
import com.github.cocosoys.mc.soyshttpovermc.api.BotManagementApi;
import com.github.cocosoys.mc.soyshttpovermc.api.CrossServerHttpClient;
import com.github.cocosoys.mc.soyshttpovermc.api.ExtensionApi;
import com.github.cocosoys.mc.soyshttpovermc.api.HttpClientApi;
import com.github.cocosoys.mc.soyshttpovermc.api.ApiToolkitApi;
import com.github.cocosoys.mc.soyshttpovermc.api.SoysHttpOverMcApi;
import com.github.cocosoys.mc.soyshttpovermc.api.ReloadHttpConfigHandler;
import com.github.cocosoys.mc.soyshttpovermc.api.WebPageApi;
import com.github.cocosoys.mc.soyshttpovermc.bot.BotManager;
import com.github.cocosoys.mc.soyshttpovermc.gateway.GatewayFilter;
import com.github.cocosoys.mc.soyshttpovermc.web.WebRegistry;

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
    private final BotManagementImpl botManagement;
    private final HttpClientImpl httpClient;
    private final ExtensionImpl extension;
    private final CrossServerHttpClientImpl crossServer;
    private final HttpOverMcPlugin hostPlugin;

    public SoysHttpOverMcApiImpl(Plugin hostPlugin, ApiRegistry apiRegistry,
                                 WebRegistry webRegistry, GatewayFilter gateway, BotManager botManager,
                                 LargeFileLoaderRegistry largeFileLoaderRegistry,
                                 CorsRegistry corsRegistry) {
        this.hostPlugin = (HttpOverMcPlugin) hostPlugin;
        this.apiRegistration = new ApiRegistrationImpl(apiRegistry);
        this.webPage = new WebPageImpl(webRegistry, largeFileLoaderRegistry, corsRegistry);
        this.authCredential = new AuthCredentialImpl(gateway);
        this.toolkit = new ApiToolkitImpl(hostPlugin);
        this.botManagement = new BotManagementImpl(botManager);
        this.httpClient = new HttpClientImpl(this.hostPlugin, apiRegistry);
        this.extension = new ExtensionImpl(this.hostPlugin);
        this.crossServer = new CrossServerHttpClientImpl(this.hostPlugin, this.hostPlugin.getMcLink());
    }

    @Override public ApiRegistrationApi getApiRegistration() { return apiRegistration; }

    @Override public WebPageApi getWebPage() { return webPage; }

    @Override public AuthCredentialApi getAuthCredential() { return authCredential; }

    @Override public ApiToolkitApi getToolkit() { return toolkit; }

    @Override public BotManagementApi getBotManagement() { return botManagement; }

    @Override public HttpClientApi getHttpClient() { return httpClient; }

    @Override public ExtensionApi getExtension() { return extension; }

    @Override public CrossServerHttpClient getCrossServer() { return crossServer; }

    @Override public void registerReloadHook(ReloadHttpConfigHandler handler) {
        hostPlugin.registerReloadHook(handler);
    }
}
