package soys.soyshttpovermc;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import soys.soyshttpovermc.bot.InternalBot;
import soys.soyshttpovermc.gateway.CredentialIssuer;
import soys.soyshttpovermc.gateway.GatewayConfig;
import soys.soyshttpovermc.gateway.GatewayFilter;
import soys.soyshttpovermc.gateway.IssuedCredential;
import soys.soyshttpovermc.gateway.TlsContextFactory;
import soys.soyshttpovermc.http.HttpMcTranslator;
import soys.soyshttpovermc.link.McLink;
import soys.soyshttpovermc.mc.McMessageHandler;
import soys.soyshttpovermc.mc.SocketSniffer;
import soys.soyshttpovermc.web.RequestStats;
import soys.soyshttpovermc.web.WebFrontendHandler;

import javax.net.ssl.SSLEngine;
import java.io.File;
import java.util.function.Supplier;
import java.util.logging.Logger;

public class HttpOverMcPlugin extends JavaPlugin {

    private InternalBot bot;
    private McLink mcLink;
    private SocketSniffer sniffer;
    private GatewayFilter gateway;
    private TlsContextFactory tlsFactory;
    private String channel;
    private String botUsername;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // 首次运行生成 gateway/ 目录下的默认配置（config.yml / https.yml / policies/*.yml）
        File gatewayDir = ensureGatewayFiles();
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

        // 0) 安全网关（独立配置目录 gateway/）+ TLS 上下文（25564 就地升级，无独立端口）
        rebuildGateway(gatewayDir, log);
        final Supplier<SSLEngine> tlsEngines = tlsFactory == null ? null : tlsFactory::newServerEngine;

        // 1) Bot 回环连接本服（目标即 Spigot 监听端口，例如 25564）。connect() 异步，不阻塞。
        bot = new InternalBot(this, botUsername, channel, mcHost, mcPort);
        mcLink = new McLink(bot, channel);
        bot.setRawMessageListener((ch, data) -> mcLink.onRawMessage(ch, data));
        bot.connect();

        // 2) 前端 + 统计：把隧道请求路由为静态资源 / 动态接口
        RequestStats stats = new RequestStats();
        WebFrontendHandler web = new WebFrontendHandler(stats,
                webRoot == null ? null : webRoot.getAbsolutePath(), mcPort, botUsername);
        McMessageHandler handler = new McMessageHandler(this, botUsername, channel, web);
        getServer().getMessenger().registerOutgoingPluginChannel(this, channel);
        getServer().getMessenger().registerIncomingPluginChannel(this, channel, handler);

        // 3) 在 Spigot 自身监听端口上安装嗅探器（三协议：MC / 明文 HTTP / HTTPS）
        if (snifferEnabled) {
            sniffer = new SocketSniffer(this, new HttpMcTranslator(mcLink),
                    () -> bot.isReady(), maxBody, stats, gateway, tlsEngines);
            sniffer.install();
        }

        log.info("HTTP-Over-MC 已启动（同端口嗅探 + 前端服务 + 安全网关）: mc=" + mcHost + ":" + mcPort
                + " 通道=" + channel + " 嗅探器=" + (snifferEnabled ? "开" : "关")
                + " 网关=" + (gateway == null ? "关" : "开")
                + " HTTPS=" + (tlsEngines == null ? "关" : "开")
                + " webroot=" + (webRoot == null ? "(jar 内置)" : webRoot.getAbsolutePath())
                + " | 25564 三协议端口：MC / 明文 HTTP / HTTPS");
    }

    /** 首次运行生成 gateway/ 目录默认配置；返回 gateway 目录 */
    private File ensureGatewayFiles() {
        File gwDir = new File(getDataFolder(), "gateway");
        saveDefaultFile("gateway/config.yml");
        saveDefaultFile("gateway/https.yml");
        saveDefaultFile("gateway/policies/tls.yml");
        saveDefaultFile("gateway/policies/ip-allowlist.yml");
        saveDefaultFile("gateway/policies/auth.yml");
        saveDefaultFile("gateway/policies/rate-limit.yml");
        saveDefaultFile("gateway/issuers/session-token.yml");
        return gwDir;
    }

    /** 从 jar 资源复制默认配置到数据目录（已存在则不覆盖） */
    private void saveDefaultFile(String path) {
        if (getResource(path) == null) return;
        File target = new File(getDataFolder(), path);
        if (!target.isFile()) {
            saveResource(path, false);
        }
    }

    /**
     * 从 gateway/ 目录重建网关（策略链 + TLS）。
     * 布局：gateway/config.yml（总开关）、gateway/https.yml（HTTPS 设置）、
     * gateway/policies/&lt;name&gt;.yml（每个策略一个文件）。
     */
    private void rebuildGateway(File gatewayDir, Logger log) {
        gateway = null;
        tlsFactory = null;
        ConfigurationSection gwCfg = GatewayConfig.loadYml(new File(gatewayDir, "config.yml"));
        boolean gatewayEnabled = gwCfg != null && gwCfg.getBoolean("enabled", true);
        if (gatewayEnabled) {
            gateway = new GatewayFilter(log);
            gateway.reload(gatewayDir);
            ConfigurationSection https = GatewayConfig.loadYml(new File(gatewayDir, "https.yml"));
            if (https != null && https.getBoolean("enabled", true)) {
                try {
                    tlsFactory = new TlsContextFactory(log, getDataFolder(), https);
                    tlsFactory.init();
                } catch (Exception e) {
                    log.warning("[HTTP-Over-MC] TLS 初始化失败，HTTPS 功能禁用: " + e.getMessage());
                    tlsFactory = null;
                }
            }
        }
        final Supplier<SSLEngine> tlsEngines = tlsFactory == null ? null : tlsFactory::newServerEngine;
        if (sniffer != null) {
            sniffer.setGateway(gateway);
            sniffer.setTlsEngineSupplier(tlsEngines);
        }
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

    /** /soyshttp reload：热重载网关策略与 TLS 配置（gateway/ 目录），无需重启服务器 */
    private boolean handleReload(CommandSender sender) {
        reloadConfig();
        Logger log = getLogger();
        File gatewayDir = ensureGatewayFiles();
        rebuildGateway(gatewayDir, log);
        sender.sendMessage("[SOYSHTTPOverMC] 网关策略已热重载："
                + (gateway == null ? "网关关闭" : gateway.getPolicies().size() + " 个策略启用")
                + "，HTTPS=" + (tlsFactory == null ? "关" : "开"));
        return true;
    }

    /** /soyshttp key <subject>：调用启用的凭证颁发器为指定主体下发凭证（X-API-Key/Bearer/Cookie 三种形态） */
    private boolean handleIssueKey(CommandSender sender, String subject) {
        if (gateway == null) {
            sender.sendMessage("[SOYSHTTPOverMC] 网关未启用，无法下发凭证");
            return true;
        }
        int n = 0;
        for (CredentialIssuer issuer : gateway.getIssuers()) {
            if (!issuer.isEnabled()) continue;
            IssuedCredential c = issuer.issue(subject);
            n++;
            StringBuilder sb = new StringBuilder();
            sb.append("[SOYSHTTPOverMC] 已为 ").append(subject).append(" 下发凭证（").append(issuer.name()).append("）:");
            if (c.getApiKey() != null) sb.append("\n  X-API-Key: ").append(c.getApiKey());
            if (c.getBearer() != null) sb.append("\n  Authorization: Bearer ").append(c.getBearer());
            if (c.getCookieName() != null) sb.append("\n  Cookie: ").append(c.getCookieName()).append('=').append(c.getCookieValue());
            sb.append("\n  curl -sk https://127.0.0.1:25564/api/status -H \"X-API-Key: ").append(c.getApiKey()).append('"');
            sender.sendMessage(sb.toString());
        }
        if (n == 0) {
            sender.sendMessage("[SOYSHTTPOverMC] 未启用任何凭证颁发器（请在 gateway/issuers/ 下将对应 yml 的 enabled 设为 true）");
        }
        return true;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("soyshttp")) {
            return false;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            return handleReload(sender);
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("key")) {
            return handleIssueKey(sender, args[1]);
        }
        sender.sendMessage("用法: /soyshttp reload | /soyshttp key <subject>");
        return true;
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
