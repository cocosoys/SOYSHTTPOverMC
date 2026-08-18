package soys.soyshttpovermc.api;

import org.bukkit.plugin.Plugin;

import java.io.File;

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

    /** 卸载指定插件名登记的全部网页 */
    void unregisterPluginPages(String pluginName);

    // ===== 大文件加载抽象（LargeFileLoader）=====

    /**
     * 注册一个自定义大文件加载器（实现 {@link LargeFileLoader}；同名覆盖）。
     * 默认所有超过 {@code web.large-file-threshold} 的大文件由内置流式加载器处理；
     * 注册后可用 {@link #setDefaultLargeFileLoader} 整体切换，或 {@link #setLargeFileLoader} 按路径强行指定。
     */
    void registerLargeFileLoader(soys.soyshttpovermc.web.LargeFileLoader loader);

    /**
     * 切换全局默认大文件加载器（按名称；未知名称返回 false）。
     * "默认为所有大文件状态"——切换后所有未按路径指定的大文件都走该加载器。
     */
    boolean setDefaultLargeFileLoader(String loaderName);

    /**
     * 为某路径前缀强行指定大文件加载方式（开发者强制切换；最长前缀优先）。
     * 例：setLargeFileLoader("/assets/map", "object-storage") —— 该前缀下的文件一律用 object-storage 加载器。
     */
    void setLargeFileLoader(String pathPrefix, String loaderName);

    // ===== 自定义错误页 =====

    /**
     * 注册自定义错误页（替换通用 404/500 等错误响应）。content 为完整 HTML/文本字节。
     * 插件禁用时自动卸载。
     */
    void registerErrorPage(Plugin owner, int status, byte[] content);

    /** 注册自定义错误页（便捷：HTML 字符串）。 */
    void registerErrorPage(Plugin owner, int status, String html);

    // ===== CORS 声明 =====

    /**
     * 为某路径前缀声明 CORS（Access-Control-Allow-*）。pathPrefix 为空或 "/" 表示全局。
     * 命中且请求为 OPTIONS 预检 → 自动 204 + CORS 头（短路）；普通请求附加 CORS 头。
     *
     * @param origin      允许来源（如 "*" 或 "https://a.com,https://b.com"）
     * @param methods     允许方法（如 "GET,POST,PUT,DELETE,OPTIONS"；可空=默认）
     * @param headers     允许请求头（如 "Content-Type,Authorization"；可空=*）
     * @param credentials 是否允许携带凭证（Access-Control-Allow-Credentials: true）
     */
    void registerCors(Plugin owner, String pathPrefix, String origin, String methods,
                      String headers, boolean credentials);
}
