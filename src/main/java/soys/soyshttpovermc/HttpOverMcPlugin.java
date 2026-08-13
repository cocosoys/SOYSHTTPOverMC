package soys.soyshttpovermc;

import org.bukkit.plugin.java.JavaPlugin;
import soys.soyshttpovermc.bot.InternalBot;
import soys.soyshttpovermc.http.HttpMcTranslator;
import soys.soyshttpovermc.link.McLink;
import soys.soyshttpovermc.mc.McMessageHandler;
import soys.soyshttpovermc.mc.SocketSniffer;

import java.util.logging.Logger;

public class HttpOverMcPlugin extends JavaPlugin {

    private InternalBot bot;
    private McLink mcLink;
    private SocketSniffer sniffer;
    private String channel;
    private String botUsername;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        botUsername = getConfig().getString("bot.username", "__http_proxy__");
        channel = getConfig().getString("channel", "httpproxy:main");
        // Bot 回连的本服地址 = Spigot 的 server-port（同端口方案的核心：访问端口 == 服务器端口）
        String mcHost = getConfig().getString("mc.host", "127.0.0.1");
        int mcPort = getConfig().getInt("mc.port", 25564);
        boolean snifferEnabled = getConfig().getBoolean("sniffer.enabled", true);
        int maxBody = getConfig().getInt("sniffer.max-body-bytes", 8 * 1024 * 1024);
        Logger log = getLogger();

        // 1) Bot 回环连接本服（目标即 Spigot 监听端口，例如 25564）。connect() 异步，不阻塞。
        bot = new InternalBot(this, botUsername, channel, mcHost, mcPort);
        mcLink = new McLink(bot, channel);
        bot.setRawMessageListener((ch, data) -> mcLink.onRawMessage(ch, data));
        bot.connect();

        // 2) 服务端插件消息监听（仅处理来自虚拟 Bot 的通道消息，做可读回显证明隧道打通）
        McMessageHandler handler = new McMessageHandler(this, botUsername, channel);
        getServer().getMessenger().registerOutgoingPluginChannel(this, channel);
        getServer().getMessenger().registerIncomingPluginChannel(this, channel, handler);

        // 3) 在 Spigot 自身监听端口上安装 HTTP 嗅探器（Geyser 式深度挂接，访问端口 == server-port）
        if (snifferEnabled) {
            sniffer = new SocketSniffer(this, new HttpMcTranslator(mcLink),
                    () -> bot.isReady(), maxBody);
            sniffer.install();
        }

        log.info("HTTP-Over-MC 已启动（同端口嗅探）: mc=" + mcHost + ":" + mcPort
                + " 通道=" + channel + " 嗅探器=" + (snifferEnabled ? "开" : "关")
                + " | 访问端口 == 服务器端口（curl 与玩家进游戏共用 " + mcPort + "）");
    }

    @Override
    public void onDisable() {
        if (sniffer != null) {
            sniffer.uninstall();
        }
        if (bot != null) {
            bot.disconnect();
        }
        getLogger().info("HTTP-Over-MC 已关闭");
    }
}
