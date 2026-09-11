package com.github.cocosoys.mc.soyshttpovermc.api.impl;

import com.github.cocosoys.mc.soyshttpovermc.HttpOverMcPlugin;
import com.github.cocosoys.mc.soyshttpovermc.api.WebPageApi;
import com.github.cocosoys.mc.soyshttpovermc.exception.ExceptionBus;
import com.github.cocosoys.mc.soyshttpovermc.exception.WebPageException;
import com.github.cocosoys.mc.soyshttpovermc.web.*;
import lombok.CustomLog;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 能力组 2：网页登记（委托 {@link WebRegistry}）。
 * 由 {@link SoysHttpOverMcApiImpl} 组合并对外暴露；实现 {@link WebPageApi}。
 */
@CustomLog
public class WebPageImpl implements WebPageApi {

    private final WebRegistry webRegistry;
    private final LargeFileLoaderRegistry largeLoaderRegistry;
    private final CorsRegistry corsRegistry;
    /**
     * 网络传输提供者（预留：暂不接入加载链路，仅占位存储）。
     */
    private final List<NetworkTransport> networkTransports = new CopyOnWriteArrayList<>();
    /**
     * 极简自动登记：插件名 → 独立自增序号（page-1、page-2 …）。
     */
    private final Map<String, AtomicInteger> autoSeq = new ConcurrentHashMap<>();

    /**
     * 极简登记默认 Content-Type。
     */
    private static final String DEFAULT_CONTENT_TYPE = "text/html; charset=utf-8";

    /**
     * 极简自动登记命名空间：{@code web/plugins/<插件>/page/}（无 /plugins 前缀的代理空间，避免与手动登记冲突）。
     */
    private static final String AUTO_BASE_PREFIX = "web/plugins/";
    private static final String AUTO_PAGE_DIR = "/page/";

    public WebPageImpl(WebRegistry webRegistry,
                       LargeFileLoaderRegistry largeLoaderRegistry, CorsRegistry corsRegistry) {
        this.webRegistry = webRegistry;
        this.largeLoaderRegistry = largeLoaderRegistry;
        this.corsRegistry = corsRegistry;
    }

    @Override
    public WebRegistry.Entry registerPage(Plugin owner, String path, byte[] content) {
        try {
            return webRegistry.registerPage(owner, path, content);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new WebPageException("E_PAGE", "exception.web.register-page", "登记网页失败(path={0}): {1}", ex, path, ex.getMessage()));
        }
    }

    @Override
    public WebRegistry.Entry registerPage(Plugin owner, String path, byte[] content, String contentType) {
        return registerPage(owner, path, content, contentType, false);
    }

    @Override
    public WebRegistry.Entry registerPage(Plugin owner, String path, byte[] content, String contentType, boolean force) {
        try {
            return webRegistry.registerPage(owner, path, content, contentType, force);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new WebPageException("E_PAGE", "exception.web.register-page", "登记网页失败(path={0}): {1}", ex, path, ex.getMessage()));
        }
    }

    @Override
    public WebRegistry.Entry registerPage(Plugin owner, String path, String httpMethod, byte[] content, String contentType, boolean force,
                             String description, List<String> nicknames) {
        try {
            return webRegistry.registerPage(owner, path, httpMethod, content, contentType, force, description, nicknames);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new WebPageException("E_PAGE", "exception.web.register-page", "登记网页失败(path={0}): {1}", ex, path, ex.getMessage()));
        }
    }

    @Override
    public WebRegistry.Entry registerResource(Plugin owner, String path, ClassLoader resourceClassLoader, String resourcePath) {
        try {
            return webRegistry.registerResource(owner, path, resourceClassLoader, resourcePath);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new WebPageException("E_RESOURCE", "exception.web.register-resource", "登记资源失败(path={0}): {1}", ex, path, ex.getMessage()));
        }
    }

    @Override
    public WebRegistry.Entry registerResource(Plugin owner, String path, ClassLoader resourceClassLoader, String resourcePath, String contentType) {
        return registerResource(owner, path, resourceClassLoader, resourcePath, contentType, false);
    }

    @Override
    public WebRegistry.Entry registerResource(Plugin owner, String path, ClassLoader resourceClassLoader, String resourcePath, String contentType, boolean force) {
        try {
            return webRegistry.registerResource(owner, path, resourceClassLoader, resourcePath, contentType, force);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new WebPageException("E_RESOURCE", "exception.web.register-resource", "登记资源失败(path={0}): {1}", ex, path, ex.getMessage()));
        }
    }

    @Override
    public WebRegistry.Entry registerResource(Plugin owner, String path, String httpMethod, ClassLoader resourceClassLoader, String resourcePath,
                                 String contentType, boolean force, String description, List<String> nicknames) {
        try {
            return webRegistry.registerResource(owner, path, httpMethod, resourceClassLoader, resourcePath, contentType, force, description, nicknames);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new WebPageException("E_RESOURCE", "exception.web.register-resource", "登记资源失败(path={0}): {1}", ex, path, ex.getMessage()));
        }
    }

    @Override
    public WebRegistry.Entry registerProxyPage(Plugin owner, String path, byte[] content) {
        try {
            return webRegistry.registerProxyPage(owner, path, content);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new WebPageException("E_PAGE_PROXY", "exception.web.register-proxy-page", "代理登记网页失败(path={0}): {1}", ex, path, ex.getMessage()));
        }
    }

    @Override
    public WebRegistry.Entry registerProxyPage(Plugin owner, String path, byte[] content, String contentType) {
        try {
            return webRegistry.registerProxyPage(owner, path, content, contentType);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new WebPageException("E_PAGE_PROXY", "exception.web.register-proxy-page", "代理登记网页失败(path={0}): {1}", ex, path, ex.getMessage()));
        }
    }

    @Override
    public WebRegistry.Entry registerProxyPage(Plugin owner, String path, String httpMethod, byte[] content, String contentType, boolean force,
                                  String description, List<String> nicknames) {
        try {
            return webRegistry.registerProxyPage(owner, path, httpMethod, content, contentType, force, description, nicknames);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new WebPageException("E_PAGE_PROXY", "exception.web.register-proxy-page", "代理登记网页失败(path={0}): {1}", ex, path, ex.getMessage()));
        }
    }

    @Override
    public WebRegistry.Entry registerProxyResource(Plugin owner, String path, ClassLoader resourceClassLoader, String resourcePath) {
        try {
            return webRegistry.registerProxyResource(owner, path, resourceClassLoader, resourcePath);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new WebPageException("E_RESOURCE_PROXY", "exception.web.register-proxy-resource", "代理登记资源失败(path={0}): {1}", ex, path, ex.getMessage()));
        }
    }

    @Override
    public WebRegistry.Entry registerProxyResource(Plugin owner, String path, ClassLoader resourceClassLoader, String resourcePath, String contentType) {
        try {
            return webRegistry.registerProxyResource(owner, path, resourceClassLoader, resourcePath, contentType);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new WebPageException("E_RESOURCE_PROXY", "exception.web.register-proxy-resource", "代理登记资源失败(path={0}): {1}", ex, path, ex.getMessage()));
        }
    }

    @Override
    public WebRegistry.Entry registerProxyResource(Plugin owner, String path, String httpMethod, ClassLoader resourceClassLoader, String resourcePath,
                                      String contentType, boolean force, String description, List<String> nicknames) {
        try {
            return webRegistry.registerProxyResource(owner, path, httpMethod, resourceClassLoader, resourcePath, contentType, force, description, nicknames);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new WebPageException("E_RESOURCE_PROXY", "exception.web.register-proxy-resource", "代理登记资源失败(path={0}): {1}", ex, path, ex.getMessage()));
        }
    }

    @Override
    public void unregisterPluginPages(String pluginName) {
        try {
            webRegistry.unregisterPlugin(pluginName);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new WebPageException("E_UNREGISTER", "exception.web.unregister", "卸载网页失败(plugin={0}): {1}", ex, pluginName, ex.getMessage()));
        }
    }

    @Override
    public java.util.Set<WebRegistry.Entry> registerDirectory(Plugin owner, String basePath, File dir) {
        try {
            return webRegistry.registerDirectory(owner, basePath, dir);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new WebPageException("E_DIR", "exception.web.register-dir", "批量登记目录失败(path={0}): {1}", ex, basePath, ex.getMessage()));
        }
    }

    @Override
    public java.util.Set<WebRegistry.Entry> registerDirectory(Plugin owner, String basePath, File dir, boolean proxy) {
        try {
            return webRegistry.registerDirectory(owner, basePath, dir, proxy);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new WebPageException("E_DIR", "exception.web.register-dir", "批量登记目录失败(path={0}): {1}", ex, basePath, ex.getMessage()));
        }
    }

    @Override
    public java.util.Set<WebRegistry.Entry> registerResourceDirectory(Plugin owner, String basePath, ClassLoader resourceClassLoader, String resourceRoot) {
        try {
            return webRegistry.registerResourceDirectory(owner, basePath, resourceClassLoader, resourceRoot);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new WebPageException("E_DIR_RES", "exception.web.register-resource-dir", "批量登记 jar 目录失败(root={0}): {1}", ex, resourceRoot, ex.getMessage()));
        }
    }

    @Override
    public java.util.Set<WebRegistry.Entry> registerResourceDirectory(Plugin owner, String basePath, ClassLoader resourceClassLoader, String resourceRoot, boolean proxy) {
        try {
            return webRegistry.registerResourceDirectory(owner, basePath, resourceClassLoader, resourceRoot, proxy);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new WebPageException("E_DIR_RES", "exception.web.register-resource-dir", "批量登记 jar 目录失败(root={0}): {1}", ex, resourceRoot, ex.getMessage()));
        }
    }

    // ===== 极简自动登记（owner 沿调用栈自动识别；失败返回 null/0/false，不抛异常）=====

    @Override
    public WebRegistry.Entry registerPage(byte[] content) {
        try {
            if (content == null || content.length == 0) return null;
            Plugin owner = resolveOwner();
            if (owner == null) return null;
            String path = autoBase(owner) + nextAutoName(owner.getName());
            return webRegistry.registerProxyPage(owner, path, content, DEFAULT_CONTENT_TYPE);
        } catch (Exception ex) {
            log.warnT("log.web.auto-register-page-failed", "极简登记网页失败: {0}", ex.getMessage());
            return null;
        }
    }

    @Override
    public WebRegistry.Entry registerPage(String resourcePath) {
        try {
            Plugin owner = resolveOwner();
            if (owner == null || resourcePath == null || resourcePath.isEmpty()) return null;
            byte[] content = readResource(owner, resourcePath);
            if (content == null) return null;
            String path = autoBase(owner) + fileNameNoExt(resourcePath);
            return webRegistry.registerProxyPage(owner, path, content, MimeTypes.forPath(resourcePath));
        } catch (Exception ex) {
            log.warnT("log.web.auto-register-resource-page-failed", "极简登记资源网页失败: {0}", ex.getMessage());
            return null;
        }
    }

    @Override
    public WebRegistry.Entry registerPage(String path, byte[] content) {
        try {
            Plugin owner = resolveOwner();
            if (owner == null || path == null || content == null) return null;
            return webRegistry.registerPage(owner, path, content);
        } catch (Exception ex) {
            log.warnT("log.web.auto-register-page-path-failed", "极简登记网页失败(path={0}): {1}", path, ex.getMessage());
            return null;
        }
    }

    @Override
    public WebRegistry.Entry registerResource(String resourcePath) {
        try {
            Plugin owner = resolveOwner();
            if (owner == null || resourcePath == null || resourcePath.isEmpty()) return null;
            String path = autoBase(owner) + fileNameNoExt(resourcePath);
            return webRegistry.registerProxyResource(owner, path,
                    owner.getClass().getClassLoader(), resourcePath, MimeTypes.forPath(resourcePath));
        } catch (Exception ex) {
            log.warnT("log.web.auto-register-resource-failed", "极简登记资源失败: {0}", ex.getMessage());
            return null;
        }
    }

    @Override
    public WebRegistry.Entry registerResource(String path, String resourcePath) {
        try {
            Plugin owner = resolveOwner();
            if (owner == null || path == null || resourcePath == null || resourcePath.isEmpty()) return null;
            return webRegistry.registerResource(owner, path,
                    owner.getClass().getClassLoader(), resourcePath);
        } catch (Exception ex) {
            log.warnT("log.web.auto-register-resource-path-failed", "极简登记资源失败(path={0}): {1}", path, ex.getMessage());
            return null;
        }
    }

    @Override
    public WebRegistry.Entry registerProxyPage(String path, byte[] content) {
        try {
            Plugin owner = resolveOwner();
            if (owner == null || path == null || content == null) return null;
            return webRegistry.registerProxyPage(owner, path, content);
        } catch (Exception ex) {
            log.warnT("log.web.auto-register-proxy-page-failed", "极简代理登记网页失败(path={0}): {1}", path, ex.getMessage());
            return null;
        }
    }

    @Override
    public WebRegistry.Entry registerProxyResource(String path, String resourcePath) {
        try {
            Plugin owner = resolveOwner();
            if (owner == null || path == null || resourcePath == null || resourcePath.isEmpty()) return null;
            return webRegistry.registerProxyResource(owner, path,
                    owner.getClass().getClassLoader(), resourcePath);
        } catch (Exception ex) {
            log.warnT("log.web.auto-register-proxy-resource-failed", "极简代理登记资源失败(path={0}): {1}", path, ex.getMessage());
            return null;
        }
    }

    @Override
    public WebRegistry.Entry registerErrorPage(byte[] content) {
        try {
            Plugin owner = resolveOwner();
            if (owner == null) return null;
            return webRegistry.registerErrorPage(owner.getName(), 404, content);
        } catch (Exception ex) {
            log.warnT("log.web.auto-register-error-page-failed", "极简登记错误页失败: {0}", ex.getMessage());
            return null;
        }
    }

    @Override
    public WebRegistry.Entry registerErrorPage(String html) {
        return registerErrorPage(html == null ? new byte[0] : html.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public java.util.Set<WebRegistry.Entry> registerDirectory(File dir) {
        try {
            Plugin owner = resolveOwner();
            if (owner == null || dir == null || !dir.isDirectory()) return null;
            return webRegistry.registerDirectory(owner, AUTO_BASE_PREFIX + owner.getName() + "/" + dir.getName(), dir, true);
        } catch (Exception ex) {
            log.warnT("log.web.auto-register-dir-failed", "极简批量登记目录失败: {0}", ex.getMessage());
            return null;
        }
    }

    // ===== 极简自动登记：内部工具 =====

    /**
     * 沿调用栈识别调用插件：跳过本插件自身帧，找第一个由插件 ClassLoader 加载的类，
     * 用 {@link JavaPlugin#getProvidingPlugin(Class)} 反查所属插件；识别失败回退主插件。
     */
    private Plugin resolveOwner() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement el : stack) {
            String cn = el.getClassName();
            if (cn == null || cn.startsWith("com.github.cocosoys.mc.soyshttpovermc.")) continue;
            try {
                Class<?> c = Class.forName(cn);
                return JavaPlugin.getProvidingPlugin(c);
            } catch (Throwable ignored) {
                // 非插件加载的类（JDK / 库），继续向上找
            }
        }
        HttpOverMcPlugin self = HttpOverMcPlugin.getInstance();
        if (self != null) {
            log.warnT("log.web.auto-owner-fallback", "极简登记无法识别调用插件，回退主插件");
        }
        return self;
    }

    /**
     * 极简自动登记路径前缀：{@code web/plugins/<插件>/page/}（无前导斜杠，走代理无前缀空间）。
     */
    private static String autoBase(Plugin owner) {
        return AUTO_BASE_PREFIX + owner.getName() + AUTO_PAGE_DIR;
    }

    /**
     * 每插件独立自增序号：page-1、page-2 …
     */
    private String nextAutoName(String pluginName) {
        return "page-" + autoSeq.computeIfAbsent(pluginName, k -> new AtomicInteger()).incrementAndGet();
    }

    /**
     * 取资源路径的文件名（不含最后一个扩展名）：{@code web/index.html} → {@code index}；{@code dist/status/index.html} → {@code index}。
     * 隐藏文件（如 {@code .well-known}）保留原名。
     */
    private static String fileNameNoExt(String resourcePath) {
        String p = resourcePath;
        int slash = p.lastIndexOf('/');
        if (slash >= 0) p = p.substring(slash + 1);
        int dot = p.lastIndexOf('.');
        if (dot > 0) p = p.substring(0, dot);
        return p.isEmpty() ? "index" : p;
    }

    /**
     * 从插件 ClassLoader 读取 jar 资源全部字节；资源不存在/读取失败返回 null。
     */
    private static byte[] readResource(Plugin owner, String resourcePath) {
        try (InputStream in = owner.getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public WebRegistry.Entry registerNetworkPage(Plugin owner, NetworkPage page) {
        try {
            return webRegistry.registerNetworkPage(owner == null ? null : owner.getName(), page);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new WebPageException("E_NET_PAGE",
                    "exception.web.register-net-page", "登记网络页失败(name={0}): {1}", ex, (page == null ? "?" : page.name()), ex.getMessage()));
        }
    }

    @Override
    public NetworkTransport registerNetworkTransport(NetworkTransport transport) {
        if (transport == null) return null;
        networkTransports.add(transport);
        // 预留接口：仅占位存储，暂不接入加载链路（网络页传输仍由 NetworkPage.load() 自行实现）
        log.warnT("log.web.transport-reserved", "registerNetworkTransport 为预留接口（暂不接入加载链路）: {0}", transport.name());
        return transport;
    }

    // ===== 大文件加载抽象（LargeFileLoader）=====

    @Override
    public LargeFileLoader registerLargeFileLoader(LargeFileLoader loader) {
        return largeLoaderRegistry == null ? null : largeLoaderRegistry.register(loader);
    }

    @Override
    public LargeFileLoader setDefaultLargeFileLoader(String loaderName) {
        return largeLoaderRegistry == null ? null : largeLoaderRegistry.setDefault(loaderName);
    }

    @Override
    public LargeFileLoader setLargeFileLoader(String pathPrefix, String loaderName) {
        return largeLoaderRegistry == null ? null : largeLoaderRegistry.setPathLoader(pathPrefix, loaderName);
    }

    @Override
    public WebRegistry.Entry registerErrorPage(Plugin owner, int status, byte[] content) {
        try {
            return webRegistry.registerErrorPage(owner == null ? null : owner.getName(), status, content);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new WebPageException("E_ERR_PAGE", "exception.web.register-error-page", "登记错误页失败(status={0}): {1}", ex, status, ex.getMessage()));
        }
    }

    @Override
    public WebRegistry.Entry registerErrorPage(Plugin owner, int status, String html) {
        return registerErrorPage(owner, status, html == null ? new byte[0] : html.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public CorsRegistry.CorsEntry registerCors(Plugin owner, String pathPrefix, String origin, String methods,
                                String headers, boolean credentials) {
        if (corsRegistry == null) return null;
        return corsRegistry.register(owner == null ? null : owner.getName(), pathPrefix, origin, methods, headers, credentials);
    }
}
