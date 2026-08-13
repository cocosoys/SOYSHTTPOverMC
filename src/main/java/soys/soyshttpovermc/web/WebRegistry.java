package soys.soyshttpovermc.web;

import soys.soyshttpovermc.log.LogKit;

import org.bukkit.plugin.Plugin;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
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
 *       （例如 {@code /dashboard}）；ownerPlugin 仍标记为真实插件，故插件卸载时仍会一并清理；</li>
 *   <li><b>内容来源</b>：{@code registerPage} 直接提供字节内容；{@code registerResource} 提供插件自有
 *       jar 内的资源路径（按需读取，省内存）；二者均按原始路径扩展名推断 Content-Type（可显式覆盖）；</li>
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

    // ===== 解析 / 卸载 =====

    /** 按方法 + 路径解析已登记网页；未命中返回 null */
    public Entry resolve(String httpMethod, String cleanPath) {
        String method = httpMethod == null ? "GET" : httpMethod.toUpperCase();
        return pages.get(method + " " + cleanPath);
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

    // ===== 内部 =====

    private void register(Plugin owner, String path, byte[] content, String contentType, boolean proxy) {
        if (path == null || content == null) return;
        String ownerName = owner == null ? null : owner.getName();
        String full = resolvePath(ownerName, path, proxy);
        String ct = (contentType == null || contentType.isEmpty()) ? MimeTypes.forPath(path) : contentType;
        pages.put("GET " + full, new Entry(ownerName, full, ct, content, null, null));
        LogKit.info("[HTTP-Over-MC] 登记网页: GET " + full + " 插件=" + ownerName + (proxy ? " (代理无前缀)" : ""));
    }

    private void registerRes(Plugin owner, String path, ClassLoader cl, String resource, String contentType, boolean proxy) {
        if (path == null || cl == null || resource == null) return;
        String ownerName = owner == null ? null : owner.getName();
        String full = resolvePath(ownerName, path, proxy);
        String ct = (contentType == null || contentType.isEmpty()) ? MimeTypes.forPath(path) : contentType;
        pages.put("GET " + full, new Entry(ownerName, full, ct, null, cl, resource));
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

    /** 登记项：保存来源与元数据，按需解析字节内容 */
    public static final class Entry {
        public final String ownerPlugin;
        public final String path;
        public final String contentType;
        private final byte[] content;       // 直接内容（优先）
        private final ClassLoader resCl;     // 资源类加载器（按需读 jar 内资源）
        private final String resource;

        Entry(String ownerPlugin, String path, String contentType, byte[] content, ClassLoader resCl, String resource) {
            this.ownerPlugin = ownerPlugin;
            this.path = path;
            this.contentType = contentType;
            this.content = content;
            this.resCl = resCl;
            this.resource = resource;
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
