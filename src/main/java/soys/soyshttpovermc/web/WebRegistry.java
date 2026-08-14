package soys.soyshttpovermc.web;

import soys.soyshttpovermc.log.LogKit;

import org.bukkit.plugin.Plugin;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
        pages.put("GET " + full, new Entry(ownerName, full, null, null, null, null, toPath, statusCode));
        LogKit.info("[HTTP-Over-MC] 登记跳转: GET " + full + " → " + toPath + " (" + statusCode + ")"
                + " 插件=" + ownerName + (proxy ? " (代理无前缀)" : ""));
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

    // ===== 内部 =====

    private void register(Plugin owner, String path, byte[] content, String contentType, boolean proxy) {
        if (path == null || content == null) return;
        String ownerName = owner == null ? null : owner.getName();
        String full = resolvePath(ownerName, path, proxy);
        String ct = (contentType == null || contentType.isEmpty()) ? MimeTypes.forPath(path) : contentType;
        pages.put("GET " + full, new Entry(ownerName, full, ct, content, null, null, null, 0));
        LogKit.info("[HTTP-Over-MC] 登记网页: GET " + full + " 插件=" + ownerName + (proxy ? " (代理无前缀)" : ""));
    }

    private void registerRes(Plugin owner, String path, ClassLoader cl, String resource, String contentType, boolean proxy) {
        if (path == null || cl == null || resource == null) return;
        String ownerName = owner == null ? null : owner.getName();
        String full = resolvePath(ownerName, path, proxy);
        String ct = (contentType == null || contentType.isEmpty()) ? MimeTypes.forPath(path) : contentType;
        pages.put("GET " + full, new Entry(ownerName, full, ct, null, cl, resource, null, 0));
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

        Entry(String ownerPlugin, String path, String contentType, byte[] content,
              ClassLoader resCl, String resource, String redirectTo, int redirectCode) {
            this.ownerPlugin = ownerPlugin;
            this.path = path;
            this.contentType = contentType;
            this.content = content;
            this.resCl = resCl;
            this.resource = resource;
            this.redirectTo = redirectTo;
            this.redirectCode = redirectCode;
        }

        public byte[] resolveBytes() {
            if (content != null) return content;
            if (resCl != null && resource != null) {
                String res = resource.startsWith("/") ? resource : "/" + resource;
                try (InputStream in = resCl.getResourceAsStream(res)) {
                    if (in != null) return toBytes(in);
                } catch (Exception ignored) {
                }
            }
            return new byte[0];
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
