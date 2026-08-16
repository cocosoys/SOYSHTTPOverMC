package soys.soyshttpovermc.api.impl;

import org.bukkit.plugin.Plugin;

import soys.soyshttpovermc.api.WebPageApi;
import soys.soyshttpovermc.exception.ExceptionBus;
import soys.soyshttpovermc.exception.WebPageException;
import soys.soyshttpovermc.web.NavRegistry;
import soys.soyshttpovermc.web.WebRegistry;

import java.io.File;

/**
 * 能力组 2：网页登记（委托 {@link WebRegistry}）。
 * 由 {@link SoysHttpOverMcApiImpl} 组合并对外暴露；实现 {@link WebPageApi}。
 */
public class WebPageImpl implements WebPageApi {

    private final WebRegistry webRegistry;
    private final NavRegistry navRegistry;

    public WebPageImpl(WebRegistry webRegistry, NavRegistry navRegistry) {
        this.webRegistry = webRegistry;
        this.navRegistry = navRegistry;
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
        try {
            webRegistry.registerPage(owner, path, content, contentType);
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
        try {
            webRegistry.registerResource(owner, path, resourceClassLoader, resourcePath, contentType);
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
    public void registerNavItem(Plugin owner, String label, String path) {
        registerNavItem(owner, label, path, null, null, 100);
    }

    @Override
    public void registerNavItem(Plugin owner, String label, String path, String icon, String permission, int order) {
        try {
            navRegistry.register(new NavRegistry.NavItem(
                    owner == null ? null : owner.getName(), label, path, icon, permission, order));
        } catch (Exception ex) {
            throw ExceptionBus.fire(new WebPageException("E_NAV", "登记导航项失败(label=" + label + "): " + ex.getMessage(), ex));
        }
    }
}
