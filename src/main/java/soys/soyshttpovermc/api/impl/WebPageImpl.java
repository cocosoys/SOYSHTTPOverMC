package soys.soyshttpovermc.api.impl;

import org.bukkit.plugin.Plugin;

import soys.soyshttpovermc.api.WebPageApi;
import soys.soyshttpovermc.exception.ExceptionBus;
import soys.soyshttpovermc.exception.WebPageException;
import soys.soyshttpovermc.log.LogKit;
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
            throw ExceptionBus.fire(new WebPageException("E_PAGE", "登记网页失败(path=" + path + "): " + ex.getMessage(), ex));
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
            throw ExceptionBus.fire(new WebPageException("E_PAGE", "登记网页失败(path=" + path + "): " + ex.getMessage(), ex));
        }
    }

    @Override
    public void registerResource(Plugin owner, String path, ClassLoader resourceClassLoader, String resourcePath) {
        try {
            webRegistry.registerResource(owner, path, resourceClassLoader, resourcePath);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new WebPageException("E_RESOURCE", "登记资源失败(path=" + path + "): " + ex.getMessage(), ex));
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
            throw ExceptionBus.fire(new WebPageException("E_RESOURCE", "登记资源失败(path=" + path + "): " + ex.getMessage(), ex));
        }
    }

    @Override
    public void registerProxyPage(Plugin owner, String path, byte[] content) {
        try {
            webRegistry.registerProxyPage(owner, path, content);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new WebPageException("E_PAGE_PROXY", "代理登记网页失败(path=" + path + "): " + ex.getMessage(), ex));
        }
    }

    @Override
    public void registerProxyPage(Plugin owner, String path, byte[] content, String contentType) {
        try {
            webRegistry.registerProxyPage(owner, path, content, contentType);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new WebPageException("E_PAGE_PROXY", "代理登记网页失败(path=" + path + "): " + ex.getMessage(), ex));
        }
    }

    @Override
    public void registerProxyResource(Plugin owner, String path, ClassLoader resourceClassLoader, String resourcePath) {
        try {
            webRegistry.registerProxyResource(owner, path, resourceClassLoader, resourcePath);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new WebPageException("E_RESOURCE_PROXY", "代理登记资源失败(path=" + path + "): " + ex.getMessage(), ex));
        }
    }

    @Override
    public void registerProxyResource(Plugin owner, String path, ClassLoader resourceClassLoader, String resourcePath, String contentType) {
        try {
            webRegistry.registerProxyResource(owner, path, resourceClassLoader, resourcePath, contentType);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new WebPageException("E_RESOURCE_PROXY", "代理登记资源失败(path=" + path + "): " + ex.getMessage(), ex));
        }
    }

    @Override
    public void unregisterPluginPages(String pluginName) {
        try {
            webRegistry.unregisterPlugin(pluginName);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new WebPageException("E_UNREGISTER", "卸载网页失败(plugin=" + pluginName + "): " + ex.getMessage(), ex));
        }
    }

    @Override
    public void registerDirectory(Plugin owner, String basePath, File dir) {
        try {
            webRegistry.registerDirectory(owner, basePath, dir);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new WebPageException("E_DIR", "批量登记目录失败(path=" + basePath + "): " + ex.getMessage(), ex));
        }
    }

    @Override
    public void registerDirectory(Plugin owner, String basePath, File dir, boolean proxy) {
        try {
            webRegistry.registerDirectory(owner, basePath, dir, proxy);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new WebPageException("E_DIR", "批量登记目录失败(path=" + basePath + "): " + ex.getMessage(), ex));
        }
    }

    @Override
    public void registerResourceDirectory(Plugin owner, String basePath, ClassLoader resourceClassLoader, String resourceRoot) {
        try {
            webRegistry.registerResourceDirectory(owner, basePath, resourceClassLoader, resourceRoot);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new WebPageException("E_DIR_RES", "批量登记 jar 目录失败(root=" + resourceRoot + "): " + ex.getMessage(), ex));
        }
    }

    @Override
    public void registerResourceDirectory(Plugin owner, String basePath, ClassLoader resourceClassLoader, String resourceRoot, boolean proxy) {
        try {
            webRegistry.registerResourceDirectory(owner, basePath, resourceClassLoader, resourceRoot, proxy);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new WebPageException("E_DIR_RES", "批量登记 jar 目录失败(root=" + resourceRoot + "): " + ex.getMessage(), ex));
        }
    }

    @Override
    public void registerHome(Plugin owner, byte[] content) {
        registerHome(owner, content, "text/html; charset=utf-8");
    }

    @Override
    public void registerHome(Plugin owner, byte[] content, String contentType) {
        if (content == null || content.length == 0) return;
        try {
            // 委托 WebRegistry.registerHome：注册到 "/"（代理无前缀），路由先于静态资源命中 → 立即覆盖首页
            webRegistry.registerHome(owner, content, contentType);
            // 明确日志：插件已注册首页并覆盖原首页（后台可见）
            LogKit.info("[HTTP-Over-MC] 插件 " + (owner == null ? "?" : owner.getName())
                    + " 已注册首页并覆盖原首页（GET /，立即生效，优先级高于 web.home 与默认 index.html）");
        } catch (Exception ex) {
            throw ExceptionBus.fire(new WebPageException("E_HOME", "注册首页失败: " + ex.getMessage(), ex));
        }
    }

    @Override
    public void registerHome(Plugin owner, String source) {
        registerHome(owner, source, null);
    }

    @Override
    public void registerHome(Plugin owner, String source, String contentType) {
        if (source == null || source.trim().isEmpty()) return;
        try {
            webRegistry.registerHomeFrom(owner == null ? null : owner.getName(), source.trim(), contentType);
            LogKit.info("[HTTP-Over-MC] 插件 " + (owner == null ? "?" : owner.getName())
                    + " 已注册首页(来源)并覆盖原首页（GET /，source=" + source.trim() + "，立即生效）");
        } catch (Exception ex) {
            throw ExceptionBus.fire(new WebPageException("E_HOME", "注册首页(来源)失败: " + ex.getMessage(), ex));
        }
    }

    @Override
    public void registerNetworkPage(Plugin owner, NetworkPage page) {
        try {
            webRegistry.registerNetworkPage(owner == null ? null : owner.getName(), page);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new WebPageException("E_NET_PAGE",
                    "登记网络页失败(name=" + (page == null ? "?" : page.name()) + "): " + ex.getMessage(), ex));
        }
    }

    @Override
    public void registerNetworkTransport(NetworkTransport transport) {
        if (transport == null) return;
        networkTransports.add(transport);
        // 预留接口：仅占位存储，暂不接入加载链路（网络页传输仍由 NetworkPage.load() 自行实现）
        LogKit.warn("[HTTP-Over-MC] registerNetworkTransport 为预留接口（暂不接入加载链路）: " + transport.name());
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
            throw ExceptionBus.fire(new WebPageException("E_ERR_PAGE", "登记错误页失败(status=" + status + "): " + ex.getMessage(), ex));
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
