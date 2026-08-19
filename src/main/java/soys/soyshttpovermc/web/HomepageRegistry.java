package soys.soyshttpovermc.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 首页注册表：维护所有插件注册的首页实例，支持按名称切换。
 * <p>所有首页以 {@link #register(String, String, byte[], String)} 存入内存，
 * 通过 {@link #switchTo(String)} 即时切换站点首页（{@code GET /}），
 * 切换时直接更新 {@link WebRegistry} 的路由表。</p>
 *
 * <p>首页列表全局共享（所有插件均可注册），当前选择可通过 {@code /soyshttp homepage} 指令查看和切换。
 * 当前首页名称持久化在 {@code config.yml} 的 {@code homepage.current} 字段，服务器重启后自动恢复。</p>
 *
 * <p>预留 {@link #broadcastSwitch(String)} 方法供未来跨服同步使用（暂不实现）。</p>
 */
public class HomepageRegistry {

    private final Map<String, HomepageEntry> entries = new LinkedHashMap<>();
    private final WebRegistry webRegistry;
    private final String hostPluginName;
    private String currentName;

    HomepageRegistry(WebRegistry webRegistry, String hostPluginName) {
        this.webRegistry = webRegistry;
        this.hostPluginName = hostPluginName == null ? "" : hostPluginName;
    }

    /**
     * 注册一个首页到列表（不自动切换）。
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
        entries.put(name, new HomepageEntry(name, ownerPlugin, content, ct));
    }

    /**
     * 切换到指定名称的首页，立即更新 {@link WebRegistry} 的 {@code GET /} 路由。
     *
     * @param name 首页名称
     * @return true 切换成功；false 名称不存在
     */
    public boolean switchTo(String name) {
        HomepageEntry entry = entries.get(name);
        if (entry == null) {
            return false;
        }
        webRegistry.setHomePage(entry.ownerPlugin, entry.content, entry.contentType);
        this.currentName = name;
        return true;
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

    // ==================== 跨服同步预留 ====================

    /**
     * 预留跨服首页同步函数：将首页切换广播到集群中的其他服务器实例。
     * <p>当前为占位实现，待后续接入跨服通信后实现。</p>
     *
     * @param name 切换到的首页名称
     */
    public void broadcastSwitch(String name) {
        // 跨服同步：预留，暂不实现
    }

    // ==================== 首页条目 ====================

    /** 首页条目：保存名称、归属插件、内容与 Content-Type。 */
    public static final class HomepageEntry {
        public final String name;
        public final String ownerPlugin;
        public final byte[] content;
        public final String contentType;

        HomepageEntry(String name, String ownerPlugin, byte[] content, String contentType) {
            this.name = name;
            this.ownerPlugin = ownerPlugin;
            this.content = content;
            this.contentType = contentType;
        }
    }
}