package soys.soyshttpovermc.command;

import org.bukkit.command.CommandSender;

import soys.soyshttpovermc.HttpOverMcPlugin;
import soys.soyshttpovermc.api.event.GatewayCredentialIssuedEvent;
import soys.soyshttpovermc.gateway.GatewayFilter;
import soys.soyshttpovermc.gateway.policy.auth.issuer.CredentialIssuer;
import soys.soyshttpovermc.gateway.policy.auth.issuer.IssuedCredential;
import soys.soyshttpovermc.gateway.policy.auth.issuer.SessionTokenIssuer;

/**
 * /soyshttp key &lt;subject&gt; —— <b>服主手动颁发的最高权限 key</b>：
 * 对 session-token 颁发器签发带 adm 标记的 ak_ 令牌（权限层直接放行，免权限访问全部 API，供外部服务接入）；
 * 其它颁发器按普通凭证签发（不具最高权限）。仅 op 可执行（SubCommand 默认 requireOp=true）。
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
        return "/soyshttp key <subject> —— 服主手动颁发最高权限 key（免权限访问全部 API，请谨慎）";
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
            // 会话令牌颁发器 → 签发服主最高权限 key（adm 标记，权限层直接放行）；
            // 其它颁发器按普通凭证签发（不具最高权限）
            IssuedCredential c = (issuer instanceof SessionTokenIssuer)
                    ? ((SessionTokenIssuer) issuer).issueAdminKey(subject)
                    : issuer.issue(subject);
            n++;
            StringBuilder sb = new StringBuilder();
            sb.append("已为 ").append(subject).append(" 下发凭证（").append(issuer.name()).append("）:");
            if (c.getApiKey() != null) sb.append("\n  X-API-Key: ").append(c.getApiKey());
            if (c.getBearer() != null) sb.append("\n  Authorization: Bearer ").append(c.getBearer());
            if (c.getCookieName() != null) sb.append("\n  Cookie: ").append(c.getCookieName()).append('=').append(c.getCookieValue());
            int port = plugin.getMcPort();
            sb.append("\n  curl -sk https://127.0.0.1:").append(port).append("/api/status -H \"X-API-Key: ").append(c.getApiKey()).append('"');
            if (issuer instanceof SessionTokenIssuer) {
                sb.append("\n  ⚠ 该 key 为最高权限（adm），可免权限访问全部 API，请仅用于可信外部服务");
            }
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
