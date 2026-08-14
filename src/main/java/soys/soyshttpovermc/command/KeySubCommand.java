package soys.soyshttpovermc.command;

import org.bukkit.command.CommandSender;

import soys.soyshttpovermc.HttpOverMcPlugin;
import soys.soyshttpovermc.api.event.GatewayCredentialIssuedEvent;
import soys.soyshttpovermc.gateway.GatewayFilter;
import soys.soyshttpovermc.gateway.policy.auth.issuer.CredentialIssuer;
import soys.soyshttpovermc.gateway.policy.auth.issuer.IssuedCredential;

/**
 * /soyshttp key &lt;subject&gt; —— 调用启用的凭证颁发器为指定主体下发凭证。
 */
public class KeySubCommand extends SubCommand {

    public KeySubCommand(HttpOverMcPlugin plugin) {
        super(plugin);
    }

    @Override
    public String name() {
        return "key";
    }

    @Override
    public String usage() {
        return "/soyshttp key <subject> —— 为指定主体下发凭证";
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            msg(sender, "用法: /soyshttp key <subject>");
            return;
        }
        String subject = args[1];
        GatewayFilter gateway = plugin.getGateway();
        if (gateway == null) {
            msg(sender, "网关未启用，无法下发凭证");
            return;
        }
        int n = 0;
        for (CredentialIssuer issuer : gateway.getIssuers()) {
            if (!issuer.isEnabled()) continue;
            IssuedCredential c = issuer.issue(subject);
            n++;
            StringBuilder sb = new StringBuilder();
            sb.append("已为 ").append(subject).append(" 下发凭证（").append(issuer.name()).append("）:");
            if (c.getApiKey() != null) sb.append("\n  X-API-Key: ").append(c.getApiKey());
            if (c.getBearer() != null) sb.append("\n  Authorization: Bearer ").append(c.getBearer());
            if (c.getCookieName() != null) sb.append("\n  Cookie: ").append(c.getCookieName()).append('=').append(c.getCookieValue());
            int port = plugin.getMcPort();
            sb.append("\n  curl -sk https://127.0.0.1:").append(port).append("/api/status -H \"X-API-Key: ").append(c.getApiKey()).append('"');
            msg(sender, sb.toString());
            // 触发凭证下发事件（供其他插件联动；同步事件，命令路径在主线程）
            try {
                plugin.getServer().getPluginManager().callEvent(new GatewayCredentialIssuedEvent(subject, issuer, c));
            } catch (Throwable ignored) {
            }
        }
        if (n == 0) {
            msg(sender, "未启用任何凭证颁发器（请在 gateway/issuers/ 下将对应 yml 的 enabled 设为 true）");
        }
    }
}
