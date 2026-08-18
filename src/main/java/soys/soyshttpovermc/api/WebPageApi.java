package soys.soyshttpovermc.api;

import org.bukkit.plugin.Plugin;

import java.io.File;

import soys.soyshttpovermc.web.NetworkPage;
import soys.soyshttpovermc.web.NetworkTransport;

/**
 * 能力组 2：网页登记（委托 {@link WebRegistry}）。
 * 由 {@link SoysHttpOverMcApi#getWebPage()} 跳转获取。
 */
public interface WebPageApi {

    void registerPage(Plugin owner, String path, byte[] content);

    void registerPage(Plugin owner, String path, byte[] content, String contentType);

    /** 登记网页（直接内容；显式 Content-Type；force=true 强制覆盖重复登记并打印强制登记的插件） */
    void registerPage(Plugin owner, String path, byte[] content, String contentType, boolean force);

    void registerResource(Plugin owner, String path, ClassLoader resourceClassLoader, String resourcePath);

    void registerResource(Plugin owner, String path, ClassLoader resourceClassLoader, String resourcePath, String contentType);

    /** 登记网页（来自插件自有 jar 的资源；显式 Content-Type；force=true 强制覆盖重复登记并打印强制登记的插件） */
    void registerResource(Plugin owner, String path, ClassLoader resourceClassLoader, String resourcePath, String contentType, boolean force);

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

    // ===== 首页（registerHome：直接把内容注册为站点首页 /，立即生效覆盖原首页） =====

    /**
     * 注册首页：把 {@code content} 注册为站点首页 {@code /}（Content-Type 默认 text/html）。
     * <b>直接应用</b>：注册即时生效（无需重启），并<b>覆盖原首页</b>——优先级高于
     * {@code web.home} 配置与默认 index.html（注册页路由先于静态资源命中）；
     * 注册时后台打印「插件已注册首页并覆盖原首页」日志。
     */
    void registerHome(Plugin owner, byte[] content);

    /** 注册首页（显式 Content-Type）。 */
    void registerHome(Plugin owner, byte[] content, String contentType);

    /**
     * 按<b>来源</b>注册首页：{@code source} 支持 相对路径（如 {@code dist/index.html}、{@code status/index.html}）/
     * 绝对路径（本地磁盘文件）/ 网络 URL（按需拉取+缓存+失败回退），语义与 {@code web.home} 一致；
     * <b>请求时解析</b>（文件热替换、URL 带 TTL 缓存），立即生效覆盖原首页。
     */
    void registerHome(Plugin owner, String source);

    /** 按来源注册首页（显式 Content-Type）。 */
    void registerHome(Plugin owner, String source, String contentType);

    // ===== 网络文件/网络网页页面（NetworkPage 抽象） =====

    /**
     * 注册网络文件/网络网页页面：访问 {@code page.path()} 时网关调用 {@code page.load()} 获取内容
     * （自动补 /plugins/&lt;插件名&gt; 前缀；Content-Type 取 {@code page.contentType()} 或按扩展名推断；
     * load 失败 → 网关 502 JSON）。开发者可在 load() 内实现<b>自定义加密传输</b>（拉取+解密+验签）。
     */
    void registerNetworkPage(Plugin owner, NetworkPage page);

    /**
     * <b>预留</b>：注册网络传输实例化入口（网络传输提供者）。当前版本仅占位存储并输出警告日志，
     * <b>暂不接入加载链路</b>；未来将统一网络页与首页远程拉取（web.home 网络 URL）的底层传输层。
     */
    void registerNetworkTransport(NetworkTransport transport);

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
