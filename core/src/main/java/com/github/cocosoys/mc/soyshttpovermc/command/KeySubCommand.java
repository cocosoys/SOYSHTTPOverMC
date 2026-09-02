package com.github.cocosoys.mc.soyshttpovermc.command;

import com.github.cocosoys.mc.soyshttpovermc.HttpOverMcPlugin;
import com.github.cocosoys.mc.soyshttpovermc.api.event.GatewayCredentialIssuedEvent;
import com.github.cocosoys.mc.soyshttpovermc.i18n.I18n;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.GatewayFilter;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.issuer.CredentialIssuer;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.issuer.IssuedCredential;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.issuer.SessionTokenIssuer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

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
        return I18n.t("command.key.usage",
                "/soyshttp key <subject> —— 服主手动颁发最高权限 key（免权限访问全部 API，请谨慎）");
    }

    @Override
    public String detail() {
        return I18n.t("command.key.detail",
                "/soyshttp key <subject> —— 为指定主体下发最高权限凭证（admin key，ak_ 前缀）。\n"
                        + "  <subject>   主体标识（通常为玩家名或外部服务名）。\n"
                        + "会话令牌颁发器启用时，签发的 ak_ key 带 adm 标记，权限层直接放行，可免权限访问全部 API\n"
                        + "（仅用于可信外部服务，请勿外泄）；其它颁发器按普通凭证签发（不具最高权限）。\n"
                        + "Tab 补全在线玩家名；仅 op 可执行。");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        // 末位参数补全在线玩家名（subject 通常是玩家名）
        List<String> out = new ArrayList<>();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            out.add(p.getName());
        }
        return out;
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            msgT(sender, "command.key.usage-short", "用法: /soyshttp key <subject>");
            return;
        }
        String subject = args[1];
        GatewayFilter gateway = plugin.getGateway();
        if (gateway == null) {
            msgT(sender, "command.key.gateway-off", "网关未启用，无法下发凭证");
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
            sb.append(I18n.t("command.key.issued", "已为 {0} 下发凭证（{1}）:", subject, issuer.name()));
            if (c.getApiKey() != null) sb.append("\n  X-API-Key: ").append(c.getApiKey());
            if (c.getBearer() != null) sb.append("\n  Authorization: Bearer ").append(c.getBearer());
            if (c.getCookieName() != null)
                sb.append("\n  Cookie: ").append(c.getCookieName()).append('=').append(c.getCookieValue());
            int port = plugin.getDelegate().getMcPort();
            sb.append("\n  curl -sk https://").append(plugin.getDelegate().getMcHost()).append(":").append(port).append("/api/status -H \"X-API-Key: ").append(c.getApiKey()).append('"');
            if (issuer instanceof SessionTokenIssuer) {
                sb.append(I18n.t("command.key.admin-warn",
                        "\n  ⚠ 该 key 为最高权限（adm），可免权限访问全部 API，请仅用于可信外部服务"));
            }
            msg(sender, sb.toString());
            // 触发凭证下发事件（供其他插件联动；同步事件，命令路径在主线程）
            try {
                plugin.getServer().getPluginManager().callEvent(new GatewayCredentialIssuedEvent(subject, issuer, c));
            } catch (Throwable ignored) {
            }
        }
        if (n == 0) {
            msgT(sender, "command.key.no-issuer",
                    "未启用任何凭证颁发器（请在 gateway/issuers/ 下将对应 yml 的 enabled 设为 true）");
        }
    }
}
