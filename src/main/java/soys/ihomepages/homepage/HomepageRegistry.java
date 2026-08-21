package soys.ihomepages.homepage;
import lombok.CustomLog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 首页注册表：维护本插件（ihomepages）注册的首页实例，支持注册/切换/注销/列表。
 *
 * <p>首页分两类：</p>
 * <ul>
 *   <li><b>字节型</b>（{@link #register(String, String, byte[], String)}）：内容直接以字节给出，切换时原样安装；</li>
 *   <li><b>来源型</b>（{@link #registerSource(String, String, String)}）：内容是一个相对路径/绝对路径/网络 URL，
 *       切换时经 {@link SourceResolver}（复用 SOYSHTTPOverMC 框架的 {@code HomePageResolver} 解析语义）解析出字节后安装。</li>
 * </ul>
 *
 * <p>切换首页时调用 {@link HomeInstaller} 把内容安装到 SOYSHTTPOverMC 伺服层（{@code GET /} 路由），
 * 与框架层零耦合（业务侧只依赖注入的 {@code webRegistry::setHomePage} 与解析来源的原语）。</p>
 *
 * <p>当前首页名称持久化在 {@code ihomepages/config.yml} 的 {@code homepage.current} 字段
 * （由 {@link HomepageState} 负责读写），服务器重启后自动恢复。</p>
 */
@CustomLog
public class HomepageRegistry {

    /** 首页安装器：把指定首页内容安装到伺服层 {@code GET /} 路由（由宿主注入 {@code webRegistry::setHomePage}）。 */
    @FunctionalInterface
    public interface HomeInstaller {
        void install(String ownerPlugin, byte[] content, String contentType);
    }

    /** 来源型首页解析器：把一个来源描述（相对路径/绝对路径/网络 URL）解析为首页字节与 Content-Type；不可解返回 null。 */
    @FunctionalInterface
    public interface SourceResolver {
        Resolved resolve(String spec);
    }

    /** 来源解析结果。 */
    public static final class Resolved {
        public final byte[] bytes;
        public final String contentType;

        public Resolved(byte[] bytes, String contentType) {
            this.bytes = bytes;
            this.contentType = contentType;
        }
    }

    private final Map<String, HomepageEntry> entries = new LinkedHashMap<>();
    private final HomeInstaller installer;
    private final SourceResolver sourceResolver;
    private String currentName;

    public HomepageRegistry(HomeInstaller installer) {
        this(installer, null);
    }

    public HomepageRegistry(HomeInstaller installer, SourceResolver sourceResolver) {
        this.installer = installer;
        this.sourceResolver = sourceResolver;
    }

    /**
     * 注册一个字节型首页到列表（不自动切换）。
     *
     * @param name        首页唯一名称（如 "default"、"event"）
     * @param ownerPlugin 归属插件名
     * @param content     首页 HTML 字节内容
     * @param contentType Content-Type（如 "text/html; charset=utf-8"）
     */
    public void register(String name, String ownerPlugin, byte[] content, String contentType) {
        if (name == null || name.isEmpty() || content == null || content.length == 0) {
            return;
        }
        String ct = (contentType == null || contentType.isEmpty()) ? "text/html; charset=utf-8" : contentType;
        entries.put(name, new HomepageEntry(name, ownerPlugin, content, ct, null));
        log.infoT("log.homepage.register-byte",
                "[ihomepages] 注册首页: {0} (字节型, 归属={1})", name, ownerPlugin);
    }

    /**
     * 注册一个来源型首页到列表（不自动切换）。内容为一个来源描述（相对路径/绝对路径/网络 URL），
     * 仅在切换到该首页时才经 {@link SourceResolver} 解析。
     *
     * @param name       首页唯一名称
     * @param ownerPlugin 归属插件名
     * @param sourceSpec 内容来源（相对路径/绝对路径/网络 URL）
     * @return true 注册成功；false 参数非法或未配置来源解析器
     */
    public boolean registerSource(String name, String ownerPlugin, String sourceSpec) {
        if (name == null || name.isEmpty() || sourceSpec == null || sourceSpec.trim().isEmpty()) {
            return false;
        }
        if (sourceResolver == null) {
            log.warnT("log.homepage.register-no-resolver",
                    "[ihomepages] 未配置来源解析器，无法注册来源型首页: {0}", name);
            return false;
        }
        entries.put(name, new HomepageEntry(name, ownerPlugin, null, "text/html; charset=utf-8", sourceSpec.trim()));
        log.infoT("log.homepage.register-source",
                "[ihomepages] 注册首页: {0} (来源型 {1}, 归属={2})", name, sourceSpec.trim(), ownerPlugin);
        return true;
    }

    /**
     * 切换到指定名称的首页，立即安装到伺服层 {@code GET /} 路由。
     * 来源型首页会现场解析；解析失败返回 false 且当前首页保持不变。
     *
     * @param name 首页名称
     * @return true 切换成功；false 名称不存在或来源解析失败
     */
    public boolean switchTo(String name) {
        HomepageEntry entry = entries.get(name);
        if (entry == null) {
            return false;
        }
        byte[] content;
        String ct;
        if (entry.content != null) {
            content = entry.content;
            ct = entry.contentType;
        } else if (entry.sourceSpec != null && sourceResolver != null) {
            Resolved r = sourceResolver.resolve(entry.sourceSpec);
            if (r == null || r.bytes == null || r.bytes.length == 0) {
                log.warnT("log.homepage.resolve-failed",
                    "[ihomepages] 来源型首页解析失败({0}, {1})，保持当前首页", name, entry.sourceSpec);
                return false;
            }
            content = r.bytes;
            ct = (r.contentType == null || r.contentType.isEmpty()) ? entry.contentType : r.contentType;
        } else {
            log.warnT("log.homepage.no-content",
                    "[ihomepages] 首页无可用内容({0})，保持当前首页", name);
            return false;
        }
        if (installer != null) {
            installer.install(entry.ownerPlugin, content, ct);
        }
        this.currentName = name;
        return true;
    }

    /**
     * 注销指定名称的首页。若注销的是当前首页，同时清除当前选择与伺服层安装的首页。
     *
     * @param name 首页名称
     * @return true 注销成功；false 名称不存在
     */
    public boolean unregister(String name) {
        HomepageEntry removed = entries.remove(name);
        if (removed == null) {
            return false;
        }
        log.infoT("log.homepage.unregister",
                "[ihomepages] 注销首页: {0} (归属={1})", name, removed.ownerPlugin);
        if (name.equals(currentName)) {
            this.currentName = null;
            if (installer != null) {
                // 空安装仅清空当前首页主体为占位，避免残留旧内容；业务侧可在注销后切换到其他首页
                installer.install(removed.ownerPlugin, new byte[]{}, removed.contentType);
            }
        }
        return true;
    }

    /** 注销全部首页并清空当前选择。返回注销数量。 */
    public int unregisterAll() {
        int n = entries.size();
        if (n > 0) {
            entries.clear();
            this.currentName = null;
            log.infoT("log.homepage.unregister-all",
                "[ihomepages] 注销全部首页: {0} 个", n);
        }
        return n;
    }

    /**
     * 获取指定名称的首页条目。
     *
     * @param name 首页名称
     * @return 首页条目，不存在返回 null
     */
    public HomepageEntry get(String name) {
        return entries.get(name);
    }

    /**
     * 列出所有已注册的首页名称。
     *
     * @return 首页名称列表（按注册顺序）
     */
    public List<String> list() {
        return new ArrayList<>(entries.keySet());
    }

    /** 当前首页名称（可能为 null）。 */
    public String getCurrentName() {
        return currentName;
    }

    /** 设置当前首页名称（仅供恢复持久化选择时使用，不触发路由切换）。 */
    public void setCurrentName(String name) {
        this.currentName = name;
    }

    // ==================== 首页条目 ====================

    /** 首页条目：保存名称、归属插件、内容（二选一：字节 or 来源描述）与 Content-Type。 */
    public static final class HomepageEntry {
        public final String name;
        public final String ownerPlugin;
        public final byte[] content;      // 字节型内容；来源型为 null
        public final String contentType;
        public final String sourceSpec;   // 来源型首页的来源描述；字节型为 null

        HomepageEntry(String name, String ownerPlugin, byte[] content, String contentType, String sourceSpec) {
            this.name = name;
            this.ownerPlugin = ownerPlugin;
            this.content = content;
            this.contentType = contentType;
            this.sourceSpec = sourceSpec;
        }
    }
}
