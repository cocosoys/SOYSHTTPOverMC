package com.github.cocosoys.mc.soyshttpovermc.command;

import com.github.cocosoys.mc.soyshttpovermc.HttpOverMcPlugin;
import com.github.cocosoys.mc.soyshttpovermc.i18n.I18n;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.GatewayFilter;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.issuer.CredentialIssuer;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.issuer.SessionTokenIssuer;
import org.bukkit.command.CommandSender;

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
        return I18n.t("command.tokens.usage", "/soyshttp tokens —— 查询所有已颁发的令牌");
    }

    @Override
    public String detail() {
        return I18n.t("command.tokens.detail",
                "/soyshttp tokens —— 查询所有已颁发的会话令牌（签发审计）。\n"
                        + "展示：主体 / 模式 / 是否 admin / 状态（有效 | 已注销）/ 签发时间 / 过期时间。\n"
                        + "令牌本体不回显（避免泄露有效凭据）；注销后记录标记为“已注销”；仅 op 可执行。");
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        GatewayFilter gateway = plugin.getGateway();
        if (gateway == null) {
            msgT(sender, "command.tokens.gateway-off", "网关未启用，无法查询令牌");
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
            msgT(sender, "command.tokens.no-issuer",
                    "未启用 session-token 颁发器（请在 gateway/issuers/session-token.yml 设 enabled: true）");
            return;
        }
        List<SessionTokenIssuer.IssuedRecord> all = issuer.listIssued();
        long active = all.stream().filter(r -> "有效".equals(r.status())).count();
        msgT(sender, "command.tokens.summary",
                "§a已颁发令牌（共 {0} 个，其中有效 {1} 个）:", all.size(), active);
        if (all.isEmpty()) {
            sender.sendMessage(I18n.t("command.tokens.empty", "  §7（暂无签发记录）"));
            return;
        }
        String yes = I18n.t("command.tokens.admin-label", "是");
        String no = I18n.t("command.tokens.not-admin-label", "否");
        for (SessionTokenIssuer.IssuedRecord r : all) {
            String statusColor = "已注销".equals(r.status()) ? "§7" : "有效".equals(r.status()) ? "§a" : "§e";
            sender.sendMessage("  §f" + r.subject
                    + "  §7mode=§f" + r.mode
                    + "  §7admin=§f" + (r.admin ? yes : no)
                    + "  " + statusColor + r.status()
                    + I18n.t("command.tokens.issued-label", "  §7签发=§f{0}", FMT.format(new Date(r.issuedAt)))
                    + I18n.t("command.tokens.expires-label", "  §7过期=§f{0}", FMT.format(new Date(r.expiresAt))));
        }
    }
}
