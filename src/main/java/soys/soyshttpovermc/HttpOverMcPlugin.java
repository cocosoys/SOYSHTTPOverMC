package soys.soyshttpovermc;

import org.bukkit.plugin.java.JavaPlugin;
import soys.soyshttpovermc.bot.InternalBot;
import soys.soyshttpovermc.http.HttpMcTranslator;
import soys.soyshttpovermc.link.McLink;
import soys.soyshttpovermc.mc.McMessageHandler;
import soys.soyshttpovermc.mc.SocketSniffer;
import soys.soyshttpovermc.web.RequestStats;
import soys.soyshttpovermc.web.WebFrontendHandler;

import java.io.File;
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
        // 前端资源目录：留空则用 jar 内置默认面板；非空则优先从磁盘读取（支持热替换）
        String webRootRaw = getConfig().getString("web.root", "");
        File webRoot = resolveWebRoot(webRootRaw);
        Logger log = getLogger();

        // 1) Bot 回环连接本服（目标即 Spigot 监听端口，例如 25564）。connect() 异步，不阻塞。
        bot = new InternalBot(this, botUsername, channel, mcHost, mcPort);
        mcLink = new McLink(bot, channel);
        bot.setRawMessageListener((ch, data) -> mcLink.onRawMessage(ch, data));
        bot.connect();

        // 2) 前端 + 统计：把隧道请求路由为静态资源 / 动态接口（替代第一版可读回显）
        RequestStats stats = new RequestStats();
        WebFrontendHandler web = new WebFrontendHandler(stats,
                webRoot == null ? null : webRoot.getAbsolutePath(), mcPort, botUsername);
        McMessageHandler handler = new McMessageHandler(this, botUsername, channel, web);
        getServer().getMessenger().registerOutgoingPluginChannel(this, channel);
        getServer().getMessenger().registerIncomingPluginChannel(this, channel, handler);

        // 3) 在 Spigot 自身监听端口上安装 HTTP 嗅探器（Geyser 式深度挂接，访问端口 == server-port）
        if (snifferEnabled) {
            sniffer = new SocketSniffer(this, new HttpMcTranslator(mcLink),
                    () -> bot.isReady(), maxBody, stats);
            sniffer.install();
        }

        log.info("HTTP-Over-MC 已启动（同端口嗅探 + 前端服务）: mc=" + mcHost + ":" + mcPort
                + " 通道=" + channel + " 嗅探器=" + (snifferEnabled ? "开" : "关")
                + " webroot=" + (webRoot == null ? "(jar 内置)" : webRoot.getAbsolutePath())
                + " | 访问端口 == 服务器端口（curl 与玩家进游戏共用 " + mcPort + "）");
    }

    /** 解析 web.root：相对 data 目录；为空返回 null（使用 jar 内置资源） */
    private File resolveWebRoot(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        File f = new File(raw);
        if (!f.isAbsolute()) {
            f = new File(getDataFolder(), raw);
        }
        return f.getAbsoluteFile();
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
