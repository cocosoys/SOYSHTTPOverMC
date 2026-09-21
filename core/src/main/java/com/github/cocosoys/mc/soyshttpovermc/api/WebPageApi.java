package com.github.cocosoys.mc.soyshttpovermc.api;

import com.github.cocosoys.mc.soyshttpovermc.web.CorsRegistry;

import com.github.cocosoys.mc.soyshttpovermc.web.LargeFileLoader;
import com.github.cocosoys.mc.soyshttpovermc.web.NetworkPage;
import com.github.cocosoys.mc.soyshttpovermc.web.NetworkTransport;
import com.github.cocosoys.mc.soyshttpovermc.web.WebRegistry;
import org.bukkit.plugin.Plugin;

import java.io.File;

/**
 * 能力组 2：网页登记（委托 {@link WebRegistry}）。
 * 由 {@link SoysHttpOverMcApi#getWebPage()} 跳转获取。
 *
 * <p><b>极简便捷登记</b>（本类最上方一组）：所有单/双参数方法均自动识别调用插件（owner），
 * 失败时按返回类型返回 null / 0 / false，不抛异常，便于快速登记。</p>
 */
public interface WebPageApi {

    // ================================================================
    // 极简便捷登记（单/双参数；owner 沿调用栈自动识别；失败返回 null / 0 / false）
    // ================================================================

    /**
     * 极简登记网页：仅传内容即可完成登记，其余信息自动补充。
     * <ul>
     *   <li><b>owner</b>：沿调用栈自动识别调用插件（{@code JavaPlugin.getProvidingPlugin}），识别失败回退主插件；</li>
     *   <li><b>path</b>：自动 = {@code web/plugins/<插件>/page/page-<序号>}（每插件独立自增序号，从 1 开始）；</li>
     *   <li><b>Content-Type</b>：默认 {@code text/html; charset=utf-8}。</li>
     * </ul>
     *
     * @return 登记成功的 {@link WebRegistry.Entry}（含实际生成的 path / owner 等）；失败（内容为空 / owner 无法识别 / 路径重复）返回 null
     */
    WebRegistry.Entry registerPage(byte[] content);

    /**
     * 极简登记网页：仅传 jar 资源路径（相对插件 jar），内容与其余信息自动补充。
     * <ul>
     *   <li><b>path</b>：自动 = {@code web/plugins/<插件>/page/<资源文件名(不含后缀)>}
     *       （如 {@code registerPage("web/index.html")} → {@code .../page/index}）；</li>
     *   <li><b>Content-Type</b>：按资源路径扩展名推断。</li>
     * </ul>
     *
     * @return 登记成功的 {@link WebRegistry.Entry}；资源不存在 / 读取失败 / owner 无法识别返回 null
     */
    WebRegistry.Entry registerPage(String resourcePath);

    /**
     * 极简登记网页：显式路径 + 内容（自动补 /plugins/&lt;插件名&gt; 前缀，走正常命名空间）。
     *
     * @return 登记成功的 {@link WebRegistry.Entry}；失败返回 null
     */
    WebRegistry.Entry registerPage(String path, byte[] content);

    /**
     * 极简登记 jar 资源（惰性读取）：仅传资源路径，其余信息自动补充。
     * path 自动 = {@code web/plugins/<插件>/page/<资源文件名(不含后缀)>}；Content-Type 按扩展名推断。
     *
     * @return 登记成功的 {@link WebRegistry.Entry}；失败返回 null
     */
    WebRegistry.Entry registerResource(String resourcePath);

    /**
     * 极简登记 jar 资源：显式路径 + 资源路径（自动补 /plugins/&lt;插件名&gt; 前缀）。
     *
     * @return 登记成功的 {@link WebRegistry.Entry}；失败返回 null
     */
    WebRegistry.Entry registerResource(String path, String resourcePath);

    /**
     * 极简代理登记网页（无 /plugins 前缀）：显式路径 + 内容。
     *
     * @return 登记成功的 {@link WebRegistry.Entry}；失败返回 null
     */
    WebRegistry.Entry registerProxyPage(String path, byte[] content);

    /**
     * 极简代理登记 jar 资源（无 /plugins 前缀）：显式路径 + 资源路径。
     *
     * @return 登记成功的 {@link WebRegistry.Entry}；失败返回 null
     */
    WebRegistry.Entry registerProxyResource(String path, String resourcePath);

    /**
     * 极简登记自定义错误页（默认 404 状态码）。
     *
     * @return 登记成功的错误页句柄 {@link WebRegistry.Entry}（path = /error/404，仅作返回值标识）；失败返回 null
     */
    WebRegistry.Entry registerErrorPage(byte[] content);

    /**
     * 极简登记自定义错误页（默认 404 状态码；HTML 字符串便捷版，UTF-8）。
     *
     * @return 登记成功的错误页句柄 {@link WebRegistry.Entry}；失败返回 null
     */
    WebRegistry.Entry registerErrorPage(String html);

    /**
     * 极简批量登记磁盘目录：basePath 自动 = {@code web/plugins/<插件>/<目录名>}（无前缀空间，与极简登记统一命名空间）。
     *
     * @return 实际登记成功的 {@link WebRegistry.Entry} 集合（LinkedHashSet 保持扫描顺序）；目录无效/owner 无法识别返回 null，合法但空目录返回空集合
     */
    java.util.Set<WebRegistry.Entry> registerDirectory(File dir);

    // ================================================================
    // 完整版登记（显式 owner / 全部参数）
    // ================================================================

    WebRegistry.Entry registerPage(Plugin owner, String path, byte[] content);

    WebRegistry.Entry registerPage(Plugin owner, String path, byte[] content, String contentType);

    /**
     * 登记网页（直接内容；显式 Content-Type；force=true 强制覆盖重复登记并打印强制登记的插件）
     * @return 登记成功的 {@link WebRegistry.Entry}；参数非法 / 路径重复且未用 force 返回 null（拒绝）；force=true 覆盖成功返回新 Entry
     */
    WebRegistry.Entry registerPage(Plugin owner, String path, byte[] content, String contentType, boolean force);

    /**
     * 登记网页（直接内容；显式 HTTP 方法 + Content-Type + force + 界面说明 + 昵称路由）。
     * 用于注册 POST/PUT/DELETE/PATCH 等非 GET 静态响应；{@code httpMethod} 为 null/空 → GET。
     * 路径含 {name} 占位符段时自动登记到参数化路由表，匹配时按段比对提取 path variables。
     * @return 登记成功的 {@link WebRegistry.Entry}；参数非法 / 路径重复且未用 force 返回 null（拒绝）；force=true 覆盖成功返回新 Entry
     */
    WebRegistry.Entry registerPage(Plugin owner, String path, String httpMethod, byte[] content, String contentType, boolean force,
                      String description, java.util.List<String> nicknames);

    WebRegistry.Entry registerResource(Plugin owner, String path, ClassLoader resourceClassLoader, String resourcePath);

    WebRegistry.Entry registerResource(Plugin owner, String path, ClassLoader resourceClassLoader, String resourcePath, String contentType);

    /**
     * 登记网页（来自插件自有 jar 的资源；显式 Content-Type；force=true 强制覆盖重复登记并打印强制登记的插件）
     * @return 登记成功的 {@link WebRegistry.Entry}；参数非法 / 路径重复且未用 force 返回 null（拒绝）；force=true 覆盖成功返回新 Entry
     */
    WebRegistry.Entry registerResource(Plugin owner, String path, ClassLoader resourceClassLoader, String resourcePath, String contentType, boolean force);

    /**
     * 登记网页（来自插件自有 jar 的资源；显式 HTTP 方法 + Content-Type + force + 界面说明 + 昵称路由）。
     * 用于注册 POST/PUT/DELETE/PATCH 等非 GET 的 jar 资源响应；语义同
     * {@link #registerResource(Plugin, String, ClassLoader, String, String, boolean)} 但带方法与元数据。
     * @return 登记成功的 {@link WebRegistry.Entry}；参数非法 / 路径重复且未用 force 返回 null（拒绝）；force=true 覆盖成功返回新 Entry
     */
    WebRegistry.Entry registerResource(Plugin owner, String path, String httpMethod, ClassLoader resourceClassLoader, String resourcePath,
                          String contentType, boolean force, String description, java.util.List<String> nicknames);

    WebRegistry.Entry registerProxyPage(Plugin owner, String path, byte[] content);

    WebRegistry.Entry registerProxyPage(Plugin owner, String path, byte[] content, String contentType);

    /**
     * 强制代理登记网页（直接内容；显式 HTTP 方法 + Content-Type + force + 界面说明 + 昵称路由）。
     * 语义同 {@link #registerPage(Plugin, String, String, byte[], String, boolean, String, java.util.List)}
     * 但无 /plugins 前缀。
     * @return 登记成功的 {@link WebRegistry.Entry}；参数非法 / 路径重复且未用 force 返回 null（拒绝）；force=true 覆盖成功返回新 Entry
     */
    WebRegistry.Entry registerProxyPage(Plugin owner, String path, String httpMethod, byte[] content, String contentType, boolean force,
                           String description, java.util.List<String> nicknames);

    WebRegistry.Entry registerProxyResource(Plugin owner, String path, ClassLoader resourceClassLoader, String resourcePath);

    WebRegistry.Entry registerProxyResource(Plugin owner, String path, ClassLoader resourceClassLoader, String resourcePath, String contentType);

    /**
     * 强制代理登记网页（来自插件自有 jar 的资源；显式 HTTP 方法 + Content-Type + force + 界面说明 + 昵称路由）。
     * 语义同 {@link #registerResource(Plugin, String, String, ClassLoader, String, String, boolean, String, java.util.List)}
     * 但无 /plugins 前缀。
     * @return 登记成功的 {@link WebRegistry.Entry}；参数非法 / 路径重复且未用 force 返回 null（拒绝）；force=true 覆盖成功返回新 Entry
     */
    WebRegistry.Entry registerProxyResource(Plugin owner, String path, String httpMethod, ClassLoader resourceClassLoader, String resourcePath,
                               String contentType, boolean force, String description, java.util.List<String> nicknames);

    /**
     * 批量登记磁盘目录（递归扫描 dir 下文件挂到 basePath；请求时惰性读，支持热替换）。
     *
     * @return 实际登记成功的 {@link WebRegistry.Entry} 集合（LinkedHashSet 保持扫描顺序）；目录无效/owner 无法识别返回 null，合法但空目录返回空集合
     */
    java.util.Set<WebRegistry.Entry> registerDirectory(Plugin owner, String basePath, File dir);

    /**
     * 批量登记磁盘目录（显式是否强制代理无前缀）。
     *
     * @return 实际登记成功的 {@link WebRegistry.Entry} 集合（LinkedHashSet 保持扫描顺序）；目录无效/owner 无法识别返回 null，合法但空目录返回空集合
     */
    java.util.Set<WebRegistry.Entry> registerDirectory(Plugin owner, String basePath, File dir, boolean proxy);

    /**
     * 批量登记插件 jar 内资源目录（扫描 resourceRoot 前缀下全部条目挂到 basePath）。
     *
     * @return 实际登记成功的 {@link WebRegistry.Entry} 集合（LinkedHashSet 保持扫描顺序）；参数无效返回 null，合法但无条目返回空集合
     */
    java.util.Set<WebRegistry.Entry> registerResourceDirectory(Plugin owner, String basePath, ClassLoader resourceClassLoader, String resourceRoot);

    /**
     * 批量登记插件 jar 内资源目录（显式是否强制代理无前缀）。
     *
     * @return 实际登记成功的 {@link WebRegistry.Entry} 集合（LinkedHashSet 保持扫描顺序）；参数无效返回 null，合法但无条目返回空集合
     */
    java.util.Set<WebRegistry.Entry> registerResourceDirectory(Plugin owner, String basePath, ClassLoader resourceClassLoader, String resourceRoot, boolean proxy);

    /**
     * 卸载指定插件名登记的全部网页
     */
    void unregisterPluginPages(String pluginName);

    /**
     * 按 tag 卸载网页（精确反注册，供 SoysExpansion 等按模块清理）。
     *
     * @return 卸载的网页数量
     */
    int unregisterByTag(String tag);

    /**
     * 卸载指定插件注册的全部 CORS 声明（反注册闭环，供 SoysExpansion 等按插件清理）。
     */
    void unregisterCors(String pluginName);

    /**
     * 注册自定义错误页（替换通用 404/500 等错误响应）。content 为完整 HTML/文本字节。
     * 插件禁用时自动卸载。
     *
     * @return 登记成功的错误页句柄 {@link WebRegistry.Entry}（path = /error/&lt;status&gt;，仅作返回值标识）；content 为空或 status 非法返回 null
     */
    WebRegistry.Entry registerErrorPage(Plugin owner, int status, byte[] content);

    /**
     * 注册自定义错误页（便捷：HTML 字符串）。
     *
     * @return 登记成功的错误页句柄 {@link WebRegistry.Entry}；失败返回 null
     */
    WebRegistry.Entry registerErrorPage(Plugin owner, int status, String html);

    /**
     * 注册网络文件/网络网页页面：访问 {@code page.path()} 时网关调用 {@code page.load()} 获取内容
     * （自动补 /plugins/&lt;插件名&gt; 前缀；Content-Type 取 {@code page.contentType()} 或按扩展名推断；
     * load 失败 → 网关 502 JSON）。开发者可在 load() 内实现<b>自定义加密传输</b>（拉取+解密+验签）。
     *
     * @return 登记成功的 {@link WebRegistry.Entry}（RegisteredNetworkPage，含 path / cacheTtl 等）；page 为空或路径重复且非强制返回 null
     */
    WebRegistry.Entry registerNetworkPage(Plugin owner, NetworkPage page);

    /**
     * <b>预留</b>：注册网络传输实例化入口（网络传输提供者）。当前版本仅占位存储并输出警告日志，
     * <b>暂不接入加载链路</b>；未来将统一网络页与首页远程拉取（web.home 网络 URL）的底层传输层。
     *
     * @return 已记录的 transport 实例；transport 为 null 返回 null
     */
    NetworkTransport registerNetworkTransport(NetworkTransport transport);

    // ===== 大文件加载抽象（LargeFileLoader）=====

    /**
     * 注册一个自定义大文件加载器（实现 {@link LargeFileLoader}；同名覆盖）。
     * 默认所有超过 {@code web.large-file-threshold} 的大文件由内置流式加载器处理；
     * 注册后可用 {@link #setDefaultLargeFileLoader} 整体切换，或 {@link #setLargeFileLoader} 按路径强行指定。
     *
     * @return 已注册的 loader 实例；loader 为 null 或名称为空返回 null
     */
    LargeFileLoader registerLargeFileLoader(LargeFileLoader loader);

    /**
     * 切换全局默认大文件加载器（按名称；未知名称返回 null）。
     * "默认为所有大文件状态"——切换后所有未按路径指定的大文件都走该加载器。
     */
    LargeFileLoader setDefaultLargeFileLoader(String loaderName);

    /**
     * 为某路径前缀强行指定大文件加载方式（开发者强制切换；最长前缀优先）。
     * 例：setLargeFileLoader("/assets/map", "object-storage") —— 该前缀下的文件一律用 object-storage 加载器。
     *
     * @return 该路径前缀当前生效的 {@link LargeFileLoader} 实例；loader 名称不存在或参数非法返回 null
     */
    LargeFileLoader setLargeFileLoader(String pathPrefix, String loaderName);

    // ===== CORS 声明 =====

    /**
     * 为某路径前缀声明 CORS（Access-Control-Allow-*）。pathPrefix 为空或 "/" 表示全局。
     * 命中且请求为 OPTIONS 预检 → 自动 204 + CORS 头（短路）；普通请求附加 CORS 头。
     *
     * @param origin      允许来源（如 "*" 或 "https://a.com,https://b.com"）
     * @param methods     允许方法（如 "GET,POST,PUT,DELETE,OPTIONS"；可空=默认）
     * @param headers     允许请求头（如 "Content-Type,Authorization"；可空=*）
     * @param credentials 是否允许携带凭证（Access-Control-Allow-Credentials: true）
     * @return 登记成功的 {@link CorsRegistry.CorsEntry}；origin=* 且 credentials=true（危险组合，浏览器会拒绝）返回 null
     */
    CorsRegistry.CorsEntry registerCors(Plugin owner, String pathPrefix, String origin, String methods,
                         String headers, boolean credentials);

    // ===== 目录索引兜底规则（静态站惯例：目录请求自动导航到该目录 index）=====

    /**
     * 设置某插件的目录索引兜底规则：访问 {@code /web/plugins/<插件名>}（或带尾部斜杠）
     * 且常规解析未命中时，302 自动导航到 {@code …/<indexFile>}（默认 "index"，经 .html 智能匹配
     * 命中 index.html）。默认全局启用（enabled=true, indexFile="index"）；规则按插件注册，
     * 重复注册以最后一次为准。
     *
     * @param ownerName 插件名（与页面 URL 前缀 /web/plugins/&lt;插件名&gt; 一致；null/空忽略）
     * @param enabled   false=该插件的目录请求不做兜底（404），true=启用
     * @param indexFile 兜底目标文件名（不含扩展名与斜杠；null/空回退默认 "index"）
     */
    void setIndexRule(String ownerName, boolean enabled, String indexFile);

    /**
     * 移除某插件的目录索引兜底规则（页面反注册时调用；移除后该插件目录请求回落全局默认兜底）。
     */
    void removeIndexRule(String ownerName);

}
