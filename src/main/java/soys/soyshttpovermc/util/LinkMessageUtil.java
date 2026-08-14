package soys.soyshttpovermc.util;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * 向玩家发送可点击链接消息的工具（BungeeCord Chat 组件，Spigot 1.12.2 自带）。
 *
 * <h3>URL 解析（{@link #resolveUrl}）</h3>
 * <ul>
 *   <li>以 {@code /} 开头 → 视为本服已注册页面路径，拼为 {@code https://host:port<path>}
 *       （如参数填 {@code /index} → {@code https://host:port/index}）；</li>
 *   <li>否则 → 视为完整 URL 原样返回。</li>
 * </ul>
 *
 * <h3>显示文字语法（{@link #build}，{@code &} 代替 {@code §} 颜色码）</h3>
 * <ul>
 *   <li>{@code %url%} —— 替换为完整 URL（可点击打开）；</li>
 *   <li>{@code %url_标签%} —— 内联可点击链接，可见文字为 {@code 标签}，点击打开 url（标签内容到下一个 {@code %}
 *       结束；旧写法 {@code %url_[标签]%} 仍兼容，方括号会被原样显示为标签的一部分）；
 *       例：{@code 点击%url_星云官网%打开官网} → 聊天显示“点击星云官网打开官网”，其中“星云官网”可点击。</li>
 * </ul>
 */
public final class LinkMessageUtil {

    private LinkMessageUtil() {
    }

    /** 解析链接目标：以 / 开头视为本服页面路径，拼 https://host:port；否则原样返回。 */
    public static String resolveUrl(String raw, String host, int port) {
        if (raw == null || raw.isEmpty()) return "";
        if (raw.startsWith("/")) {
            return "https://" + host + ":" + port + raw;
        }
        return raw;
    }

    /**
     * 把显示文字解析为可点击组件链。display 为空时返回一条以 url 为标签的可点击链接。
     */
    public static BaseComponent[] build(String display, String url) {
        if (url == null) url = "";
        if (display == null || display.isEmpty()) {
            return makeLink(url, url);
        }
        String text = display.replace('&', '§');
        List<BaseComponent> out = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        int i = 0, n = text.length();
        while (i < n) {
            // %url_标签% —— 标签到下一个 % 结束（旧写法 %url_[标签]% 的方括号被当作标签内容，天然兼容）
            if (text.startsWith("%url_", i)) {
                int endPct = text.indexOf('%', i + 5);
                if (endPct > i + 5) {
                    flushPlain(out, buf);
                    String label = text.substring(i + 5, endPct);
                    add(out, makeLink(label, url));
                    i = endPct + 1;
                    continue;
                }
            }
            // %url%
            if (text.startsWith("%url%", i)) {
                flushPlain(out, buf);
                add(out, makeLink(url, url));
                i += 5;
                continue;
            }
            buf.append(text.charAt(i));
            i++;
        }
        flushPlain(out, buf);
        if (out.isEmpty()) out.add(new TextComponent(""));
        return out.toArray(new BaseComponent[0]);
    }

    /** 向玩家发送链接消息（命令路径天然在主线程；若从其他线程调用请自行切主线程）。 */
    public static void send(Player player, String url, String display) {
        if (player == null) return;
        player.spigot().sendMessage(build(display, url));
    }

    /** 生成可点击链接组件（标签为空时用 url 本身作标签）。 */
    private static BaseComponent[] makeLink(String label, String url) {
        String colored = (label == null || label.isEmpty()) ? url : label.replace('&', '§');
        BaseComponent[] parts = TextComponent.fromLegacyText(colored);
        BaseComponent[] hover = new BaseComponent[]{ new TextComponent("点击打开: " + url) };
        for (BaseComponent p : parts) {
            p.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
            p.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover));
        }
        return parts;
    }

    private static void flushPlain(List<BaseComponent> out, StringBuilder buf) {
        if (buf.length() == 0) return;
        for (BaseComponent p : TextComponent.fromLegacyText(buf.toString())) out.add(p);
        buf.setLength(0);
    }

    private static void add(List<BaseComponent> out, BaseComponent[] parts) {
        for (BaseComponent p : parts) out.add(p);
    }
}
