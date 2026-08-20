package soys.soyshttpovermc.api.impl;

import lombok.CustomLog;
import org.bukkit.plugin.Plugin;

import soys.soyshttpovermc.api.WebPageApi;
import soys.soyshttpovermc.exception.ExceptionBus;
import soys.soyshttpovermc.exception.WebPageException;
import soys.soyshttpovermc.log.LogKit;
import soys.soyshttpovermc.i18n.I18n;
import soys.soyshttpovermc.web.CorsRegistry;
import soys.soyshttpovermc.web.LargeFileLoader;
import soys.soyshttpovermc.web.LargeFileLoaderRegistry;
import soys.soyshttpovermc.web.NetworkPage;
import soys.soyshttpovermc.web.NetworkTransport;
import soys.soyshttpovermc.web.WebRegistry;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 能力组 2：网页登记（委托 {@link WebRegistry}）。
 * 由 {@link SoysHttpOverMcApiImpl} 组合并对外暴露；实现 {@link WebPageApi}。
 */
@CustomLog
public class WebPageImpl implements WebPageApi {

    private final WebRegistry webRegistry;
    private final LargeFileLoaderRegistry largeLoaderRegistry;
    private final CorsRegistry corsRegistry;
    /** 网络传输提供者（预留：暂不接入加载链路，仅占位存储）。 */
    private final List<NetworkTransport> networkTransports = new CopyOnWriteArrayList<>();

    public WebPageImpl(WebRegistry webRegistry,
                       LargeFileLoaderRegistry largeLoaderRegistry, CorsRegistry corsRegistry) {
        this.webRegistry = webRegistry;
        this.largeLoaderRegistry = largeLoaderRegistry;
        this.corsRegistry = corsRegistry;
    }

    @Override
    public void registerPage(Plugin owner, String path, byte[] content) {
        try {
            webRegistry.registerPage(owner, path, content);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new WebPageException("E_PAGE", I18n.t("exception.web.register-page", "登记网页失败(path={0}): {1}", path, ex.getMessage()), ex));
        }
    }

    @Override
    public void registerPage(Plugin owner, String path, byte[] content, String contentType) {
        registerPage(owner, path, content, contentType, false);
    }

    @Override
    public void registerPage(Plugin owner, String path, byte[] content, String contentType, boolean force) {
        try {
            webRegistry.registerPage(owner, path, content, contentType, force);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new WebPageException("E_PAGE", I18n.t("exception.web.register-page", "登记网页失败(path={0}): {1}", path, ex.getMessage()), ex));
        }
    }

    @Override
    public void registerResource(Plugin owner, String path, ClassLoader resourceClassLoader, String resourcePath) {
        try {
            webRegistry.registerResource(owner, path, resourceClassLoader, resourcePath);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new WebPageException("E_RESOURCE", I18n.t("exception.web.register-resource", "登记资源失败(path={0}): {1}", path, ex.getMessage()), ex));
        }
    }

    @Override
    public void registerResource(Plugin owner, String path, ClassLoader resourceClassLoader, String resourcePath, String contentType) {
        registerResource(owner, path, resourceClassLoader, resourcePath, contentType, false);
    }

    @Override
    public void registerResource(Plugin owner, String path, ClassLoader resourceClassLoader, String resourcePath, String contentType, boolean force) {
        try {
            webRegistry.registerResource(owner, path, resourceClassLoader, resourcePath, contentType, force);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new WebPageException("E_RESOURCE", I18n.t("exception.web.register-resource", "登记资源失败(path={0}): {1}", path, ex.getMessage()), ex));
        }
    }

    @Override
    public void registerProxyPage(Plugin owner, String path, byte[] content) {
        try {
            webRegistry.registerProxyPage(owner, path, content);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new WebPageException("E_PAGE_PROXY", I18n.t("exception.web.register-proxy-page", "代理登记网页失败(path={0}): {1}", path, ex.getMessage()), ex));
        }
    }

    @Override
    public void registerProxyPage(Plugin owner, String path, byte[] content, String contentType) {
        try {
            webRegistry.registerProxyPage(owner, path, content, contentType);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new WebPageException("E_PAGE_PROXY", I18n.t("exception.web.register-proxy-page", "代理登记网页失败(path={0}): {1}", path, ex.getMessage()), ex));
        }
    }

    @Override
    public void registerProxyResource(Plugin owner, String path, ClassLoader resourceClassLoader, String resourcePath) {
        try {
            webRegistry.registerProxyResource(owner, path, resourceClassLoader, resourcePath);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new WebPageException("E_RESOURCE_PROXY", I18n.t("exception.web.register-proxy-resource", "代理登记资源失败(path={0}): {1}", path, ex.getMessage()), ex));
        }
    }

    @Override
    public void registerProxyResource(Plugin owner, String path, ClassLoader resourceClassLoader, String resourcePath, String contentType) {
        try {
            webRegistry.registerProxyResource(owner, path, resourceClassLoader, resourcePath, contentType);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new WebPageException("E_RESOURCE_PROXY", I18n.t("exception.web.register-proxy-resource", "代理登记资源失败(path={0}): {1}", path, ex.getMessage()), ex));
        }
    }

    @Override
    public void unregisterPluginPages(String pluginName) {
        try {
            webRegistry.unregisterPlugin(pluginName);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new WebPageException("E_UNREGISTER", I18n.t("exception.web.unregister", "卸载网页失败(plugin={0}): {1}", pluginName, ex.getMessage()), ex));
        }
    }

    @Override
    public void registerDirectory(Plugin owner, String basePath, File dir) {
        try {
            webRegistry.registerDirectory(owner, basePath, dir);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new WebPageException("E_DIR", I18n.t("exception.web.register-dir", "批量登记目录失败(path={0}): {1}", basePath, ex.getMessage()), ex));
        }
    }

    @Override
    public void registerDirectory(Plugin owner, String basePath, File dir, boolean proxy) {
        try {
            webRegistry.registerDirectory(owner, basePath, dir, proxy);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new WebPageException("E_DIR", I18n.t("exception.web.register-dir", "批量登记目录失败(path={0}): {1}", basePath, ex.getMessage()), ex));
        }
    }

    @Override
    public void registerResourceDirectory(Plugin owner, String basePath, ClassLoader resourceClassLoader, String resourceRoot) {
        try {
            webRegistry.registerResourceDirectory(owner, basePath, resourceClassLoader, resourceRoot);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new WebPageException("E_DIR_RES", I18n.t("exception.web.register-resource-dir", "批量登记 jar 目录失败(root={0}): {1}", resourceRoot, ex.getMessage()), ex));
        }
    }

    @Override
    public void registerResourceDirectory(Plugin owner, String basePath, ClassLoader resourceClassLoader, String resourceRoot, boolean proxy) {
        try {
            webRegistry.registerResourceDirectory(owner, basePath, resourceClassLoader, resourceRoot, proxy);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new WebPageException("E_DIR_RES", I18n.t("exception.web.register-resource-dir", "批量登记 jar 目录失败(root={0}): {1}", resourceRoot, ex.getMessage()), ex));
        }
    }

    @Override
    public void registerNetworkPage(Plugin owner, NetworkPage page) {
        try {
            webRegistry.registerNetworkPage(owner == null ? null : owner.getName(), page);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new WebPageException("E_NET_PAGE",
                    I18n.t("exception.web.register-net-page", "登记网络页失败(name={0}): {1}", (page == null ? "?" : page.name()), ex.getMessage()), ex));
        }
    }

    @Override
    public void registerNetworkTransport(NetworkTransport transport) {
        if (transport == null) return;
        networkTransports.add(transport);
        // 预留接口：仅占位存储，暂不接入加载链路（网络页传输仍由 NetworkPage.load() 自行实现）
        log.warn(I18n.t("log.web.transport-reserved", "registerNetworkTransport 为预留接口（暂不接入加载链路）: {0}", transport.name()));
    }

    // ===== 大文件加载抽象（LargeFileLoader）=====

    @Override
    public void registerLargeFileLoader(LargeFileLoader loader) {
        if (largeLoaderRegistry == null || loader == null) return;
        largeLoaderRegistry.register(loader);
    }

    @Override
    public boolean setDefaultLargeFileLoader(String loaderName) {
        return largeLoaderRegistry != null && largeLoaderRegistry.setDefault(loaderName);
    }

    @Override
    public void setLargeFileLoader(String pathPrefix, String loaderName) {
        if (largeLoaderRegistry == null) return;
        largeLoaderRegistry.setPathLoader(pathPrefix, loaderName);
    }

    @Override
    public void registerErrorPage(Plugin owner, int status, byte[] content) {
        try {
            webRegistry.registerErrorPage(owner == null ? null : owner.getName(), status, content);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new WebPageException("E_ERR_PAGE", I18n.t("exception.web.register-error-page", "登记错误页失败(status={0}): {1}", status, ex.getMessage()), ex));
        }
    }

    @Override
    public void registerErrorPage(Plugin owner, int status, String html) {
        registerErrorPage(owner, status, html == null ? new byte[0] : html.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void registerCors(Plugin owner, String pathPrefix, String origin, String methods,
                             String headers, boolean credentials) {
        if (corsRegistry == null) return;
        corsRegistry.register(owner == null ? null : owner.getName(), pathPrefix, origin, methods, headers, credentials);
    }
}
