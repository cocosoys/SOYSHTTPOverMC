package com.github.cocosoys.mc.soyshttpovermc.api.impl;

import com.github.cocosoys.mc.soyshttpovermc.annotations.PermissionService;
import com.github.cocosoys.mc.soyshttpovermc.api.ApiRegistrationApi;
import com.github.cocosoys.mc.soyshttpovermc.api.event.ApiInfo;
import com.github.cocosoys.mc.soyshttpovermc.exception.ApiException;
import com.github.cocosoys.mc.soyshttpovermc.exception.ExceptionBus;
import com.github.cocosoys.mc.soyshttpovermc.web.ApiRegistry;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * 能力组 1：注解式 API 注册（委托 {@link ApiRegistry}）。
 * 由 {@link SoysHttpOverMcApiImpl} 组合并对外暴露；实现 {@link ApiRegistrationApi}。
 */
public class ApiRegistrationImpl implements ApiRegistrationApi {

    private final ApiRegistry apiRegistry;

    public ApiRegistrationImpl(ApiRegistry apiRegistry) {
        this.apiRegistry = apiRegistry;
    }

    @Override
    public void registerController(Object instance) {
        try {
            apiRegistry.register(instance);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new ApiException("E_REGISTER", "exception.api.register-fail", "注册控制器失败: {0}", ex, ex.getMessage()));
        }
    }

    @Override
    public void registerController(Object instance, Plugin owner) {
        try {
            apiRegistry.register(owner, instance);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new ApiException("E_REGISTER", "exception.api.register-fail-owner", "注册控制器失败(owner={0}): {1}", ex, owner, ex.getMessage()));
        }
    }

    @Override
    public void registerController(Object instance, boolean force) {
        try {
            apiRegistry.register(instance, force);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new ApiException("E_REGISTER", "exception.api.register-fail-force", "注册控制器失败(force={0}): {1}", ex, force, ex.getMessage()));
        }
    }

    @Override
    public void registerController(Object instance, Plugin owner, boolean force) {
        try {
            apiRegistry.register(owner, instance, force);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new ApiException("E_REGISTER", "exception.api.register-fail-owner-force", "注册控制器失败(owner={0}, force={1}): {2}", ex, owner, force, ex.getMessage()));
        }
    }

    @Override
    public void registerProxyController(Object instance) {
        try {
            apiRegistry.registerProxy(instance);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new ApiException("E_REGISTER_PROXY", "exception.api.register-proxy-fail", "代理注册控制器失败: {0}", ex, ex.getMessage()));
        }
    }

    @Override
    public void registerProxyController(Object instance, Plugin owner) {
        try {
            apiRegistry.registerProxy(owner, instance);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new ApiException("E_REGISTER_PROXY", "exception.api.register-proxy-fail-owner", "代理注册控制器失败(owner={0}): {1}", ex, owner, ex.getMessage()));
        }
    }

    @Override
    public void registerProxyController(Object instance, boolean force) {
        try {
            apiRegistry.registerProxy(instance, force);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new ApiException("E_REGISTER_PROXY", "exception.api.register-proxy-fail-force", "代理注册控制器失败(force={0}): {1}", ex, force, ex.getMessage()));
        }
    }

    @Override
    public void registerProxyController(Object instance, Plugin owner, boolean force) {
        try {
            apiRegistry.registerProxy(owner, instance, force);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new ApiException("E_REGISTER_PROXY", "exception.api.register-proxy-fail-owner-force", "代理注册控制器失败(owner={0}, force={1}): {2}", ex, owner, force, ex.getMessage()));
        }
    }

    @Override
    public List<ApiInfo> unregisterController(Object instance) {
        try {
            return apiRegistry.unregister(instance);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new ApiException("E_UNREGISTER", "exception.api.unregister-fail", "卸载控制器失败: {0}", ex, ex.getMessage()));
        }
    }

    @Override
    public List<ApiInfo> unregisterPluginControllers(String pluginName) {
        try {
            return apiRegistry.unregisterPlugin(pluginName);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new ApiException("E_UNREGISTER", "exception.api.unregister-plugin-fail", "卸载插件端点失败(plugin={0}): {1}", ex, pluginName, ex.getMessage()));
        }
    }

    @Override
    public void setPermissionService(PermissionService ps) {
        try {
            apiRegistry.setPermissionService(ps);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new ApiException("E_PERMISSION", "exception.api.permission-fail", "接入权限服务失败: {0}", ex, ex.getMessage()));
        }
    }

    @Override
    public PermissionService getPermissionService() {
        return apiRegistry.getPermissionService();
    }

    @Override
    public String getApiPrefix() {
        return apiRegistry.getPathPrefix();
    }

    @Override
    public List<ApiInfo> getRegisteredApis() {
        return apiRegistry.listEndpoints();
    }
}
