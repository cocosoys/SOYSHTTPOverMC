package soys.soyshttpovermc.api;

import org.bukkit.plugin.Plugin;

import soys.soyshttpovermc.annotations.PermissionService;
import soys.soyshttpovermc.api.event.ApiInfo;

import java.util.List;

/**
 * 能力组 1：注解式 API 注册（委托 {@link ApiRegistry}）。
 * 由 {@link SoysHttpOverMcApi#getApiRegistration()} 跳转获取。
 */
public interface ApiRegistrationApi {

    /** 注册注解式控制器（自动标记所属插件；非主插件自动加 /plugins/&lt;插件名&gt; 前缀） */
    void registerController(Object instance);

    /** 注册注解式控制器并显式指定所属插件 */
    void registerController(Object instance, Plugin owner);

    /** 强制以主插件代理注册（无 /plugins/&lt;插件名&gt; 前缀，ownerPlugin 仍标记真实插件） */
    void registerProxyController(Object instance);

    /** 强制以主插件代理注册并显式指定所属插件 */
    void registerProxyController(Object instance, Plugin owner);

    /** 卸载某控制器实例注册的全部端点 */
    List<ApiInfo> unregisterController(Object instance);

    /** 卸载指定插件名注册的全部端点 */
    List<ApiInfo> unregisterPluginControllers(String pluginName);

    /** 接入权限判定服务（@ApiPermission 生效的前提） */
    void setPermissionService(PermissionService ps);

    /** 读取当前权限判定服务 */
    PermissionService getPermissionService();

    /** 网关全局 API 前缀（如 /api） */
    String getApiPrefix();

    /** 当前已注册的全部端点快照 */
    List<ApiInfo> getRegisteredApis();
}
