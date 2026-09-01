package com.github.cocosoys.mc.soyshttpovermc.api.impl;

import com.github.cocosoys.mc.soyshttpovermc.HttpOverMcPlugin;
import com.github.cocosoys.mc.soyshttpovermc.api.*;
import com.github.cocosoys.mc.soyshttpovermc.web.ApiRegistry;
import com.github.cocosoys.mc.soyshttpovermc.web.CorsRegistry;
import com.github.cocosoys.mc.soyshttpovermc.web.LargeFileLoaderRegistry;
import com.github.cocosoys.mc.soyshttpovermc.web.WebRegistry;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.GatewayFilter;
import org.bukkit.plugin.Plugin;

/**
 * {@link SoysHttpOverMcApi} 的包内实现（Holder 跳转）：
 * 构造注入各 registry，按能力组组合独立实现类
 * （{@code soys.soyshttpovermc.api.impl.*}），每个分组 getter 返回对应实现类，
 * 自身不持有业务逻辑。由 HttpOverMcPlugin 在 onEnable 构造并暴露。
 */
public class SoysHttpOverMcApiImpl implements SoysHttpOverMcApi {

    private final ApiRegistrationImpl apiRegistration;
    private final WebPageImpl webPage;
    private final AuthCredentialImpl authCredential;
    private final ApiToolkitImpl toolkit;
    private final HttpClientImpl httpClient;
    private final ExtensionImpl extension;
    private final HttpOverMcPlugin hostPlugin;

    public SoysHttpOverMcApiImpl(Plugin hostPlugin, ApiRegistry apiRegistry,
                                 WebRegistry webRegistry, GatewayFilter gateway,
                                 LargeFileLoaderRegistry largeFileLoaderRegistry,
                                 CorsRegistry corsRegistry) {
        this.hostPlugin = (HttpOverMcPlugin) hostPlugin;
        this.apiRegistration = new ApiRegistrationImpl(apiRegistry);
        this.webPage = new WebPageImpl(webRegistry, largeFileLoaderRegistry, corsRegistry);
        this.authCredential = new AuthCredentialImpl(gateway);
        this.toolkit = new ApiToolkitImpl(hostPlugin);
        this.httpClient = new HttpClientImpl(this.hostPlugin, apiRegistry);
        this.extension = new ExtensionImpl(this.hostPlugin);
    }

    @Override
    public ApiRegistrationApi getApiRegistration() {
        return apiRegistration;
    }

    @Override
    public WebPageApi getWebPage() {
        return webPage;
    }

    @Override
    public AuthCredentialApi getAuthCredential() {
        return authCredential;
    }

    @Override
    public ApiToolkitApi getToolkit() {
        return toolkit;
    }

    @Override
    public HttpClientApi getHttpClient() {
        return httpClient;
    }

    @Override
    public ExtensionApi getExtension() {
        return extension;
    }

    @Override
    public void registerReloadHook(ReloadHttpConfigHandler handler) {
        hostPlugin.getDelegate().registerReloadHook(handler);
    }
}
