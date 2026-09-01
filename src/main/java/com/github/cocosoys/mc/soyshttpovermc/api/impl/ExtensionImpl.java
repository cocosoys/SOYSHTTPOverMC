package com.github.cocosoys.mc.soyshttpovermc.api.impl;

import com.github.cocosoys.mc.soyshttpovermc.HttpOverMcPlugin;
import com.github.cocosoys.mc.soyshttpovermc.api.ExtensionApi;
import com.github.cocosoys.mc.soyshttpovermc.command.SoysHttpCommand;
import com.github.cocosoys.mc.soyshttpovermc.command.SubCommand;
import com.github.cocosoys.mc.soyshttpovermc.exception.ApiException;
import com.github.cocosoys.mc.soyshttpovermc.exception.ExceptionBus;
import com.github.cocosoys.mc.soyshttpovermc.i18n.I18n;
import com.github.cocosoys.mc.soyshttpovermc.web.WebInterceptor;
import com.github.cocosoys.mc.soyshttpovermc.web.WebInterceptorRegistry;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.GatewayFilter;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.SecurityPolicy;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.bridge.spi.LoginProvider;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.bridge.spi.LoginProviderFactory;

/**
 * 能力组 8：扩展接入实现（委托 {@link LoginProviderFactory} / {@link SoysHttpCommand}）。
 * 由 {@link SoysHttpOverMcApiImpl} 组合并对外暴露。
 */
public class ExtensionImpl implements ExtensionApi {

    private final HttpOverMcPlugin plugin;

    public ExtensionImpl(HttpOverMcPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void registerLoginProvider(LoginProvider provider) {
        try {
            LoginProviderFactory.register(provider);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new ApiException("E_REGISTER_LOGIN_PROVIDER",
                    "exception.extension.login-provider-fail", "注册登录插件提供者失败: {0}", ex, ex.getMessage()));
        }
    }

    @Override
    public void registerSubCommand(SubCommand subCommand) {
        SoysHttpCommand cmd = plugin.getCommand();
        if (cmd == null) {
            throw ExceptionBus.fire(new ApiException("E_REGISTER_SUB_COMMAND",
                    "exception.extension.sub-command-uninit", "/soyshttp 命令尚未初始化（请在宿主 onEnable 完成后注册子指令）"));
        }
        cmd.registerSubCommand(subCommand);
    }

    @Override
    public void registerWebInterceptor(WebInterceptor interceptor) {
        try {
            WebInterceptorRegistry reg = plugin.getWebInterceptorRegistry();
            if (reg == null) {
                throw new IllegalStateException(I18n.t("exception.extension.interceptor-registry-uninit", "请求拦截器注册中心尚未初始化（宿主未就绪）"));
            }
            reg.register(interceptor);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new ApiException("E_REGISTER_INTERCEPTOR",
                    "exception.extension.interceptor-register-fail", "注册请求拦截器失败: {0}", ex, ex.getMessage()));
        }
    }

    @Override
    public void registerPolicy(SecurityPolicy policy) {
        try {
            GatewayFilter gw = plugin.getGateway();
            if (gw == null) {
                throw new IllegalStateException(I18n.t("exception.extension.policy-gateway-off", "网关未启用，无法注入策略"));
            }
            gw.addPluginPolicy(policy);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new ApiException("E_REGISTER_POLICY",
                    "exception.extension.policy-inject-fail", "注入插件策略失败: {0}", ex, ex.getMessage()));
        }
    }
}
