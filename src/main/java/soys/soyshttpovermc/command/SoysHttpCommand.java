package soys.soyshttpovermc.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import soys.soyshttpovermc.HttpOverMcPlugin;
import soys.soyshttpovermc.api.event.GatewayCredentialIssuedEvent;
import soys.soyshttpovermc.gateway.GatewayFilter;
import soys.soyshttpovermc.gateway.policy.auth.issuer.CredentialIssuer;
import soys.soyshttpovermc.gateway.policy.auth.issuer.IssuedCredential;
import soys.soyshttpovermc.log.LogKit;

/**
 * /soyshttp 命令执行器（从 {@code HttpOverMcPlugin} 抽离）：
 * <ul>
 *   <li>{@code /soyshttp reload} —— 热重载日志级别 + 网关策略与 TLS 配置（无需重启服务器）；</li>
 *   <li>{@code /soyshttp key <subject>} —— 调用启用的凭证颁发器为指定主体下发凭证
 *       （X-API-Key / Bearer / Cookie 三种形态）。</li>
 * </ul>
 */
public class SoysHttpCommand implements CommandExecutor {

    private final HttpOverMcPlugin plugin;

    public SoysHttpCommand(HttpOverMcPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("soyshttp")) {
            return false;
        }
        if(!sender.isOp()){
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
}
