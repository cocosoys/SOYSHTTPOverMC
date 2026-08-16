package soys.soyshttpovermc.web;

import soys.soyshttpovermc.log.LogKit;

import org.bukkit.plugin.Plugin;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
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
 *       （例如 {@code /dashboard}）；ownerPlugin 仍标记为真实插件，故插件卸载时仍会一并清理；
 *       {@code registerHome} 可强制覆盖内置首页 {@code /}；</li>
 *   <li><b>跳转</b>：{@code registerRedirect} / {@code registerProxyRedirect} 登记 302/301 跳转，
 *       访问 A 网址时浏览器自动跳转到 B 网址（可跳转站内路径或站外 URL）；</li>
 *   <li><b>内容来源</b>：{@code registerPage} 直接提供字节内容；{@code registerResource} 提供插件自有
 *       jar 内的资源路径（按需读取，省内存）；二者均按原始路径扩展名推断 Content-Type（可显式覆盖）；
 *       <b>.html 后缀智能匹配</b>：注册 {@code /login} 后，{@code /login} 与 {@code /login.html} 均可访问；</li>
 *   <li><b>生命周期</b>：插件被禁用时（{@code PluginDisableEvent}）网关自动卸载其名下全部网页，
 *       亦可调用 {@link #unregisterPlugin(String)} 显式卸载。</li>
 * </ul>
 */
public class WebRegistry {

    /** 路由表：key = "GET <路径>"，value = 登记项 */
    private final Map<String, Entry> pages = new ConcurrentHashMap<>();
    /** 宿主插件名（SOYSHTTPOverMC 本体）：其登记不加 /plugins 前缀 */
    private final String hostName;

    public WebRegistry(String hostName) {
        this.hostName = hostName == null ? "" : hostName;
    }

    // ===== 普通登记（自动 /plugins/<插件名> 前缀） =====

    /** 登记网页（直接内容；Content-Type 按路径扩展名推断） */
    public void registerPage(Plugin owner, String path, byte[] content) {
        registerPage(owner, path, content, null);
    }

    /** 登记网页（直接内容；显式 Content-Type） */
    public void registerPage(Plugin owner, String path, byte[] content, String contentType) {
        register(owner, path, content, contentType, false);
    }

    /** 登记网页（来自插件自有 jar 的资源；Content-Type 按路径扩展名推断） */
    public void registerResource(Plugin owner, String path, ClassLoader resourceClassLoader, String resourcePath) {
        registerResource(owner, path, resourceClassLoader, resourcePath, null);
    }

    /** 登记网页（来自插件自有 jar 的资源；显式 Content-Type） */
    public void registerResource(Plugin owner, String path, ClassLoader resourceClassLoader, String resourcePath, String contentType) {
        registerRes(owner, path, resourceClassLoader, resourcePath, contentType, false);
    }

    // ===== 强制代理登记（无 /plugins/<插件名> 前缀） =====

    /** 强制以主插件代理登记网页（直接内容；Content-Type 按路径扩展名推断） */
    public void registerProxyPage(Plugin owner, String path, byte[] content) {
        registerProxyPage(owner, path, content, null);
    }

    /** 强制以主插件代理登记网页（直接内容；显式 Content-Type） */
    public void registerProxyPage(Plugin owner, String path, byte[] content, String contentType) {
        register(owner, path, content, contentType, true);
    }

    /** 强制以主插件代理登记网页（来自插件自有 jar 的资源） */
    public void registerProxyResource(Plugin owner, String path, ClassLoader resourceClassLoader, String resourcePath) {
        registerProxyResource(owner, path, resourceClassLoader, resourcePath, null);
    }

    /** 强制以主插件代理登记网页（来自插件自有 jar 的资源；显式 Content-Type） */
    public void registerProxyResource(Plugin owner, String path, ClassLoader resourceClassLoader, String resourcePath, String contentType) {
        registerRes(owner, path, resourceClassLoader, resourcePath, contentType, true);
    }

    /**
     * 强制代理首页：以主插件名义覆盖内置门户首页 {@code /}（第三方自制首页）。
     * 等价于 {@code registerProxyPage(owner, "/", content, contentType)}；WebRegistry 路由先于内置静态资源命中。
     */
    public void registerHome(Plugin owner, byte[] content) {
        registerHome(owner, content, "text/html; charset=utf-8");
    }

    /** 强制代理首页（显式 Content-Type）。 */
    public void registerHome(Plugin owner, byte[] content, String contentType) {
        registerProxyPage(owner, "/", content, contentType);
    }

    // ===== 跳转登记（A 网址 → B 网址，302/301） =====

    /** 登记跳转（默认 302，路径自动补 /plugins/<插件名> 前缀）。访问 A 时浏览器自动跳转到 B。 */
    public void registerRedirect(Plugin owner, String fromPath, String toPath) {
        registerRedirect(owner, fromPath, toPath, false, 302);
    }

    /** 登记跳转（显式状态码 301/302 等）。 */
    public void registerRedirect(Plugin owner, String fromPath, String toPath, int statusCode) {
        registerRedirect(owner, fromPath, toPath, false, statusCode);
    }

    /** 强制代理跳转（无 /plugins/<插件名> 前缀；默认 302）。 */
    public void registerProxyRedirect(Plugin owner, String fromPath, String toPath) {
        registerRedirect(owner, fromPath, toPath, true, 302);
    }

    /** 强制代理跳转（显式状态码）。 */
    public void registerProxyRedirect(Plugin owner, String fromPath, String toPath, int statusCode) {
        registerRedirect(owner, fromPath, toPath, true, statusCode);
    }

    /** 内部：登记跳转项。 */
    private void registerRedirect(Plugin owner, String fromPath, String toPath, boolean proxy, int statusCode) {
        if (fromPath == null || toPath == null) return;
        String ownerName = owner == null ? null : owner.getName();
        String full = resolvePath(ownerName, fromPath, proxy);
        pages.put("GET " + full, new Entry(ownerName, full, null, null, null, null, toPath, statusCode, null));
        LogKit.info("[HTTP-Over-MC] 登记跳转: GET " + full + " → " + toPath + " (" + statusCode + ")"
                + " 插件=" + ownerName + (proxy ? " (代理无前缀)" : ""));
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

    /** 目录批量登记（显式是否强制代理无前缀）。 */
    public void registerDirectory(Plugin owner, String basePath, File dir, boolean proxy) {
        if (owner == null || basePath == null || dir == null || !dir.isDirectory()) return;
        walkDirectory(owner.getName(), basePath, dir, proxy);
    }

    /** 强制代理目录批量登记（无 /plugins/&lt;插件名&gt; 前缀）。 */
    public void registerProxyDirectory(Plugin owner, String basePath, File dir) {
        registerDirectory(owner, basePath, dir, true);
    }

    /**
     * 批量登记插件 jar 内资源目录（如 resources/web/ 下的前端产物）：扫描插件 jar 中
     * {@code resourceRoot} 前缀下的全部条目，挂到 {@code basePath} 下。
     */
    public void registerResourceDirectory(Plugin owner, String basePath, ClassLoader resourceClassLoader, String resourceRoot) {
        registerResourceDirectory(owner, basePath, resourceClassLoader, resourceRoot, false);
    }

    /** jar 资源目录批量登记（显式是否强制代理无前缀）。 */
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
                pages.put("GET " + full, new Entry(owner.getName(), full, null,
                        null, resourceClassLoader, "/" + name, null, 0, null));
            }
            LogKit.info("[HTTP-Over-MC] 批量登记 jar 目录: " + owner.getName() + " root=" + resourceRoot + " base=" + basePath);
        } catch (Exception ex) {
            LogKit.warn("[HTTP-Over-MC] 批量登记 jar 目录失败: " + owner.getName() + " -> " + ex.getMessage());
        }
    }

    /** 强制代理 jar 资源目录批量登记。 */
    public void registerProxyResourceDirectory(Plugin owner, String basePath, ClassLoader resourceClassLoader, String resourceRoot) {
        registerResourceDirectory(owner, basePath, resourceClassLoader, resourceRoot, true);
    }

    // ===== 解析 / 卸载 =====

    /**
     * 按方法 + 路径解析已登记网页；未命中返回 null。
     * 支持 .html 后缀智能匹配：注册 /login 后，访问 /login 与 /login.html 均可命中（反之亦然）。
     */
    public Entry resolve(String httpMethod, String cleanPath) {
        String method = httpMethod == null ? "GET" : httpMethod.toUpperCase();
        if (cleanPath == null) return null;
        Entry e = pages.get(method + " " + cleanPath);
        if (e != null) return e;
        // .html 兼容：/login ↔ /login.html
        if (cleanPath.indexOf('.') < 0) {
            return pages.get(method + " " + cleanPath + ".html");
        }
        if (cleanPath.endsWith(".html") && cleanPath.length() > 5) {
            return pages.get(method + " " + cleanPath.substring(0, cleanPath.length() - 5));
        }
        return null;
    }

    /** 卸载指定插件名登记的全部网页（监听 PluginDisableEvent 时调用） */
    public void unregisterPlugin(String pluginName) {
        if (pluginName == null || pluginName.isEmpty()) return;
        int n = pages.size();
        pages.entrySet().removeIf(e -> pluginName.equals(e.getValue().ownerPlugin));
        int removed = n - pages.size();
        if (removed > 0) {
            LogKit.info("[HTTP-Over-MC] 卸载网页（插件 " + pluginName + "）：共 " + removed + " 个");
        }
    }

    /** 列出全部已登记网页路径（含归属插件），按路径排序；供 /soyshttp pages 查看。 */
    public List<String> listPaths() {
        List<String> out = new ArrayList<>();
        for (Entry e : pages.values()) {
            out.add(e.path + " (owner=" + (e.ownerPlugin == null ? "?" : e.ownerPlugin) + ")");
        }
        Collections.sort(out);
        return out;
    }

    /** 列出全部已登记项（Entry 原对象），按路径排序；供 /soyshttp pages 分类展示（区分页/资源/跳转）。 */
    public List<Entry> listEntries() {
        List<Entry> out = new ArrayList<>(pages.values());
        out.sort((a, b) -> a.path.compareTo(b.path));
        return out;
    }

    // ===== 内部 =====

    private void register(Plugin owner, String path, byte[] content, String contentType, boolean proxy) {
        if (path == null || content == null) return;
        String ownerName = owner == null ? null : owner.getName();
        String full = resolvePath(ownerName, path, proxy);
        // contentType 为空 → 置 null，服务端按 MimeTypes 请求时实时推断（支持后续 registerMimeType）
        String ct = (contentType == null || contentType.isEmpty()) ? null : contentType;
        pages.put("GET " + full, new Entry(ownerName, full, ct, content, null, null, null, 0, null));
        LogKit.info("[HTTP-Over-MC] 登记网页: GET " + full + " 插件=" + ownerName + (proxy ? " (代理无前缀)" : ""));
    }

    private void registerRes(Plugin owner, String path, ClassLoader cl, String resource, String contentType, boolean proxy) {
        if (path == null || cl == null || resource == null) return;
        String ownerName = owner == null ? null : owner.getName();
        String full = resolvePath(ownerName, path, proxy);
        String ct = (contentType == null || contentType.isEmpty()) ? null : contentType;
        pages.put("GET " + full, new Entry(ownerName, full, ct, null, cl, resource, null, 0, null));
        LogKit.info("[HTTP-Over-MC] 登记网页(资源): GET " + full + " 插件=" + ownerName + (proxy ? " (代理无前缀)" : ""));
    }

    /** 计算最终路径：非主插件且非代理 → 前置 /plugins/<插件名> */
    private String resolvePath(String ownerName, String path, boolean proxy) {
        String p = path.startsWith("/") ? path : "/" + path;
        if (!proxy && ownerName != null && !ownerName.equals(hostName)) {
            p = "/plugins/" + ownerName + p;
        }
        return p;
    }

    /** 递归扫描磁盘目录并逐项登记为磁盘惰性资源（请求时再读文件，支持热替换）。 */
    private void walkDirectory(String ownerName, String basePath, File dir, boolean proxy) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                walkDirectory(ownerName, joinWeb(basePath, f.getName()), f, proxy);
            } else if (f.isFile()) {
                String full = resolvePath(ownerName, joinWeb(basePath, f.getName()), proxy);
                // contentType 置 null → 服务端按扩展名实时推断（支持热替换 MimeTypes）
                pages.put("GET " + full, new Entry(ownerName, full, null, null, null, null, null, 0, f));
            }
        }
        LogKit.info("[HTTP-Over-MC] 批量登记磁盘目录: " + ownerName + " base=" + basePath + " dir=" + dir.getAbsolutePath());
    }

    /** 拼接 web 路径片段（保证单层斜杠，根前缀 / 不产生双斜杠）。 */
    private static String joinWeb(String a, String b) {
        String x = a.endsWith("/") ? a.substring(0, a.length() - 1) : a;
        String y = b.startsWith("/") ? b : "/" + b;
        return x + y;
    }

    /** 取插件 jar 文件（用于扫描 jar 内资源目录）；取不到返回 null。 */
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

    /** 读取磁盘文件为字节（登记目录用的惰性资源）。 */
    private static byte[] readFile(File f) throws IOException {
        try (FileInputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
            return out.toByteArray();
        }
    }

    /** 登记项：保存来源与元数据，按需解析字节内容；redirectTo 非空时表示跳转。 */
    public static final class Entry {
        public final String ownerPlugin;
        public final String path;
        public final String contentType;
        public final String redirectTo;    // 非空 = 跳转目标（Location）
        public final int redirectCode;     // 302 / 301 ...
        private final byte[] content;       // 直接内容（优先）
        private final ClassLoader resCl;     // 资源类加载器（按需读 jar 内资源）
        private final String resource;
        private final File diskFile;         // 磁盘文件（登记目录用，惰性读取，支持热替换）

        Entry(String ownerPlugin, String path, String contentType, byte[] content,
              ClassLoader resCl, String resource, String redirectTo, int redirectCode, File diskFile) {
            this.ownerPlugin = ownerPlugin;
            this.path = path;
            this.contentType = contentType;
            this.content = content;
            this.resCl = resCl;
            this.resource = resource;
            this.redirectTo = redirectTo;
            this.redirectCode = redirectCode;
            this.diskFile = diskFile;
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

        /** 是否为跳转入口（302/301 等）。 */
        public boolean isRedirect() {
            return redirectTo != null;
        }

        /** 是否为可在浏览器直接打开的 HTML 页（.html 后缀或 text/html 类型或站点首页 /）。 */
        public boolean isHtmlPage() {
            if (path.equals("/") || path.endsWith(".html")) return true;
            return effectiveContentType().startsWith("text/html");
        }

        /** 是否归类为“可打开界面”：HTML 页或跳转入口（点击即可到达某 UI）。脚本/图片/字体等纯资源返回 false。 */
        public boolean isNavigable() {
            return isRedirect() || isHtmlPage();
        }

        /** 简要种类标签：用于 /soyshttp pages 展示（页 / 资源 / 跳转→目标）。 */
        public String kindLabel() {
            if (isRedirect()) return "跳转→" + redirectTo;
            if (isHtmlPage()) return "页";
            return "资源";
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
