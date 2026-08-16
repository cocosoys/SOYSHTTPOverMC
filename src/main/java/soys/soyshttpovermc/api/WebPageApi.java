package soys.soyshttpovermc.api;

import org.bukkit.plugin.Plugin;

import java.io.File;

import soys.soyshttpovermc.web.NavRegistry;

/**
 * 能力组 2：网页登记（委托 {@link WebRegistry}）。
 * 由 {@link SoysHttpOverMcApi#getWebPage()} 跳转获取。
 */
public interface WebPageApi {

    void registerPage(Plugin owner, String path, byte[] content);

    void registerPage(Plugin owner, String path, byte[] content, String contentType);

    void registerResource(Plugin owner, String path, ClassLoader resourceClassLoader, String resourcePath);

    void registerResource(Plugin owner, String path, ClassLoader resourceClassLoader, String resourcePath, String contentType);

    void registerProxyPage(Plugin owner, String path, byte[] content);

    void registerProxyPage(Plugin owner, String path, byte[] content, String contentType);

    void registerProxyResource(Plugin owner, String path, ClassLoader resourceClassLoader, String resourcePath);

    void registerProxyResource(Plugin owner, String path, ClassLoader resourceClassLoader, String resourcePath, String contentType);

    /** 批量登记磁盘目录（递归扫描 dir 下文件挂到 basePath；请求时惰性读，支持热替换） */
    void registerDirectory(Plugin owner, String basePath, File dir);

    /** 批量登记磁盘目录（显式是否强制代理无前缀） */
    void registerDirectory(Plugin owner, String basePath, File dir, boolean proxy);

    /** 批量登记插件 jar 内资源目录（扫描 resourceRoot 前缀下全部条目挂到 basePath） */
    void registerResourceDirectory(Plugin owner, String basePath, ClassLoader resourceClassLoader, String resourceRoot);

    /** 批量登记插件 jar 内资源目录（显式是否强制代理无前缀） */
    void registerResourceDirectory(Plugin owner, String basePath, ClassLoader resourceClassLoader, String resourceRoot, boolean proxy);

    /** 登记门户导航项（默认 order=100，无图标/权限） */
    void registerNavItem(Plugin owner, String label, String path);

    /** 登记门户导航项（含图标/权限/排序） */
    void registerNavItem(Plugin owner, String label, String path, String icon, String permission, int order);

    /** 卸载指定插件名登记的全部网页 */
    void unregisterPluginPages(String pluginName);
}
