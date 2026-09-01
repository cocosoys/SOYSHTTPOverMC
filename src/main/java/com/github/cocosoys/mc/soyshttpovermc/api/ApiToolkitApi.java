package com.github.cocosoys.mc.soyshttpovermc.api;

import org.bukkit.entity.Player;

import java.util.Collection;

/**
 * 能力组 4：工具（对象 → JSON、扩展名 → Content-Type、发送链接消息）。
 * 由 {@link SoysHttpOverMcApi#getToolkit()} 跳转获取。
 */
public interface ApiToolkitApi {

    /**
     * 任意对象 → JSON 字符串（复用 JsonWriter）
     */
    String toJson(Object obj);

    /**
     * 扩展名 → Content-Type（复用 MimeTypes）
     */
    String guessContentType(String path);

    /**
     * 注册/覆盖扩展名的 Content-Type（全局生效，线程安全）。自定义扩展名（如 vue / ts / json5）
     * 需先注册，浏览器才会按正确类型渲染/执行对应静态资源。
     *
     * @param ext         扩展名（不含点，如 {@code "vue"}）
     * @param contentType 完整 Content-Type（如 {@code "text/html; charset=utf-8"}）
     */
    void registerMimeType(String ext, String contentType);

    /**
     * 向玩家发送一条可点击链接消息（url 应为完整 URL；display 支持 %url% 与 %url_标签%（标签到下一个 % 结束，
     * 旧写法 %url_[标签]% 仍兼容），{@code &} 代替 {@code §} 颜色码）。详见 {@code soys.soyshttpovermc.util.LinkMessageUtil}。
     */
    void sendLink(Player player, String url, String display);

    /**
     * 批量向多名玩家发送同一条链接消息。
     */
    void sendLink(Collection<? extends Player> players, String url, String display);
}
