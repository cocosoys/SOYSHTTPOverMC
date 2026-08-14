package soys.soyshttpovermc.command;

import org.bukkit.command.CommandSender;

import soys.soyshttpovermc.HttpOverMcPlugin;
import soys.soyshttpovermc.gateway.GatewayFilter;
import soys.soyshttpovermc.gateway.policy.auth.issuer.CredentialIssuer;
import soys.soyshttpovermc.gateway.policy.auth.issuer.SessionTokenIssuer;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * /soyshttp tokens —— 查询所有已颁发的会话令牌（签发审计：主体/模式/是否 admin/状态/签发/过期）。
 * 令牌本体不回显（避免泄露有效凭据）；注销后记录标记为"已注销"。仅 op 可执行。
 */
public class TokensSubCommand extends SubCommand {

    private static final SimpleDateFormat FMT = new SimpleDateFormat("MM-dd HH:mm");

    public TokensSubCommand(HttpOverMcPlugin plugin) {
        super(plugin);
    }

    @Override
    public String name() {
        return "tokens";
    }

    @Override
    public String usage() {
        return "/soyshttp tokens —— 查询所有已颁发的令牌";
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        GatewayFilter gateway = plugin.getGateway();
        if (gateway == null) {
            msg(sender, "网关未启用，无法查询令牌");
            return;
        }
        SessionTokenIssuer issuer = null;
        for (CredentialIssuer i : gateway.getIssuers()) {
            if (i instanceof SessionTokenIssuer) {
                issuer = (SessionTokenIssuer) i;
                break;
            }
        }
        if (issuer == null) {
            msg(sender, "未启用 session-token 颁发器（请在 gateway/issuers/session-token.yml 设 enabled: true）");
            return;
        }
        List<SessionTokenIssuer.IssuedRecord> all = issuer.listIssued();
        long active = all.stream().filter(r -> "有效".equals(r.status())).count();
        msg(sender, "§a已颁发令牌（共 " + all.size() + " 个，其中有效 " + active + " 个）:");
        if (all.isEmpty()) {
            sender.sendMessage("  §7（暂无签发记录）");
            return;
        }
        for (SessionTokenIssuer.IssuedRecord r : all) {
            String statusColor = "已注销".equals(r.status()) ? "§7" : "有效".equals(r.status()) ? "§a" : "§e";
            sender.sendMessage("  §f" + r.subject
                    + "  §7mode=§f" + r.mode
                    + "  §7admin=§f" + (r.admin ? "是" : "否")
                    + "  " + statusColor + r.status()
                    + "  §7签发=§f" + FMT.format(new Date(r.issuedAt))
                    + "  §7过期=§f" + FMT.format(new Date(r.expiresAt)));
        }
    }
}
