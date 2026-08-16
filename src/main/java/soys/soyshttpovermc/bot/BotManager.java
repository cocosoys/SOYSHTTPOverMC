package soys.soyshttpovermc.bot;

import soys.soyshttpovermc.exception.BotException;
import soys.soyshttpovermc.link.McLink;
import soys.soyshttpovermc.log.LogKit;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bot 生命周期与通道调度管理器（门面 Bot 组的后端）：
 * <ul>
 *   <li>托管<b>主 Bot</b>（插件自带的无头回环 Bot）及其 McLink 隧道；主通道消息交给主 McLink，
 *       其余自定义通道消息分发给 {@link #registerChannel} 登记的监听器；</li>
 *   <li>支持 {@link #addBot} 创建<b>额外</b>受管无头 Bot（独立通道/隧道），{@link #kickBot} 踢出；</li>
 *   <li>支持在主 Bot 上 {@link #registerChannel} 注册更多 PluginMessage 通道并监听其下行消息。</li>
 * </ul>
 * 主 Bot 的 RawMessageListener 由本管理器接管（构造后由 HttpOverMcPlugin 设置
 * {@code bot.setRawMessageListener(botManager::dispatch)}），从而在不破坏核心隧道的前提下扩展通道。
 */
public class BotManager {

    private final JavaPlugin plugin;
    private final InternalBot mainBot;
    private final McLink mainLink;
    private final String mainChannel;
    private final String host;
    private final int port;
    /** 群组服转发兼容：额外受管 Bot 创建时透传，使其握手附加 !<uuid> 以通过 bungee:true / Velocity 转发校验 */
    private final boolean proxyForwarding;

    /** 自定义通道监听器：channel -> 监听器（主 Bot 收到该通道下行消息时回调） */
    private final Map<String, InternalBot.RawMessageListener> customListeners = new ConcurrentHashMap<>();
    /** 额外受管 Bot：bot 名 -> 托管项 */
    private final Map<String, ManagedBot> bots = new ConcurrentHashMap<>();

    public BotManager(JavaPlugin plugin, InternalBot mainBot, McLink mainLink,
                      String mainChannel, String host, int port, boolean proxyForwarding) {
        this.plugin = plugin;
        this.mainBot = mainBot;
        this.mainLink = mainLink;
        this.mainChannel = mainChannel;
        this.host = host;
        this.port = port;
        this.proxyForwarding = proxyForwarding;
    }

    /**
     * 主 Bot 收到任意通道下行消息时的统一入口（HttpOverMcPlugin 设为 bot 的 RawMessageListener）。
     * 主通道 → 主 McLink（完成隧道多路复用）；其余 → 对应自定义监听器。
     */
    public void dispatch(String ch, byte[] data) {
        if (mainChannel.equals(ch)) {
            mainLink.onRawMessage(ch, data);
            return;
        }
        InternalBot.RawMessageListener l = customListeners.get(ch);
        if (l != null) {
            l.onRawMessage(ch, data);
        }
    }

    /** 在主 Bot 上注册一个自定义通道并监听其下行消息（服务端侧需对应 PluginMessageListener 才能回发）。 */
    public void registerChannel(String channel, InternalBot.RawMessageListener listener) {
        if (channel == null || channel.isEmpty() || listener == null) return;
        customListeners.put(channel, listener);
        mainBot.registerExtraChannel(channel);
        LogKit.info("[HTTP-Over-MC] 已登记自定义通道监听: " + channel);
    }

    /** 注销自定义通道监听（并向服务端发送 unregister）。 */
    public void unregisterChannel(String channel) {
        if (channel == null || channel.isEmpty()) return;
        customListeners.remove(channel);
        mainBot.unregisterExtraChannel(channel);
        LogKit.info("[HTTP-Over-MC] 已注销自定义通道监听: " + channel);
    }

    /** 创建一个额外受管无头 Bot（独立通道 + 独立 McLink 隧道）并回连本服。若同名已存在则返回既有项。 */
    public ManagedBot addBot(String name, String channel) {
        if (name == null || name.isEmpty() || channel == null || channel.isEmpty()) return null;
        ManagedBot existing = bots.get(name);
        if (existing != null) return existing;
        InternalBot bot = new InternalBot(plugin, name, channel, host, port);
        bot.setProxyForwarding(proxyForwarding);
        McLink link = new McLink(bot, channel);
        bot.setRawMessageListener((ch, data) -> {
            if (channel.equals(ch)) {
                link.onRawMessage(ch, data);
            } else {
                InternalBot.RawMessageListener l = customListeners.get(ch);
                if (l != null) l.onRawMessage(ch, data);
            }
        });
        bot.connect();
        ManagedBot mb = new ManagedBot(name, channel, bot, link);
        bots.put(name, mb);
        LogKit.info("[HTTP-Over-MC] 已添加受管 Bot: name=" + name + " channel=" + channel);
        return mb;
    }

    /** 踢出并断开一个额外受管 Bot（主 Bot 不可踢）。 */
    public void kickBot(String name) throws BotException {
        if(name.equals(plugin.getConfig().getString("bot.username"))){
            throw new BotException("禁止踢出主bot "+plugin.getConfig().getString("bot.username"));
        }
        ManagedBot mb = bots.remove(name);
        if (mb != null) {
            mb.getBot().disconnect();
            LogKit.info("[HTTP-Over-MC] 已踢出受管 Bot: " + name);
        }
    }

    /** 获取额外受管 Bot（不存在返回 null）。 */
    public ManagedBot getBot(String name) {
        return bots.get(name);
    }

    /** 断开并清空全部额外受管 Bot（插件 onDisable 时调用；主 Bot 由 HttpOverMcPlugin 单独处理）。 */
    public void disconnectAll() {
        for (ManagedBot mb : new java.util.ArrayList<>(bots.values())) {
            try { mb.getBot().disconnect(); } catch (Throwable ignored) {}
        }
        bots.clear();
    }

    /** 触发主 Bot 重新连接（被踢出游戏等特殊情况后恢复隧道；主通道与 McLink 引用保持不变）。 */
    public void reconnectMainBot() {
        mainBot.reconnect();
        LogKit.info("[HTTP-Over-MC] 主 Bot 重新连接已触发: " + mainBot.getUsername());
    }

    /** 该玩家名是否受本管理器托管的 Bot（主 Bot 或额外受管 Bot）——供登录插件集成器判断免登录对象。 */
    public boolean isManagedBot(String playerName) {
        if (playerName == null) return false;
        String main = mainBot == null ? null : mainBot.getUsername();
        if (playerName.equalsIgnoreCase(main)) return true;
        return bots.containsKey(playerName);
    }

    /** 全部受管 Bot 名（主 Bot + 额外 Bot）。 */
    public java.util.Set<String> getBotNames() {
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        if (mainBot != null && mainBot.getUsername() != null) names.add(mainBot.getUsername());
        names.addAll(bots.keySet());
        return names;
    }

    /** 受管 Bot 的不可变持有项（名称 / 通道 / Bot / McLink）。 */
    public static final class ManagedBot {
        private final String name;
        private final String channel;
        private final InternalBot bot;
        private final McLink link;

        ManagedBot(String name, String channel, InternalBot bot, McLink link) {
            this.name = name;
            this.channel = channel;
            this.bot = bot;
            this.link = link;
        }

        public String getName() { return name; }
        public String getChannel() { return channel; }
        public InternalBot getBot() { return bot; }
        public McLink getLink() { return link; }
    }
}
