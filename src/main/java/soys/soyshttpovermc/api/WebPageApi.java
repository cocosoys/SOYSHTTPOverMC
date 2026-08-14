package soys.soyshttpovermc.api;

import org.bukkit.plugin.Plugin;

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

    /** 卸载指定插件名登记的全部网页 */
    void unregisterPluginPages(String pluginName);
}
