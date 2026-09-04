package com.github.cocosoys.mc.soyshttpovermc.web;

import com.github.cocosoys.mc.soyshttpovermc.i18n.I18n;
import lombok.CustomLog;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 插件网页登记处（第三方插件接入点）。
 *
 * <p>其他插件可通过 {@code HttpOverMcPlugin.getInstance().getWebRegistry()} 登记自己的网页/静态资源，
 * 网关会把这些页面纳入 HTTP 路由（与内置前端、注解式 API 并列）。</p>
 *
 * <h3>命名空间约定</h3>
 * <ul>
 *   <li><b>默认前缀</b>：登记时自动补充 {@code /plugins/<插件名>}，例如插件 Foo 登记 {@code /dashboard}
 *       → 实际访问地址 {@code /plugins/Foo/dashboard}；</li>
 *   <li><b>强制代理（无前缀）</b>：调用 {@code registerProxyPage} / {@code registerProxyResource}，
 *       以主插件 SOYSHTTPOverMC 名义代理登记，不加 {@code /plugins/<插件名>} 前缀
 *       （例如 {@code /dashboard}）；ownerPlugin 仍标记为真实插件，故插件卸载时仍会一并清理；</li>
 *   <li><b>跳转</b>：{@code registerRedirect} / {@code registerProxyRedirect} 登记 302/301 跳转，
 *       访问 A 网址时浏览器自动跳转到 B 网址（可跳转站内路径或站外 URL）；</li>
 *   <li><b>内容来源</b>：{@code registerPage} 直接提供字节内容；{@code registerResource} 提供插件自有
 *       jar 内的资源路径（按需读取，省内存）；二者均按原始路径扩展名推断 Content-Type（可显式覆盖）；
 *       <b>.html 后缀智能匹配</b>：注册 {@code /login} 后，{@code /login} 与 {@code /login.html} 均可访问；</li>
 *   <li><b>生命周期</b>：插件被禁用时（{@code PluginDisableEvent}）网关自动卸载其名下全部网页，
 *       亦可调用 {@link #unregisterPlugin(String)} 显式卸载。</li>
 * </ul>
 */
@CustomLog
public class WebRegistry {

    /**
     * 路由表：key = "<METHOD> <路径>"（如 "GET /api/users"），value = 登记项
     */
    private Map<String, Entry> pages = new ConcurrentHashMap<>();
    /**
     * 参数化路由表：key = "<METHOD> <模板>"（如 "GET /api/users/{id}"），value = 登记项。
     * 模板路径含 {name} 占位符段；匹配时按段比对并提取 path variables。
     * 仅在精确匹配未命中时启用，避免影响默认 GET 路由性能。
     */
    private Map<String, Entry> parameterizedPages = new ConcurrentHashMap<>();
    /**
     * 网络文件/网络网页页面：key = "<METHOD> <路径>"（NetworkPage 抽象，按需 load）
     */
    private Map<String, RegisteredNetworkPage> networkPages = new ConcurrentHashMap<>();
    /**
     * 昵称路由索引：key = 昵称路径（规范化后），value = 登记项。注册时构建，O(1) 匹配，避免遍历全部页面。
     */
    private Map<String, Entry> nicknameIndex = new ConcurrentHashMap<>();
    /**
     * 宿主插件名（SOYSHTTPOverMC 本体）：其登记不加 /plugins 前缀
     */
    private final String hostName;

    /**
     * 默认 HTTP 方法（保持向后兼容：未显式指定方法时按 GET 登记）。
     */
    public static final String DEFAULT_METHOD = "GET";

    public WebRegistry(String hostName) {
        this.hostName = hostName == null ? "" : hostName;
    }

    /**
     * 规范化 HTTP 方法（大写；null/空 → GET）。
     */
    private static String normalizeMethod(String method) {
        String m = method == null ? "" : method.trim().toUpperCase();
        return m.isEmpty() ? DEFAULT_METHOD : m;
    }

    /**
     * 判定路径是否含 {name} 占位符段（参数化路由）。
     */
    private static boolean isParameterized(String path) {
        return path != null && path.indexOf('{') >= 0 && path.indexOf('}') > path.indexOf('{');
    }

    // ===== 普通登记（自动 /plugins/<插件名> 前缀） =====

    /**
     * 登记网页（直接内容；Content-Type 按路径扩展名推断）；重复路径默认阻止（force=true 可强制覆盖）。
     */
    public Entry registerPage(Plugin owner, String path, byte[] content) {
        return registerPage(owner, path, content, null, false);
    }

    /**
     * 登记网页（直接内容；显式 Content-Type）；重复路径默认阻止（force=true 可强制覆盖）。
     */
    public Entry registerPage(Plugin owner, String path, byte[] content, String contentType) {
        return registerPage(owner, path, content, contentType, false);
    }

    /**
     * 登记网页（直接内容；显式 Content-Type；force=true 强制覆盖重复登记并打印强制登记的插件）。
     */
    public Entry registerPage(Plugin owner, String path, byte[] content, String contentType, boolean force) {
        return register(owner, path, content, contentType, false, force);
    }

    /**
     * 登记网页（直接内容；显式 Content-Type；force=true；附带界面说明与昵称路由）。
     */
    public Entry registerPage(Plugin owner, String path, byte[] content, String contentType, boolean force,
                              String description, List<String> nicknames) {
        return register(owner, path, content, contentType, false, force, description, nicknames);
    }

    /**
     * 登记网页（直接内容；显式 Content-Type；force=true；附带界面说明与昵称路由；注册时声明访问权限）。
     * {@code permissions} 为 AND 语义（全部通过才放行）；优先级低于 pages.yml 单页内联、高于 pages.yml 全局。
     */
    public Entry registerPage(Plugin owner, String path, byte[] content, String contentType, boolean force,
                              String description, List<String> nicknames, List<String> permissions) {
        return register(owner, path, content, contentType, false, force, description, nicknames, permissions);
    }

    /**
     * 登记网页（直接内容；显式 HTTP 方法 + Content-Type + force + 界面说明 + 昵称路由）。
     * 用于注册 POST/PUT/DELETE/PATCH 等非 GET 的静态响应（如简单 POST 接收器返回固定 JSON）；
     * {@code httpMethod} 为 null/空 时按 GET 处理（与默认重载等价）。
     * 路径含 {name} 占位符段时自动登记到参数化路由表，匹配时按段比对提取 path variables。
     */
    public Entry registerPage(Plugin owner, String path, String httpMethod, byte[] content, String contentType, boolean force,
                              String description, List<String> nicknames) {
        return register(owner, path, httpMethod, content, contentType, false, force, description, nicknames);
    }

    /**
     * 登记网页（直接内容；显式 HTTP 方法 + Content-Type + force + 界面说明 + 昵称路由；注册时声明访问权限）。
     * 语义同上一重载，另带注册权限（AND 语义；优先级低于 pages.yml 单页内联、高于 pages.yml 全局）。
     */
    public Entry registerPage(Plugin owner, String path, String httpMethod, byte[] content, String contentType, boolean force,
                              String description, List<String> nicknames, List<String> permissions) {
        return register(owner, path, httpMethod, content, contentType, false, force, description, nicknames, permissions);
    }

    /**
     * 登记网页（来自插件自有 jar 的资源；Content-Type 按路径扩展名推断）；重复路径默认阻止。
     */
    public Entry registerResource(Plugin owner, String path, ClassLoader resourceClassLoader, String resourcePath) {
        return registerResource(owner, path, resourceClassLoader, resourcePath, null, false);
    }

    /**
     * 登记网页（来自插件自有 jar 的资源；显式 Content-Type）；重复路径默认阻止。
     */
    public Entry registerResource(Plugin owner, String path, ClassLoader resourceClassLoader, String resourcePath, String contentType) {
        return registerResource(owner, path, resourceClassLoader, resourcePath, contentType, false);
    }

    /**
     * 登记网页（来自插件自有 jar 的资源；显式 Content-Type；force=true 强制覆盖重复登记并打印强制登记的插件）。
     */
    public Entry registerResource(Plugin owner, String path, ClassLoader resourceClassLoader, String resourcePath, String contentType, boolean force) {
        return registerRes(owner, path, resourceClassLoader, resourcePath, contentType, false, force);
    }

    /**
     * 登记网页（来自插件自有 jar 的资源；显式 HTTP 方法 + Content-Type + force + 界面说明 + 昵称路由）。
     * 用于注册 POST/PUT/DELETE/PATCH 等非 GET 的 jar 资源响应；路径含 {name} 时自动登记到参数化路由表。
     */
    public Entry registerResource(Plugin owner, String path, String httpMethod, ClassLoader resourceClassLoader, String resourcePath,
                                  String contentType, boolean force, String description, List<String> nicknames) {
        return registerRes(owner, path, httpMethod, resourceClassLoader, resourcePath, contentType, false, force, description, nicknames);
    }

    public Entry registerResource(Plugin owner, String path, String httpMethod, ClassLoader resourceClassLoader, String resourcePath,
                                  String contentType, boolean force, String description, List<String> nicknames, List<String> permissions) {
        return registerRes(owner, path, httpMethod, resourceClassLoader, resourcePath, contentType, false, force, description, nicknames, permissions);
    }

    // ===== 强制代理登记（无 /plugins/<插件名> 前缀） =====

    /**
     * 强制以主插件代理登记网页（直接内容；Content-Type 按路径扩展名推断）；重复路径默认阻止。
     */
    public Entry registerProxyPage(Plugin owner, String path, byte[] content) {
        return registerProxyPage(owner, path, content, null);
    }

    /**
     * 强制以主插件代理登记网页（直接内容；显式 Content-Type）；重复路径默认阻止（force=true 可强制覆盖）。
     */
    public Entry registerProxyPage(Plugin owner, String path, byte[] content, String contentType) {
        return register(owner, path, content, contentType, true, false);
    }

    /**
     * 强制以主插件代理登记网页（直接内容；显式 Content-Type；force=true 强制覆盖重复登记）。
     */
    public Entry registerProxyPage(Plugin owner, String path, byte[] content, String contentType, boolean force) {
        return register(owner, path, content, contentType, true, force);
    }

    /**
     * 强制以主插件代理登记网页（直接内容；显式 Content-Type；force=true；附带界面说明与昵称路由）。
     */
    public Entry registerProxyPage(Plugin owner, String path, byte[] content, String contentType, boolean force,
                                   String description, List<String> nicknames) {
        return register(owner, path, content, contentType, true, force, description, nicknames);
    }

    /**
     * 强制以主插件代理登记网页（直接内容；显式 Content-Type；force=true；附带界面说明与昵称路由；注册时声明访问权限）。
     */
    public Entry registerProxyPage(Plugin owner, String path, byte[] content, String contentType, boolean force,
                                   String description, List<String> nicknames, List<String> permissions) {
        return register(owner, path, content, contentType, true, force, description, nicknames, permissions);
    }

    /**
     * 强制代理登记网页（直接内容；显式 HTTP 方法 + Content-Type + force + 界面说明 + 昵称路由）。
     * 用于注册 POST/PUT/DELETE/PATCH 等非 GET 的静态响应（无 /plugins 前缀）；语义同
     * {@link #registerPage(Plugin, String, String, byte[], String, boolean, String, List)} 但走代理路径。
     */
    public Entry registerProxyPage(Plugin owner, String path, String httpMethod, byte[] content, String contentType, boolean force,
                                   String description, List<String> nicknames) {
        return register(owner, path, httpMethod, content, contentType, true, force, description, nicknames);
    }

    /**
     * 强制代理登记网页（直接内容；显式 HTTP 方法 + Content-Type + force + 界面说明 + 昵称路由；注册时声明访问权限）。
     */
    public Entry registerProxyPage(Plugin owner, String path, String httpMethod, byte[] content, String contentType, boolean force,
                                   String description, List<String> nicknames, List<String> permissions) {
        return register(owner, path, httpMethod, content, contentType, true, force, description, nicknames, permissions);
    }

    /**
     * 强制代理登记网页（直接内容；desc + 昵称 + 权限 + 来源 tags）。tags 用于 reload 时按来源卸载
     * （如 pages.yml 注册的页面标记 "pages.yml"）。
     */
    public Entry registerProxyPage(Plugin owner, String path, byte[] content, String contentType, boolean force,
                                   String description, List<String> nicknames, List<String> permissions, List<String> tags) {
        return register(owner, path, content, contentType, true, force, description, nicknames, permissions, tags);
    }

    /**
     * 强制代理登记网页（直接内容；force + 来源 tags）。tags 用于 reload 时按来源卸载。
     */
    public Entry registerProxyPage(Plugin owner, String path, byte[] content, String contentType, boolean force,
                                   List<String> tags) {
        return register(owner, path, DEFAULT_METHOD, content, contentType, true, force, null, null, null, tags);
    }

    /**
     * 强制以主插件代理登记网页（来自插件自有 jar 的资源）；重复路径默认阻止。
     */
    public Entry registerProxyResource(Plugin owner, String path, ClassLoader resourceClassLoader, String resourcePath) {
        return registerProxyResource(owner, path, resourceClassLoader, resourcePath, null);
    }

    /**
     * 强制以主插件代理登记网页（来自插件自有 jar 的资源；显式 Content-Type）；重复路径默认阻止。
     */
    public Entry registerProxyResource(Plugin owner, String path, ClassLoader resourceClassLoader, String resourcePath, String contentType) {
        return registerRes(owner, path, resourceClassLoader, resourcePath, contentType, true, false);
    }

    /**
     * 强制代理登记网页（来自插件自有 jar 的资源；显式 HTTP 方法 + Content-Type + force + 界面说明 + 昵称路由）。
     * 语义同 {@link #registerResource(Plugin, String, String, ClassLoader, String, String, boolean, String, List)}
     * 但走代理路径（无 /plugins 前缀）。
     */
    public Entry registerProxyResource(Plugin owner, String path, String httpMethod, ClassLoader resourceClassLoader, String resourcePath,
                                       String contentType, boolean force, String description, List<String> nicknames) {
        return registerRes(owner, path, httpMethod, resourceClassLoader, resourcePath, contentType, true, force, description, nicknames);
    }

    public Entry registerProxyResource(Plugin owner, String path, String httpMethod, ClassLoader resourceClassLoader, String resourcePath,
                                       String contentType, boolean force, String description, List<String> nicknames, List<String> permissions) {
        return registerRes(owner, path, httpMethod, resourceClassLoader, resourcePath, contentType, true, force, description, nicknames, permissions);
    }

    // ===== 跳转登记（A 网址 → B 网址，302/301） =====

    /**
     * 登记跳转（默认 302，路径自动补 /plugins/<插件名> 前缀）。访问 A 时浏览器自动跳转到 B。
     */
    public Entry registerRedirect(Plugin owner, String fromPath, String toPath) {
        return registerRedirect(owner, fromPath, toPath, false, 302);
    }

    /**
     * 登记跳转（显式状态码 301/302 等）。
     */
    public Entry registerRedirect(Plugin owner, String fromPath, String toPath, int statusCode) {
        return registerRedirect(owner, fromPath, toPath, false, statusCode);
    }

    /**
     * 强制代理跳转（无 /plugins/<插件名> 前缀；默认 302）。
     */
    public Entry registerProxyRedirect(Plugin owner, String fromPath, String toPath) {
        return registerRedirect(owner, fromPath, toPath, true, 302);
    }

    /**
     * 强制代理跳转（显式状态码）。
     */
    public Entry registerProxyRedirect(Plugin owner, String fromPath, String toPath, int statusCode) {
        return registerRedirect(owner, fromPath, toPath, true, statusCode);
    }

    /**
     * 强制代理跳转（显式状态码 + 来源 tags）。tags 用于 reload 时按来源卸载。
     */
    public Entry registerProxyRedirect(Plugin owner, String fromPath, String toPath, int statusCode, List<String> tags) {
        return registerRedirect(owner, fromPath, toPath, true, statusCode, false, tags);
    }

    /**
     * 登记核心：重复路径<b>默认阻止</b>（打印拒绝日志，返回 false）；{@code force=true} 时
     * 强制覆盖并打印<b>强制登记的插件</b>与原登记插件。
     *
     * <p>路由 key 形如 {@code "<METHOD> <路径>"}。若路径含 {name} 占位符段 → 登记到参数化路由表
     * （{@link #parameterizedPages}），匹配时按段比对并提取 path variables；否则登记到精确路由表
     * （{@link #pages}）。</p>
     */
    private Entry putEntry(String key, Entry e, boolean force) {
        // 路径含 {name} 占位符 → 走参数化路由表；key 形如 "GET /api/users/{id}"
        Map<String, Entry> table = isParameterized(e.path) ? parameterizedPages : pages;
        Entry old = table.get(key);
        if (old != null && !force) {
            log.warnT("log.web.register-duplicate-denied", "拒绝重复登记: {0}（已由插件 {1} 登记；如需覆盖请用强制登记 force=true）",
                    key, old.ownerPlugin);
            return null;
        }
        table.put(key, e);
        // 构建昵称路由索引（O(1) 匹配，避免 resolveFull 时遍历全部页面）
        if (e.nicknames != null && !e.nicknames.isEmpty()) {
            for (String nickname : e.nicknames) {
                if (nickname == null || nickname.trim().isEmpty()) continue;
                String a = nickname.startsWith("/") ? nickname : "/" + nickname;
                a = a.replace('\\', '/');
                nicknameIndex.put(a, e);
                // .html 后缀智能匹配：同时索引带 .html 和不带 .html 的形式
                if (a.indexOf('.') < 0) {
                    nicknameIndex.put(a + ".html", e);
                } else if (a.endsWith(".html") && a.length() > 5) {
                    nicknameIndex.put(a.substring(0, a.length() - 5), e);
                }
            }
        }
        if (old != null) {
            log.infoT("log.web.register-force-overwrite", "插件 {0} 强制登记覆盖: {1}（原登记插件 {2}）", e.ownerPlugin, key, old.ownerPlugin);
        }
        return e;
    }

    /**
     * 内部：登记跳转项（force=true 强制覆盖重复路径）。
     */
    private Entry registerRedirect(Plugin owner, String fromPath, String toPath, boolean proxy, int statusCode) {
        return registerRedirect(owner, fromPath, toPath, proxy, statusCode, false, null);
    }

    /**
     * 内部：登记跳转项（显式 force）。
     */
    private Entry registerRedirect(Plugin owner, String fromPath, String toPath, boolean proxy, int statusCode, boolean force) {
        return registerRedirect(owner, fromPath, toPath, proxy, statusCode, force, null);
    }

    /**
     * 内部：登记跳转项（显式 force + 来源 tags）。
     *
     * @return 登记成功的 Entry；注册失败（重复路径且非 force）返回 null
     */
    private Entry registerRedirect(Plugin owner, String fromPath, String toPath, boolean proxy, int statusCode, boolean force, List<String> tags) {
        if (fromPath == null || toPath == null) return null;
        String ownerName = owner == null ? null : owner.getName();
        String full = resolvePath(ownerName, fromPath, proxy);
        Entry entry = putEntry("GET " + full,
                new Entry(ownerName, full, null, null, null, null, toPath, statusCode, null, null, null, null, tags), force);
        if (entry == null) return null;
        log.infoT("log.web.register-redirect", "登记跳转: GET {0} → {1} ({2}) 插件={3}{4}", full, toPath, statusCode,
                ownerName, proxy ? " (代理无前缀)" : "");
        return entry;
    }

    // ===== 目录批量登记（一行托管整个前端文件夹） =====

    /**
     * 批量登记磁盘目录（如插件自带的前端 dist/ 文件夹）：递归扫描 {@code dir} 下全部文件，
     * 按相对路径挂到 {@code basePath} 下（非主插件自动补 /plugins/&lt;插件名&gt; 前缀）。
     * 每个文件以磁盘 File 形式惰性登记（请求时再读，支持磁盘热替换），不入内存。
     *
     * <p>示例：{@code registerDirectory(owner, "/", distDir)} → 访问 /plugins/Foo/index.html 等；
     * 配合 {@code registerProxyDirectory(owner, "/app", distDir)} 可挂到无前缀的 /app。</p>
     */
    public void registerDirectory(Plugin owner, String basePath, File dir) {
        registerDirectory(owner, basePath, dir, false);
    }

    /**
     * 目录批量登记（显式是否强制代理无前缀）。
     */
    public void registerDirectory(Plugin owner, String basePath, File dir, boolean proxy) {
        if (owner == null || basePath == null || dir == null || !dir.isDirectory()) return;
        walkDirectory(owner.getName(), basePath, dir, proxy);
    }

    /**
     * 强制代理目录批量登记（无 /plugins/&lt;插件名&gt; 前缀）。
     */
    public void registerProxyDirectory(Plugin owner, String basePath, File dir) {
        registerDirectory(owner, basePath, dir, true);
    }

    /**
     * 强制代理登记单个磁盘页（无 /plugins/&lt;插件名&gt; 前缀）。
     * 以<b>惰性磁盘文件</b>形式登记：请求时才读盘，支持磁盘热替换（与目录批量登记一致）；
     * 文件缺失时仍登记（由磁盘来源为空处理），但不会抛错。重复路径默认阻止，{@code force=true} 强制覆盖。
     * 用于把核心内置页（/login、/status 等）纳入注册通道，同时保留 webroot 磁盘覆盖能力。
     */
    public Entry registerProxyFile(Plugin owner, String path, File diskFile, String contentType, boolean force) {
        return registerProxyFile(owner, path, diskFile, contentType, force, null);
    }

    /**
     * 强制代理登记单个磁盘页（带 jar 资源兜底）：磁盘文件缺失/读取失败时，
     * 回退读取插件自身 jar 内 {@code jarFallbackResource}（如 {@code /dist/login.html}）。
     * 保留磁盘热替换优先，兼顾自定义 web.root 缺失内置文件时不至于返回空壳。
     */
    public Entry registerProxyFile(Plugin owner, String path, File diskFile, String contentType, boolean force,
                                   String jarFallbackResource) {
        return registerProxyFile(owner, path, diskFile, contentType, force, jarFallbackResource, null, null);
    }

    /**
     * 强制代理登记单个磁盘页（带 jar 资源兜底 + 界面说明与昵称路由）：磁盘文件缺失/读取失败时，
     * 回退读取插件自身 jar 内 {@code jarFallbackResource}（如 {@code /dist/login.html}）。
     * 保留磁盘热替换优先，兼顾自定义 web.root 缺失内置文件时不至于返回空壳。
     */
    public Entry registerProxyFile(Plugin owner, String path, File diskFile, String contentType, boolean force,
                                   String jarFallbackResource, String description, List<String> nicknames) {
        return registerProxyFile(owner, path, diskFile, contentType, force, jarFallbackResource, description, nicknames, null);
    }

    public Entry registerProxyFile(Plugin owner, String path, File diskFile, String contentType, boolean force,
                                   String jarFallbackResource, String description, List<String> nicknames, List<String> permissions) {
        return registerProxyFile(owner, path, diskFile, contentType, force, jarFallbackResource, description, nicknames, permissions, null);
    }

    public Entry registerProxyFile(Plugin owner, String path, File diskFile, String contentType, boolean force,
                                   String jarFallbackResource, String description, List<String> nicknames, List<String> permissions,
                                   List<String> tags) {
        if (owner == null || path == null || diskFile == null) return null;
        String ownerName = owner.getName();
        String full = resolvePath(ownerName, path, true);
        String ct = (contentType == null || contentType.isEmpty()) ? null : contentType;
        ClassLoader fbCl = (jarFallbackResource == null || jarFallbackResource.isEmpty())
                ? null : owner.getClass().getClassLoader();
        Entry entry = putEntry("GET " + full,
                new Entry(ownerName, full, ct, null, fbCl, jarFallbackResource, null, 0, diskFile, description, nicknames, permissions, tags), force);
        if (entry == null) return null;
        log.infoT("log.web.register-proxy-file",
                "登记网页(磁盘惰性): GET {0} 插件={1}{2}{3}", full, ownerName,
                fbCl == null ? "" : " 兜底=" + jarFallbackResource,
                permissions == null || permissions.isEmpty() ? "" : " 权限=" + permissions);
        return entry;
    }

    /**
     * 批量登记插件 jar 内资源目录（如 resources/dist/ 下的前端产物）：扫描插件 jar 中
     * {@code resourceRoot} 前缀下的全部条目，挂到 {@code basePath} 下。
     */
    public void registerResourceDirectory(Plugin owner, String basePath, ClassLoader resourceClassLoader, String resourceRoot) {
        registerResourceDirectory(owner, basePath, resourceClassLoader, resourceRoot, false);
    }

    /**
     * jar 资源目录批量登记（显式是否强制代理无前缀）。
     */
    public void registerResourceDirectory(Plugin owner, String basePath, ClassLoader resourceClassLoader, String resourceRoot, boolean proxy) {
        if (owner == null || basePath == null || resourceClassLoader == null || resourceRoot == null) return;
        File jar = pluginJar(owner);
        if (jar == null) return;
        String root = resourceRoot.startsWith("/") ? resourceRoot.substring(1) : resourceRoot;
        if (!root.isEmpty() && !root.endsWith("/")) root += "/";
        try (JarFile jf = new JarFile(jar)) {
            Enumeration<JarEntry> en = jf.entries();
            while (en.hasMoreElements()) {
                JarEntry je = en.nextElement();
                if (je.isDirectory()) continue;
                String name = je.getName();
                String rel = root.isEmpty() ? name : (name.startsWith(root) ? name.substring(root.length()) : null);
                if (rel == null || rel.isEmpty()) continue;
                String full = resolvePath(owner.getName(), joinWeb(basePath, rel), proxy);
                // contentType 置 null → 服务端按扩展名实时推断（配合 registerMimeType）
                // 重复路径默认阻止（批量覆盖同一路径时打印拒绝日志）
                putEntry("GET " + full, new Entry(owner.getName(), full, null,
                        null, resourceClassLoader, "/" + name, null, 0, null), false);
            }
            log.infoT("log.web.register-jar-dir", "批量登记 jar 目录: {0} root={1} base={2}", owner.getName(), resourceRoot, basePath);
        } catch (Exception ex) {
            log.warnT("log.web.register-jar-dir-fail", "批量登记 jar 目录失败: {0} -> {1}", owner.getName(), ex.getMessage());
        }
    }

    /**
     * 强制代理 jar 资源目录批量登记。
     */
    public void registerProxyResourceDirectory(Plugin owner, String basePath, ClassLoader resourceClassLoader, String resourceRoot) {
        registerResourceDirectory(owner, basePath, resourceClassLoader, resourceRoot, true);
    }

    // ===== 网络文件/网络网页页面（NetworkPage 抽象：开发者自定义传输，如加密） =====

    /**
     * 登记网络文件/网络网页页面：访问 {@code page.path()} 时网关调用 {@code page.load()} 获取内容
     * （自动补 /plugins/&lt;插件名&gt; 前缀，与普通登记页一致；支持 .html 后缀智能匹配）。
     * 重复路径默认阻止（force=true 强制覆盖并打印强制登记的插件）。
     */
    public Entry registerNetworkPage(String ownerPlugin, NetworkPage page) {
        return registerNetworkPage(ownerPlugin, page, false);
    }

    /**
     * 登记网络页（显式 force；重复路径默认阻止，force=true 强制覆盖并打印强制登记的插件）。
     */
    public Entry registerNetworkPage(String ownerPlugin, NetworkPage page, boolean force) {
        return registerNetworkPage(ownerPlugin, page, force, null);
    }

    public Entry registerNetworkPage(String ownerPlugin, NetworkPage page, boolean force, List<String> permissions) {
        return registerNetworkPage(ownerPlugin, page, force, permissions, null);
    }

    public Entry registerNetworkPage(String ownerPlugin, NetworkPage page, boolean force, List<String> permissions, List<String> tags) {
        if (page == null || page.path() == null) return null;
        String full = resolvePath(ownerPlugin, page.path(), false);
        String key = "GET " + full;
        RegisteredNetworkPage old = networkPages.get(key);
        if (old != null && !force) {
            log.warnT("log.web.register-netpage-duplicate-denied", "拒绝重复登记(网络页): {0}（已由插件 {1} 登记；如需覆盖请用强制登记 force=true）",
                    key, old.ownerPlugin);
            return null;
        }
        RegisteredNetworkPage ne = new RegisteredNetworkPage(ownerPlugin, full, page, permissions, tags);
        networkPages.put(key, ne);
        if (old != null) {
            log.infoT("log.web.register-netpage-force-overwrite", "插件 {0} 强制登记覆盖(网络页): {1}（原登记插件 {2}）", ownerPlugin, key, old.ownerPlugin);
        }
        log.infoT("log.web.register-netpage", "登记网络页: {0} name={1} 插件={2} cacheTtl={3}s{4}", key, page.name(),
                ownerPlugin, page.cacheTtlSeconds(),
                permissions == null || permissions.isEmpty() ? "" : " 权限=" + permissions);
        return ne;
    }

    public List<String> networkPagePermissions(String httpMethod, String cleanPath) {
        String method = httpMethod == null ? "GET" : httpMethod.toUpperCase();
        if (cleanPath == null) return null;
        RegisteredNetworkPage e = networkPages.get(method + " " + cleanPath);
        if (e == null && cleanPath.indexOf('.') < 0) {
            e = networkPages.get(method + " " + cleanPath + ".html");
        }
        if (e == null && cleanPath.endsWith(".html") && cleanPath.length() > 5) {
            e = networkPages.get(method + " " + cleanPath.substring(0, cleanPath.length() - 5));
        }
        return e == null ? null : e.permissions;
    }

    /**
     * 按方法 + 路径匹配网络页（精确 + .html 智能匹配）；未命中返回 null。
     */
    public NetworkPage resolveNetworkPage(String httpMethod, String cleanPath) {
        String method = httpMethod == null ? "GET" : httpMethod.toUpperCase();
        if (cleanPath == null) return null;
        RegisteredNetworkPage e = networkPages.get(method + " " + cleanPath);
        if (e == null && cleanPath.indexOf('.') < 0) {
            e = networkPages.get(method + " " + cleanPath + ".html");
        }
        if (e == null && cleanPath.endsWith(".html") && cleanPath.length() > 5) {
            e = networkPages.get(method + " " + cleanPath.substring(0, cleanPath.length() - 5));
        }
        return e == null ? null : e.page;
    }

    /**
     * 网络页登记项：继承 Entry（复用 ownerPlugin / path / permissions / tags 等公共字段），
     * NetworkPage 作为子类专属来源（按需 load + 可选缓存）。需要识别网络页时用 instanceof 判断。
     */
    private static final class RegisteredNetworkPage extends Entry {
        final NetworkPage page;

        RegisteredNetworkPage(String ownerPlugin, String path, NetworkPage page) {
            this(ownerPlugin, path, page, null, null);
        }

        RegisteredNetworkPage(String ownerPlugin, String path, NetworkPage page, List<String> permissions) {
            this(ownerPlugin, path, page, permissions, null);
        }

        RegisteredNetworkPage(String ownerPlugin, String path, NetworkPage page, List<String> permissions, List<String> tags) {
            super(ownerPlugin, path, null, null, null, null, null, 0, null, null, permissions, tags);
            this.page = page;
        }
    }

    // ===== 解析 / 卸载 =====

    /**
     * 按方法 + 路径解析已登记网页；未命中返回 null。
     * 解析顺序：
     * <ol>
     *   <li>精确匹配（含 .html 后缀智能匹配）；</li>
     *   <li>参数化路由匹配（含 {name} 占位符段，如 {@code /api/users/{id}} 命中 {@code /api/users/123}）；
     *       <b>不</b>返回 path variables —— 若需提取路径参数请用 {@link #resolveFull}；</li>
     *   <li>昵称路由（扫描全部登记项，避免别名表与主表不一致）。</li>
     * </ol>
     */
    public Entry resolve(String httpMethod, String cleanPath) {
        ResolveResult rr = resolveFull(httpMethod, cleanPath);
        return rr == null ? null : rr.entry;
    }

    /**
     * 按方法 + 路径解析已登记网页，<b>带 path variables</b>；未命中返回 null。
     * 参数化路由命中时，pathVariables 含 {name} → 实际值 映射（如 {@code {id=123}}）。
     * 精确匹配与昵称路由命中时，pathVariables 为空 Map。
     */
    public ResolveResult resolveFull(String httpMethod, String cleanPath) {
        String method = httpMethod == null ? DEFAULT_METHOD : httpMethod.toUpperCase();
        if (cleanPath == null) return null;
        // 1) 精确匹配（含 .html 后缀智能匹配）
        Entry e = lookup(pages, method, cleanPath);
        if (e != null) return new ResolveResult(e, java.util.Collections.emptyMap());
        // 2) 参数化路由匹配（{name} 占位符段，按段比对）
        ResolveResult pr = matchParameterized(parameterizedPages, method, cleanPath);
        if (pr != null) return pr;
        // 3) 昵称路由（O(1) 索引查找，注册时已构建 nicknameIndex）
        Entry ne = nicknameIndex.get(cleanPath);
        if (ne != null) return new ResolveResult(ne, java.util.Collections.emptyMap());
        // .html 后缀智能匹配（路径无扩展名时尝试 +.html，带 .html 时尝试去后缀）
        if (cleanPath.indexOf('.') < 0) {
            ne = nicknameIndex.get(cleanPath + ".html");
            if (ne != null) return new ResolveResult(ne, java.util.Collections.emptyMap());
        } else if (cleanPath.endsWith(".html") && cleanPath.length() > 5) {
            ne = nicknameIndex.get(cleanPath.substring(0, cleanPath.length() - 5));
            if (ne != null) return new ResolveResult(ne, java.util.Collections.emptyMap());
        }
        return null;
    }

    /**
     * 在一个表中做主键命中 + .html 后缀智能匹配。
     */
    private static Entry lookup(Map<String, Entry> table, String method, String cleanPath) {
        Entry e = table.get(method + " " + cleanPath);
        if (e != null) return e;
        if (cleanPath.indexOf('.') < 0) {
            return table.get(method + " " + cleanPath + ".html");
        }
        if (cleanPath.endsWith(".html") && cleanPath.length() > 5) {
            return table.get(method + " " + cleanPath.substring(0, cleanPath.length() - 5));
        }
        return null;
    }

    /**
     * 在参数化路由表中按段匹配：模板段为 {name} 时匹配任意非空段并提取 path variable；
     * 普通段必须严格相等。模板与路径段数必须相同（不支持 {var} 之外的通配）。
     * 仅支持 .html 后缀智能匹配（路径无扩展名时同时尝试 {@code path + ".html"}）。
     */
    private static ResolveResult matchParameterized(Map<String, Entry> table, String method, String cleanPath) {
        if (table.isEmpty()) return null;
        String[] reqSegments = splitSegments(cleanPath);
        for (Map.Entry<String, Entry> kv : table.entrySet()) {
            String key = kv.getKey();
            // key 形如 "GET /api/users/{id}" —— 取空格之后的部分作为模板
            int sp = key.indexOf(' ');
            if (sp < 0) continue;
            String keyMethod = key.substring(0, sp);
            if (!keyMethod.equals(method)) continue;
            String pattern = key.substring(sp + 1);
            ResolveResult rr = matchPattern(pattern, reqSegments, kv.getValue());
            if (rr != null) return rr;
        }
        // .html 后缀智能匹配（无扩展名路径试 .html）
        if (cleanPath.indexOf('.') < 0) {
            String[] reqSegmentsHtml = splitSegments(cleanPath + ".html");
            for (Map.Entry<String, Entry> kv : table.entrySet()) {
                String key = kv.getKey();
                int sp = key.indexOf(' ');
                if (sp < 0) continue;
                String keyMethod = key.substring(0, sp);
                if (!keyMethod.equals(method)) continue;
                String pattern = key.substring(sp + 1);
                ResolveResult rr = matchPattern(pattern, reqSegmentsHtml, kv.getValue());
                if (rr != null) return rr;
            }
        }
        return null;
    }

    /**
     * 按段比对模板与请求路径；模板段 {name} 提取为 path variable，普通段严格相等。
     */
    private static ResolveResult matchPattern(String pattern, String[] reqSegments, Entry entry) {
        String[] patSegments = splitSegments(pattern);
        if (patSegments.length != reqSegments.length) return null;
        Map<String, String> vars = null;
        for (int i = 0; i < patSegments.length; i++) {
            String p = patSegments[i];
            String r = reqSegments[i];
            if (p.startsWith("{") && p.endsWith("}") && p.length() > 2) {
                String name = p.substring(1, p.length() - 1).trim();
                if (name.isEmpty()) return null;
                if (r.isEmpty()) return null;
                if (vars == null) vars = new LinkedHashMap<>();
                vars.put(name, r);
            } else if (!p.equals(r)) {
                return null;
            }
        }
        return new ResolveResult(entry, vars == null ? java.util.Collections.emptyMap() : vars);
    }

    /**
     * 按 "/" 切分路径段（去除前导斜杠与空段，保留中间空段表示 //）。
     */
    private static String[] splitSegments(String path) {
        if (path == null || path.isEmpty()) return new String[0];
        String p = path.startsWith("/") ? path.substring(1) : path;
        // 末尾斜杠视为一段空字符串已无意义（去除），中间斜杠严格切分
        if (p.endsWith("/")) p = p.substring(0, p.length() - 1);
        if (p.isEmpty()) return new String[0];
        return p.split("/", -1);
    }

    /**
     * 解析结果：命中的登记项 + 路径变量（参数化路由时填充，否则为空 Map）。
     */
    public static final class ResolveResult {
        public final Entry entry;
        public final Map<String, String> pathVariables;

        ResolveResult(Entry entry, Map<String, String> pathVariables) {
            this.entry = entry;
            this.pathVariables = pathVariables == null
                    ? java.util.Collections.emptyMap()
                    : java.util.Collections.unmodifiableMap(pathVariables);
        }
    }

    /**
     * 卸载指定插件名登记的全部网页（监听 PluginDisableEvent 时调用）
     */
    public void unregisterPlugin(String pluginName) {
        if (pluginName == null || pluginName.isEmpty()) return;
        int n = pages.size();
        pages.entrySet().removeIf(e -> pluginName.equals(e.getValue().ownerPlugin));
        int removed = n - pages.size();
        // 一并清理该插件的参数化路由（{name} 模板路由）
        int pn = parameterizedPages.size();
        parameterizedPages.entrySet().removeIf(e -> pluginName.equals(e.getValue().ownerPlugin));
        int pRemoved = pn - parameterizedPages.size();
        // 一并清理该插件的网络页
        int netRemoved = (int) networkPages.entrySet().stream()
                .filter(e -> pluginName.equals(e.getValue().ownerPlugin)).count();
        networkPages.entrySet().removeIf(e -> pluginName.equals(e.getValue().ownerPlugin));
        // 一并清理该插件的昵称路由索引（旧版遗漏：否则已卸载页面的昵称仍可命中）
        int nickRemoved = (int) nicknameIndex.entrySet().stream()
                .filter(e -> pluginName.equals(e.getValue().ownerPlugin)).count();
        nicknameIndex.entrySet().removeIf(e -> pluginName.equals(e.getValue().ownerPlugin));
        if (removed > 0 || pRemoved > 0 || netRemoved > 0 || nickRemoved > 0) {
            log.infoT("log.web.unregister", "卸载网页（插件 {0}）：共 {1} 个{2}{3}{4}", pluginName, removed + pRemoved,
                    pRemoved > 0 ? "，参数化 " + pRemoved + " 个" : "",
                    netRemoved > 0 ? "，网络页 " + netRemoved + " 个" : "",
                    nickRemoved > 0 ? "，昵称 " + nickRemoved + " 个" : "");
        }
        // 一并清理该插件的自定义错误页
        errorPages.entrySet().removeIf(e -> pluginName.equals(e.getValue().ownerPlugin));
    }

    /**
     * 按来源 tag 卸载全部登记项（如 pages.yml 注册的页面标记 tag="pages.yml"，reload 时先卸载再重新注册，
     * 避免已删除路径/昵称残留内存）。清理范围：精确路由（pages）+ 参数化路由（parameterizedPages）
     * + 昵称索引（nicknameIndex）+ 网络页（networkPages，若网络页也带 tag）。
     *
     * @return 卸载数量（0=无匹配）
     */
    public int unregisterByTag(String tag) {
        if (tag == null || tag.isEmpty()) return 0;
        int total = 0;
        int n = pages.size();
        pages.entrySet().removeIf(e -> hasTag(e.getValue().tags, tag));
        total += n - pages.size();
        int pn = parameterizedPages.size();
        parameterizedPages.entrySet().removeIf(e -> hasTag(e.getValue().tags, tag));
        total += pn - parameterizedPages.size();
        int nn = nicknameIndex.size();
        nicknameIndex.entrySet().removeIf(e -> hasTag(e.getValue().tags, tag));
        total += nn - nicknameIndex.size();
        int netN = networkPages.size();
        networkPages.entrySet().removeIf(e -> hasTag(e.getValue().tags, tag));
        total += netN - networkPages.size();
        if (total > 0) {
            log.infoT("log.web.unregister-tag", "按 tag 卸载网页: tag={0} 共 {1} 个", tag, total);
        }
        return total;
    }

    private static boolean hasTag(List<String> tags, String tag) {
        return tags != null && tags.contains(tag);
    }

    // ===== 自定义错误页（registerErrorPage） =====

    /**
     * 自定义错误页：status -> (owner, content)
     */
    private final Map<Integer, ErrorPage> errorPages = new ConcurrentHashMap<>();

    /**
     * 注册自定义错误页（替换通用 404/500 等错误响应；content 为完整 HTML/文本字节）。
     */
    public void registerErrorPage(String ownerPlugin, int status, byte[] content) {
        if (content == null || content.length == 0 || status <= 0) return;
        errorPages.put(status, new ErrorPage(ownerPlugin, content));
        log.infoT("log.web.register-error-page", "已登记自定义错误页 status={0} owner={1}", status, ownerPlugin);
    }

    /**
     * 查询自定义错误页（未注册返回 null）。
     */
    public byte[] errorPage(int status) {
        ErrorPage e = errorPages.get(status);
        return e == null ? null : e.content;
    }

    /**
     * 错误页条目。
     */
    private static final class ErrorPage {
        final String ownerPlugin;
        final byte[] content;

        ErrorPage(String ownerPlugin, byte[] content) {
            this.ownerPlugin = ownerPlugin;
            this.content = content;
        }
    }

    /**
     * 列出全部已登记网页路径（含归属插件），按路径排序；供 /soyshttp pages 查看。
     */
    public List<String> listPaths() {
        List<String> out = new ArrayList<>();
        for (Entry e : pages.values()) {
            out.add(e.path + " (owner=" + (e.ownerPlugin == null ? "?" : e.ownerPlugin) + ")");
        }
        for (Entry e : parameterizedPages.values()) {
            out.add(e.path + " (owner=" + (e.ownerPlugin == null ? "?" : e.ownerPlugin) + ", param)");
        }
        Collections.sort(out);
        return out;
    }

    // ==================== 伺服层公共入口：安装站点首页（GET /） ====================
    // 首页的多实例注册/切换/持久化属于上层业务（由 ihomepages 的 HomepageRegistry 负责），
    // base 仅保留把指定内容安装到 GET / 路由的伺服能力；第三方可经此方法覆盖默认首页。

    /**
     * 直接设置站点首页 {@code GET /} 路由（content 为空则忽略）。
     */
    public void setHomePage(String ownerPlugin, byte[] content, String contentType) {
        if (content == null || content.length == 0) return;
        String key = "GET /";
        String ct = (contentType == null || contentType.isEmpty()) ? MimeTypes.forExt("html") : contentType;
        putEntry(key, new Entry(ownerPlugin, "/", ct, content, null, null, null, 0, null), true);
        log.infoT("log.web.switch-home", "切换首页: GET / current={0}", ownerPlugin == null ? "?" : ownerPlugin);
    }

    /**
     * 列出全部已登记项（Entry 原对象），按路径排序；供 /soyshttp pages 分类展示（区分页/资源/跳转）。
     */
    public List<Entry> listEntries() {
        List<Entry> out = new ArrayList<>(pages.values());
        out.addAll(parameterizedPages.values());
        out.sort((a, b) -> a.path.compareTo(b.path));
        return out;
    }

    // ===== 内部 =====

    private Entry register(Plugin owner, String path, byte[] content, String contentType, boolean proxy) {
        return register(owner, path, content, contentType, proxy, false);
    }

    /**
     * 内部：登记直接内容（显式 force；force=true 强制覆盖重复路径并打印强制登记插件）。
     */
    private Entry register(Plugin owner, String path, byte[] content, String contentType, boolean proxy, boolean force) {
        return register(owner, path, DEFAULT_METHOD, content, contentType, proxy, force, null, null);
    }

    /**
     * 内部：登记直接内容（显式 force + 界面说明 + 昵称路由）。
     */
    private Entry register(Plugin owner, String path, byte[] content, String contentType, boolean proxy, boolean force,
                           String description, List<String> nicknames) {
        return register(owner, path, DEFAULT_METHOD, content, contentType, proxy, force, description, nicknames);
    }

    private Entry register(Plugin owner, String path, byte[] content, String contentType, boolean proxy, boolean force,
                           String description, List<String> nicknames, List<String> permissions) {
        return register(owner, path, DEFAULT_METHOD, content, contentType, proxy, force, description, nicknames, permissions);
    }

    private Entry register(Plugin owner, String path, byte[] content, String contentType, boolean proxy, boolean force,
                           String description, List<String> nicknames, List<String> permissions, List<String> tags) {
        return register(owner, path, DEFAULT_METHOD, content, contentType, proxy, force, description, nicknames, permissions, tags);
    }

    /**
     * 内部：登记直接内容（显式 HTTP 方法 + force + 界面说明 + 昵称路由；无注册权限）。
     * 当 {@code httpMethod} 非默认 GET 或路径含 {name} 占位符时，日志显式标注方法与是否参数化路由。
     */
    private Entry register(Plugin owner, String path, String httpMethod, byte[] content, String contentType,
                           boolean proxy, boolean force, String description, List<String> nicknames) {
        return register(owner, path, httpMethod, content, contentType, proxy, force, description, nicknames, null);
    }

    /**
     * 内部：登记直接内容（显式 HTTP 方法 + force + 界面说明 + 昵称路由 + 注册权限；无 tags）。
     */
    private Entry register(Plugin owner, String path, String httpMethod, byte[] content, String contentType,
                           boolean proxy, boolean force, String description, List<String> nicknames,
                           List<String> permissions) {
        return register(owner, path, httpMethod, content, contentType, proxy, force, description, nicknames, permissions, null);
    }

    /**
     * 内部：登记直接内容（显式 HTTP 方法 + force + 界面说明 + 昵称路由 + 注册权限 + 来源 tags）。
     * 当 {@code httpMethod} 非默认 GET 或路径含 {name} 占位符时，日志显式标注方法与是否参数化路由。
     *
     * @return 登记成功的 Entry；注册失败（重复路径且非 force）返回 null
     */
    private Entry register(Plugin owner, String path, String httpMethod, byte[] content, String contentType,
                           boolean proxy, boolean force, String description, List<String> nicknames,
                           List<String> permissions, List<String> tags) {
        if (path == null || content == null) return null;
        String method = normalizeMethod(httpMethod);
        String ownerName = owner == null ? null : owner.getName();
        String full = resolvePath(ownerName, path, proxy);
        // contentType 为空 → 置 null，服务端按 MimeTypes 请求时实时推断（支持后续 registerMimeType）
        String ct = (contentType == null || contentType.isEmpty()) ? null : contentType;
        boolean param = isParameterized(full);
        Entry entry = putEntry(method + " " + full,
                new Entry(ownerName, method, full, ct, content, null, null, null, 0, null, description, nicknames, permissions, tags), force);
        if (entry == null) return null;
        log.infoT("log.web.register-page", "登记网页: {0} {1} 插件={2}{3}{4}{5}", method, full, ownerName,
                proxy ? I18n.t("log.registry.label.proxy-no-prefix", " (代理无前缀)") : "",
                param ? I18n.t("log.web.label.parameterized", " (参数化)") : "",
                permissions == null || permissions.isEmpty() ? "" : " 权限=" + permissions);
        return entry;
    }

    private Entry registerRes(Plugin owner, String path, ClassLoader cl, String resource, String contentType, boolean proxy) {
        return registerRes(owner, path, DEFAULT_METHOD, cl, resource, contentType, proxy, false, null, null);
    }

    /**
     * 内部：登记 jar 资源（显式 force；force=true 强制覆盖重复路径并打印强制登记插件）。
     */
    private Entry registerRes(Plugin owner, String path, ClassLoader cl, String resource, String contentType, boolean proxy, boolean force) {
        return registerRes(owner, path, DEFAULT_METHOD, cl, resource, contentType, proxy, force, null, null);
    }

    /**
     * 内部：登记 jar 资源（显式 force + 界面说明 + 昵称路由）。
     */
    private Entry registerRes(Plugin owner, String path, ClassLoader cl, String resource, String contentType, boolean proxy, boolean force,
                              String description, List<String> nicknames) {
        return registerRes(owner, path, DEFAULT_METHOD, cl, resource, contentType, proxy, force, description, nicknames, null);
    }

    /**
     * 内部：登记 jar 资源（显式 HTTP 方法 + force + 界面说明 + 昵称路由；无注册权限）。
     */
    private Entry registerRes(Plugin owner, String path, String httpMethod, ClassLoader cl, String resource,
                              String contentType, boolean proxy, boolean force,
                              String description, List<String> nicknames) {
        return registerRes(owner, path, httpMethod, cl, resource, contentType, proxy, force, description, nicknames, null);
    }

    /**
     * 内部：登记 jar 资源（显式 HTTP 方法 + force + 界面说明 + 昵称路由 + 注册权限；无 tags）。
     */
    private Entry registerRes(Plugin owner, String path, String httpMethod, ClassLoader cl, String resource,
                              String contentType, boolean proxy, boolean force,
                              String description, List<String> nicknames, List<String> permissions) {
        return registerRes(owner, path, httpMethod, cl, resource, contentType, proxy, force, description, nicknames, permissions, null);
    }

    /**
     * 内部：登记 jar 资源（显式 HTTP 方法 + force + 界面说明 + 昵称路由 + 注册权限 + 来源 tags）。
     *
     * @return 登记成功的 Entry；注册失败（重复路径且非 force）返回 null
     */
    private Entry registerRes(Plugin owner, String path, String httpMethod, ClassLoader cl, String resource,
                              String contentType, boolean proxy, boolean force,
                              String description, List<String> nicknames, List<String> permissions, List<String> tags) {
        if (path == null || cl == null || resource == null) return null;
        String method = normalizeMethod(httpMethod);
        String ownerName = owner == null ? null : owner.getName();
        String full = resolvePath(ownerName, path, proxy);
        String ct = (contentType == null || contentType.isEmpty()) ? null : contentType;
        boolean param = isParameterized(full);
        Entry entry = putEntry(method + " " + full,
                new Entry(ownerName, method, full, ct, null, cl, resource, null, 0, null, description, nicknames, permissions, tags), force);
        if (entry == null) return null;
        log.infoT("log.web.register-resource", "登记网页(资源): {0} {1} 插件={2}{3}{4}{5}", method, full, ownerName,
                proxy ? " (代理无前缀)" : "",
                param ? I18n.t("log.web.label.parameterized", " (参数化)") : "",
                permissions == null || permissions.isEmpty() ? "" : " 权限=" + permissions);
        return entry;
    }

    /**
     * 计算最终路径：非主插件且非代理 → 前置 /plugins/<插件名>
     */
    private String resolvePath(String ownerName, String path, boolean proxy) {
        String p = path.startsWith("/") ? path : "/" + path;
        if (!proxy && ownerName != null && !ownerName.equals(hostName)) {
            p = "/plugins/" + ownerName + p;
        }
        return p;
    }

    /**
     * 递归扫描磁盘目录并逐项登记为磁盘惰性资源（请求时再读文件，支持热替换）。
     */
    private void walkDirectory(String ownerName, String basePath, File dir, boolean proxy) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                walkDirectory(ownerName, joinWeb(basePath, f.getName()), f, proxy);
            } else if (f.isFile()) {
                String full = resolvePath(ownerName, joinWeb(basePath, f.getName()), proxy);
                // contentType 置 null → 服务端按扩展名实时推断（支持热替换 MimeTypes）
                // 重复路径默认阻止
                putEntry("GET " + full, new Entry(ownerName, full, null, null, null, null, null, 0, f), false);
            }
        }
        log.infoT("log.web.register-disk-dir", "批量登记磁盘目录: {0} base={1} dir={2}", ownerName, basePath, dir.getAbsolutePath());
    }

    /**
     * 拼接 web 路径片段（保证单层斜杠，根前缀 / 不产生双斜杠）。
     */
    private static String joinWeb(String a, String b) {
        String x = a.endsWith("/") ? a.substring(0, a.length() - 1) : a;
        String y = b.startsWith("/") ? b : "/" + b;
        return x + y;
    }

    /**
     * 取插件 jar 文件（用于扫描 jar 内资源目录）；取不到返回 null。
     */
    private static File pluginJar(Plugin p) {
        try {
            java.security.CodeSource cs = p.getClass().getProtectionDomain().getCodeSource();
            if (cs != null && cs.getLocation() != null) {
                java.net.URL loc = cs.getLocation();
                if ("file".equals(loc.getProtocol())) {
                    return new File(loc.toURI());
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * 读取磁盘文件为字节（登记目录用的惰性资源）。
     */
    private static byte[] readFile(File f) throws IOException {
        try (FileInputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
            return out.toByteArray();
        }
    }

    /**
     * 登记项：保存来源与元数据，按需解析字节内容；redirectTo 非空时表示跳转。
     */
    public static class Entry {
        public final String ownerPlugin;
        /**
         * HTTP 方法（大写：GET/POST/PUT/DELETE/PATCH 等；默认 GET）。
         */
        public final String httpMethod;
        public final String path;
        public final String contentType;
        public final String redirectTo;    // 非空 = 跳转目标（Location）
        public final int redirectCode;     // 302 / 301 ...
        private final byte[] content;       // 直接内容（优先）
        private final ClassLoader resCl;     // 资源类加载器（按需读 jar 内资源）
        private final String resource;
        private final File diskFile;         // 磁盘文件（登记目录用，惰性读取，支持热替换）
        /**
         * 界面说明：/soyshttp pages 展示时若存在自动拼接 “ —— ”+description。
         */
        public final String description;
        /**
         * 昵称路由（别名 URL 路径，如 "/主页"）：访问昵称同样命中本登记项内容。
         */
        public final List<String> nicknames;
        /**
         * 访问所需权限（注册时声明，AND 语义；null=未声明）。
         * 判定优先级：pages.yml 单页内联 &gt; 本注册权限 &gt; pages.yml 全局。
         */
        public final List<String> permissions;
        /**
         * 来源标记（如 pages.yml 注册的页面标记 &quot;pages.yml&quot;）：用于 reload 时按 tag 定向卸载
         * （{@link #unregisterByTag(String)}）。null=未标记。
         */
        public final List<String> tags;

        /**
         * 磁盘文件（null=非磁盘来源）；供内容缓存做热替换失效（lastModified）与大文件加载器判定。
         */
        public File getDiskFile() {
            return diskFile;
        }

        Entry(String ownerPlugin, String path, String contentType, byte[] content,
              ClassLoader resCl, String resource, String redirectTo, int redirectCode, File diskFile) {
            this(ownerPlugin, DEFAULT_METHOD, path, contentType, content, resCl, resource, redirectTo, redirectCode, diskFile, null, null);
        }

        Entry(String ownerPlugin, String path, String contentType, byte[] content,
              ClassLoader resCl, String resource, String redirectTo, int redirectCode, File diskFile,
              String description, List<String> nicknames) {
            this(ownerPlugin, DEFAULT_METHOD, path, contentType, content, resCl, resource, redirectTo, redirectCode, diskFile, description, nicknames, null);
        }

        Entry(String ownerPlugin, String path, String contentType, byte[] content,
              ClassLoader resCl, String resource, String redirectTo, int redirectCode, File diskFile,
              String description, List<String> nicknames, List<String> permissions) {
            this(ownerPlugin, DEFAULT_METHOD, path, contentType, content, resCl, resource, redirectTo, redirectCode,
                    diskFile, description, nicknames, permissions, null);
        }

        Entry(String ownerPlugin, String path, String contentType, byte[] content,
              ClassLoader resCl, String resource, String redirectTo, int redirectCode, File diskFile,
              String description, List<String> nicknames, List<String> permissions, List<String> tags) {
            this(ownerPlugin, DEFAULT_METHOD, path, contentType, content, resCl, resource, redirectTo, redirectCode,
                    diskFile, description, nicknames, permissions, tags);
        }

        /**
         * 全参数构造器（带显式 HTTP 方法；无注册权限）。
         */
        Entry(String ownerPlugin, String httpMethod, String path, String contentType, byte[] content,
              ClassLoader resCl, String resource, String redirectTo, int redirectCode, File diskFile,
              String description, List<String> nicknames) {
            this(ownerPlugin, httpMethod, path, contentType, content, resCl, resource, redirectTo, redirectCode,
                    diskFile, description, nicknames, null);
        }

        /**
         * 全参数构造器（带显式 HTTP 方法 + 注册权限）。
         *
         * @param httpMethod HTTP 方法（GET/POST/PUT/DELETE/PATCH 等；null/空 → GET）
         * @param permissions 访问所需权限（null/空=未声明）
         */
        Entry(String ownerPlugin, String httpMethod, String path, String contentType, byte[] content,
              ClassLoader resCl, String resource, String redirectTo, int redirectCode, File diskFile,
              String description, List<String> nicknames, List<String> permissions) {
            this(ownerPlugin, httpMethod, path, contentType, content, resCl, resource, redirectTo, redirectCode,
                    diskFile, description, nicknames, permissions, null);
        }

        Entry(String ownerPlugin, String httpMethod, String path, String contentType, byte[] content,
              ClassLoader resCl, String resource, String redirectTo, int redirectCode, File diskFile,
              String description, List<String> nicknames, List<String> permissions, List<String> tags) {
            this.ownerPlugin = ownerPlugin;
            this.httpMethod = (httpMethod == null || httpMethod.trim().isEmpty())
                    ? DEFAULT_METHOD : httpMethod.trim().toUpperCase();
            this.path = path;
            this.contentType = contentType;
            this.content = content;
            this.resCl = resCl;
            this.resource = resource;
            this.redirectTo = redirectTo;
            this.redirectCode = redirectCode;
            this.diskFile = diskFile;
            this.description = description;
            this.nicknames = (nicknames == null || nicknames.isEmpty()) ? null : new ArrayList<>(nicknames);
            this.permissions = (permissions == null || permissions.isEmpty()) ? null : new ArrayList<>(permissions);
            this.tags = (tags == null || tags.isEmpty()) ? null : new ArrayList<>(tags);
        }

        public byte[] resolveBytes() {
            if (content != null) return content;
            if (diskFile != null) {
                try {
                    return readFile(diskFile);
                } catch (Exception ignored) {
                }
            }
            if (resCl != null && resource != null) {
                // ClassLoader.getResourceAsStream 不接受前导 '/'（Spigot PluginClassLoader 会解析失败），须剥掉
                String res = resource.startsWith("/") ? resource.substring(1) : resource;
                try (InputStream in = resCl.getResourceAsStream(res)) {
                    if (in != null) return toBytes(in);
                } catch (Exception ignored) {
                }
            }
            return new byte[0];
        }

        /**
         * 生效的 Content-Type：显式指定则原样返回；否则<b>请求时</b>按当前 {@link MimeTypes} 实时推断
         * （这样第三方插件先 registerDirectory 再 registerMimeType 也能让新扩展名立即生效）。
         */
        public String effectiveContentType() {
            if (contentType != null && !contentType.isEmpty()) return contentType;
            return MimeTypes.forPath(path);
        }

        /**
         * 是否为跳转入口（302/301 等）。
         */
        public boolean isRedirect() {
            return redirectTo != null;
        }

        /**
         * 是否为可在浏览器直接打开的 HTML 页（.html 后缀或 text/html 类型或站点首页 /）。
         */
        public boolean isHtmlPage() {
            if (path.equals("/") || MimeTypes.isHtmlPath(path)) return true;
            return MimeTypes.isHtmlType(effectiveContentType());
        }

        /**
         * 是否归类为“可打开界面”：HTML 页或跳转入口（点击即可到达某 UI）。脚本/图片/字体等纯资源返回 false。
         */
        public boolean isNavigable() {
            return isRedirect() || isHtmlPage();
        }

        /**
         * 简要种类标签：用于 /soyshttp pages 展示（页 / 资源 / 跳转→目标，i18n）。
         */
        public String kindLabel() {
            if (isRedirect()) return I18n.t("web.entry.kind-redirect", "跳转→") + redirectTo;
            if (isHtmlPage()) return I18n.t("web.entry.kind-page", "页");
            return I18n.t("web.entry.kind-resource", "资源");
        }
    }

    private static byte[] toBytes(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) > 0) out.write(b, 0, n);
        return out.toByteArray();
    }
}
