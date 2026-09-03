package com.github.cocosoys.mc.soyshttpovermc.adapter.compat;

import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 可点击链接消息兼容工具。
 *
 * <p>发送带「点击打开 URL」的聊天链接需要 Spigot API（{@code Player#spigot()} + bungee chat），
 * 而 <b>CraftBukkit 低版本（含 1.6.4 / 1.7.10）没有这些类</b>。本工具用反射探测运行时是否存在
 * Spigot API：存在则发送可点击组件，否则降级为普通文本消息（链接明文附在显示文字后）。</p>
 *
 * <p>编译基线为 1.6.4，仅依赖 {@link Player#sendMessage(String)} 等全版本存在的方法。</p>
 */
public final class ChatCompat {

    private static final Logger LOG = Logger.getLogger(ChatCompat.class.getName());

    /** Spigot API 可用性缓存（探测一次）。 */
    private static volatile Boolean spigotAvailable;
    /** 缓存的 spigot() 方法句柄。 */
    private static volatile Method spigotMethod;

    private ChatCompat() {
    }

    /**
     * 向玩家发送可点击链接。
     *
     * @param player  目标玩家
     * @param url     点击后打开的 URL
     * @param display 显示文字（可用 {@code §} 颜色码）
     * @return 是否以「可点击组件」方式发出；{@code false} 表示已降级为纯文本消息
     */
    public static boolean sendClickable(Player player, String url, String display) {
        if (player == null) {
            return false;
        }
        if (spigotUsable()) {
            try {
                Object spigot = spigotMethod.invoke(player);
                Object component = buildClickableComponent(url, display);
                if (component != null) {
                    sendComponent(spigot, component);
                    return true;
                }
            } catch (Throwable t) {
                LOG.log(Level.FINE, "[adapter] Spigot 可点击组件发送失败，降级纯文本", t);
            }
        }
        // 降级：纯文本消息（显示文字 + 链接）
        player.sendMessage(display + " §7" + url);
        return false;
    }

    /**
     * 运行时是否存在可用的 Spigot 聊天 API（探测一次并缓存）。
     */
    public static boolean spigotUsable() {
        Boolean cached = spigotAvailable;
        if (cached != null) {
            return cached;
        }
        boolean ok = false;
        try {
            Method m = Player.class.getMethod("spigot");
            m.setAccessible(true);
            // 确认 bungee chat 组件类在 classpath 上（CraftBukkit 无）
            Class.forName("net.md_5.bungee.api.chat.TextComponent");
            Class.forName("net.md_5.bungee.api.chat.ClickEvent");
            spigotMethod = m;
            ok = true;
        } catch (Throwable t) {
            ok = false;
        }
        spigotAvailable = ok;
        return ok;
    }

    /**
     * 反射构造 TextComponent（含 OPEN_URL 点击事件）。
     *
     * @return 组件对象；不可用时返回 null
     */
    private static Object buildClickableComponent(String url, String display) {
        try {
            ClassLoader cl = ChatCompat.class.getClassLoader();
            Class<?> textComponent = Class.forName("net.md_5.bungee.api.chat.TextComponent", true, cl);
            Class<?> clickEvent = Class.forName("net.md_5.bungee.api.chat.ClickEvent", true, cl);
            Class<?> action = Class.forName("net.md_5.bungee.api.chat.ClickEvent$Action", true, cl);

            Constructor<?> tcCtor = textComponent.getConstructor(String.class);
            Object component = tcCtor.newInstance(display == null ? url : display);

            Object openUrl = Enum.valueOf((Class<? extends Enum>) action, "OPEN_URL");
            Constructor<?> ceCtor = clickEvent.getConstructor(action, String.class);
            Object event = ceCtor.newInstance(openUrl, url);

            Method setClick = textComponent.getMethod("setClickEvent", clickEvent);
            setClick.invoke(component, event);
            return component;
        } catch (Throwable t) {
            LOG.log(Level.FINE, "[adapter] 构造可点击组件失败", t);
            return null;
        }
    }

    /**
     * 反射调用 {@code spigot.sendMessage(BaseComponent...)}。
     */
    private static void sendComponent(Object spigot, Object component) throws Exception {
        // Player.Spigot.sendMessage(BaseComponent...) 签名（varargs）
        Class<?> spigotClass = spigot.getClass();
        Class<?> baseComponent = Class.forName("net.md_5.bungee.api.chat.BaseComponent");
        Method send = null;
        for (Method m : spigotClass.getMethods()) {
            if ("sendMessage".equals(m.getName()) && m.getParameterCount() == 1
                    && m.getParameterTypes()[0].isArray()
                    && baseComponent.isAssignableFrom(m.getParameterTypes()[0].getComponentType())) {
                send = m;
                break;
            }
        }
        if (send == null) {
            // 兜底：BaseComponent... 以数组参数匹配
            for (Method m : spigotClass.getMethods()) {
                if ("sendMessage".equals(m.getName()) && m.getParameterCount() == 1) {
                    send = m;
                    break;
                }
            }
        }
        if (send == null) {
            throw new NoSuchMethodException("Player.Spigot#sendMessage not found");
        }
        Object[] args = {component};
        send.invoke(spigot, args);
    }
}
