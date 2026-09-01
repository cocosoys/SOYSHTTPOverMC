package com.github.cocosoys.mc.soyshttpovermc.command;

import com.github.cocosoys.mc.soyshttpovermc.HttpOverMcPlugin;
import com.github.cocosoys.mc.soyshttpovermc.i18n.I18n;
import com.github.cocosoys.mc.soyshttpovermc.util.LinkMessageUtil;
import com.github.cocosoys.mc.soyshttpovermc.util.PlayerTargetUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * /soyshttp send &lt;url|/page&gt; [显示文字] [玩家] —— 向玩家发送可点击链接消息。
 * <ul>
 *   <li>第一个参数 <b>url</b>：完整 URL，或以 {@code /} 开头的本服页面路径（拼 https://host:port）；</li>
 *   <li>[显示文字]：支持 {@code %url%} 与 {@code %url_标签%}（标签到下一个 % 结束；旧写法
 *       {@code %url_[标签]%} 仍兼容）占位符，{@code &} 代替 {@code §} 颜色码；</li>
 *   <li>[玩家]：玩家名，或原生选择器 @a/@p/@r/@e/@s（1.12.2 忽略 [条件]）；省略则发给自己（命令者须为玩家）。</li>
 * </ul>
 */
public class SendSubCommand extends SubCommand {

    public SendSubCommand(HttpOverMcPlugin plugin) {
        super(plugin);
    }

    @Override
    public String name() {
        return "send";
    }

    @Override
    public String usage() {
        return I18n.t("command.send.usage", "/soyshttp send <url|/page> [显示文字] [玩家] —— 发送可点击链接");
    }

    @Override
    public String detail() {
        return I18n.t("command.send.detail",
                "/soyshttp send <url|/page> [显示文字] [玩家] —— 向玩家发送可点击链接消息。\n"
                        + "  <url|/page>   完整 URL；或以 / 开头的本服页面路径（自动拼 https://<对外地址>/...）。\n"
                        + "  [显示文字]     支持 %url%（整条链接）与 %url_标签%（仅标签部分着色）；& 代替 § 颜色码。\n"
                        + "  [玩家]         玩家名，或选择器 @a/@p/@r/@e/@s；省略则发给自己（命令者须为玩家）。\n"
                        + "示例：\n"
                        + "  /soyshttp send /status\n"
                        + "  /soyshttp send /status \"状态面板：%url%\"\n"
                        + "  /soyshttp send https://example.com \"点&b这里&f进去\" @a");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        // 末位参数补全：在线玩家名 + 原生选择器（@a/@p/@r/@e/@s）
        List<String> out = new ArrayList<>();
        out.add("@a");
        out.add("@p");
        out.add("@r");
        out.add("@e");
        out.add("@s");
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            out.add(p.getName());
        }
        return out;
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            msgT(sender, "command.send.usage-short", "用法: /soyshttp send <url或/page路径> [显示文字] [玩家]");
            return;
        }
        String raw = args[1];
        String host = plugin.getDelegate().getMcHost();
        int port = plugin.getDelegate().getMcPort();
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
                msgT(sender, "command.send.target-not-found",
                        "未找到在线玩家: {0}（玩家需在线；或用 @a/@p/@r/@e/@s 选择器）", target);
            } else {
                msgT(sender, "command.send.target-none",
                        "未指定目标玩家，且命令执行者不是玩家（请补玩家名或 @a/@p/@r/@e/@s）");
            }
            return;
        }
        for (Player p : players) {
            LinkMessageUtil.send(p, url, display);
        }
        msgT(sender, "command.send.sent", "已向 {0} 名玩家发送链接: {1}", players.size(), url);
    }
}
