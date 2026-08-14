package soys.soyshttpovermc.api;

import org.bukkit.entity.Player;

import java.util.Collection;

/**
 * 能力组 4：工具（对象 → JSON、扩展名 → Content-Type、发送链接消息）。
 * 由 {@link SoysHttpOverMcApi#getToolkit()} 跳转获取。
 */
public interface ApiToolkitApi {

    /** 任意对象 → JSON 字符串（复用 JsonWriter） */
    String toJson(Object obj);

    /** 扩展名 → Content-Type（复用 MimeTypes） */
    String guessContentType(String path);

    /**
     * 向玩家发送一条可点击链接消息（url 应为完整 URL；display 支持 %url% 与 %url_[标签]%，
     * {@code &} 代替 {@code §} 颜色码）。详见 {@code soys.soyshttpovermc.util.LinkMessageUtil}。
     */
    void sendLink(Player player, String url, String display);

    /** 批量向多名玩家发送同一条链接消息。 */
    void sendLink(Collection<? extends Player> players, String url, String display);
}
