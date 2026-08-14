package soys.soyshttpovermc.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import soys.soyshttpovermc.HttpOverMcPlugin;
import soys.soyshttpovermc.api.event.GatewayCredentialIssuedEvent;
import soys.soyshttpovermc.gateway.GatewayFilter;
import soys.soyshttpovermc.gateway.policy.auth.issuer.CredentialIssuer;
import soys.soyshttpovermc.gateway.policy.auth.issuer.IssuedCredential;
import soys.soyshttpovermc.log.LogKit;
import soys.soyshttpovermc.util.LinkMessageUtil;
import soys.soyshttpovermc.util.PlayerTargetUtil;
import soys.soyshttpovermc.web.WebRegistry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * /soyshttp 命令执行器（从 {@code HttpOverMcPlugin} 抽离）：
 * <ul>
 *   <li>{@code /soyshttp reload} —— 热重载日志级别 + 网关策略与 TLS 配置（无需重启服务器）；</li>
 *   <li>{@code /soyshttp key <subject>} —— 调用启用的凭证颁发器为指定主体下发凭证；</li>
 *   <li>{@code /soyshttp reconnect} —— 主 Bot 重新连接（被踢出游戏等特殊情况后恢复隧道）；</li>
 *   <li>{@code /soyshttp send <url|/page> [显示文字] [玩家]} —— 向玩家发送可点击链接消息；</li>
 *   <li>{@code /soyshttp pages} —— 查看当前已注册的全部网页；</li>
 * </ul>
 * 另支持简写 {@code /shttp}（plugin.yml 注册别名命令）。
 */
public class SoysHttpCommand implements CommandExecutor {

    private final HttpOverMcPlugin plugin;

    public SoysHttpCommand(HttpOverMcPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("soyshttp") && !command.getName().equalsIgnoreCase("shttp")) {
            return false;
        }
        if (!sender.isOp()) {
            sender.sendMessage("[SOYSHTTPOverMC] 无权限（需 op）");
            return true;
        }
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "reload":
                return handleReload(sender);
            case "key":
                if (args.length >= 2) return handleIssueKey(sender, args[1]);
                sendUsage(sender);
                return true;
            case "reconnect":
                return handleReconnect(sender);
            case "send":
                return handleSend(sender, args);
            case "pages":
                return handleListPages(sender);
            default:
                sendUsage(sender);
                return true;
        }
    }

    /** /soyshttp reload：热重载日志级别 + 网关策略与 TLS 配置（gateway/ 目录），无需重启服务器 */
    private boolean handleReload(CommandSender sender) {
        plugin.reloadHttpConfig();
        sender.sendMessage("[SOYSHTTPOverMC] 网关策略已热重载："
                + (plugin.getGateway() == null ? "网关关闭" : plugin.getGateway().getPolicies().size() + " 个策略启用")
                + "，HTTPS=" + (plugin.isTlsEnabled() ? "开" : "关")
                + "，事件调试=" + (plugin.isDebugEventsEnabled() ? "开" : "关")
                + "，日志级别=" + LogKit.levelName() + "（配置=" + plugin.getConfig().getString("log.level", "INFO") + "）");
        return true;
    }

    /** /soyshttp key <subject>：调用启用的凭证颁发器为指定主体下发凭证 */
    private boolean handleIssueKey(CommandSender sender, String subject) {
        GatewayFilter gateway = plugin.getGateway();
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
            int port = plugin.getConfig().getInt("mc.port", 25564);
            sb.append("\n  curl -sk https://127.0.0.1:").append(port).append("/api/status -H \"X-API-Key: ").append(c.getApiKey()).append('"');
            sender.sendMessage(sb.toString());
            // 触发凭证下发事件（供其他插件联动；同步事件，命令路径在主线程）
            try {
                plugin.getServer().getPluginManager().callEvent(new GatewayCredentialIssuedEvent(subject, issuer, c));
            } catch (Throwable ignored) {
            }
        }
        if (n == 0) {
            sender.sendMessage("[SOYSHTTPOverMC] 未启用任何凭证颁发器（请在 gateway/issuers/ 下将对应 yml 的 enabled 设为 true）");
        }
        return true;
    }

    /** /soyshttp reconnect：触发主 Bot 重新连接（被踢出游戏等特殊情况后恢复隧道） */
    private boolean handleReconnect(CommandSender sender) {
        if (plugin.getBotManager() == null) {
            sender.sendMessage("[SOYSHTTPOverMC] Bot 未初始化");
            return true;
        }
        plugin.getBotManager().reconnectMainBot();
        sender.sendMessage("[SOYSHTTPOverMC] 主 Bot 重新连接已触发: user="
                + plugin.getConfig().getString("bot.username", "__http_proxy__") + "，请稍候查看日志确认就绪");
        return true;
    }

    /**
     * /soyshttp send <url|/page> [显示文字] [玩家]
     * <ul>
     *   <li>第一个参数 <b>url</b>：完整 URL，或以 {@code /} 开头的本服页面路径（拼 https://host:port）；</li>
     *   <li>[显示文字]：支持 {@code %url%} 与 {@code %url_[标签]%} 占位符，{@code &} 代替 {@code §} 颜色码；</li>
     *   <li>[玩家]：玩家名，或原生选择器 @a/@p/@r/@e/@s（1.12.2 忽略 [条件]）；省略则发给自己（命令者须为玩家）。</li>
     * </ul>
     */
    private boolean handleSend(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("[SOYSHTTPOverMC] 用法: /soyshttp send <url或/page路径> [显示文字] [玩家]");
            return true;
        }
        String raw = args[1];
        String host = plugin.getConfig().getString("mc.host", "127.0.0.1");
        int port = plugin.getConfig().getInt("mc.port", 25564);
        String url = LinkMessageUtil.resolveUrl(raw, host, port);

        // 解析 [显示文字] 与 [玩家]（显示文字可能含空格，按规则把目标参数剥离）
        String display = null;
        String target = null;
        if (args.length >= 3) {
            List<String> rest = new ArrayList<>(Arrays.asList(args).subList(2, args.length));
            String last = rest.get(rest.size() - 1);
            if (isTargetToken(last) && rest.size() >= 2) {
                target = last;
                display = join(rest.subList(0, rest.size() - 1));
            } else if (isTargetToken(rest.get(0)) && rest.size() >= 2) {
                target = rest.get(0);
                display = join(rest.subList(1, rest.size()));
            } else {
                display = join(rest);
            }
        }

        List<Player> players = (target == null)
                ? (sender instanceof Player ? Collections.singletonList((Player) sender) : Collections.emptyList())
                : PlayerTargetUtil.resolve(sender, target);

        if (players.isEmpty()) {
            if (target != null) {
                sender.sendMessage("[SOYSHTTPOverMC] 未找到在线玩家: " + target
                        + "（玩家需在线；或用 @a/@p/@r/@e/@s 选择器）");
            } else {
                sender.sendMessage("[SOYSHTTPOverMC] 未指定目标玩家，且命令执行者不是玩家（请补玩家名或 @a/@p/@r/@e/@s）");
            }
            return true;
        }
        for (Player p : players) {
            LinkMessageUtil.send(p, url, display);
        }
        sender.sendMessage("[SOYSHTTPOverMC] 已向 " + players.size() + " 名玩家发送链接: " + url);
        return true;
    }

    /** /soyshttp pages：查看当前已注册的全部网页 */
    private boolean handleListPages(CommandSender sender) {
        WebRegistry reg = plugin.getWebRegistry();
        if (reg == null) {
            sender.sendMessage("[SOYSHTTPOverMC] 网页登记处未初始化");
            return true;
        }
        List<String> list = reg.listPaths();
        sender.sendMessage("[SOYSHTTPOverMC] 已注册网页(" + list.size() + "):");
        for (String s : list) sender.sendMessage("  " + s);
        return true;
    }

    /** 该 token 是否像“目标玩家”（选择器或在线玩家名）。 */
    private static boolean isTargetToken(String s) {
        if (s == null || s.isEmpty()) return false;
        if (s.charAt(0) == '@') return true;
        return Bukkit.getPlayerExact(s) != null || Bukkit.getPlayer(s) != null;
    }

    private static String join(List<String> list) {
        return String.join(" ", list);
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("[SOYSHTTPOverMC] 用法:");
        sender.sendMessage("  /soyshttp reload —— 热重载配置与网关");
        sender.sendMessage("  /soyshttp key <subject> —— 下发凭证");
        sender.sendMessage("  /soyshttp reconnect —— 主 Bot 重新连接");
        sender.sendMessage("  /soyshttp send <url|/page> [显示文字] [玩家] —— 发送可点击链接");
        sender.sendMessage("  /soyshttp pages —— 查看已注册页面");
        sender.sendMessage("  （shttp 为简写）");
    }
}
