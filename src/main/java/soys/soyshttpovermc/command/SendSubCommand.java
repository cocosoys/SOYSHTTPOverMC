package soys.soyshttpovermc.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import soys.soyshttpovermc.HttpOverMcPlugin;
import soys.soyshttpovermc.util.LinkMessageUtil;
import soys.soyshttpovermc.util.PlayerTargetUtil;
import soys.soyshttpovermc.web.WebRegistry;

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
        return "/soyshttp send <url|/page> [显示文字] [玩家] —— 发送可点击链接";
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
            msg(sender, "用法: /soyshttp send <url或/page路径> [显示文字] [玩家]");
            return;
        }
        String raw = args[1];
        String host = plugin.getMcHost();
        int port = plugin.getMcPort();
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
                msg(sender, "未找到在线玩家: " + target + "（玩家需在线；或用 @a/@p/@r/@e/@s 选择器）");
            } else {
                msg(sender, "未指定目标玩家，且命令执行者不是玩家（请补玩家名或 @a/@p/@r/@e/@s）");
            }
            return;
        }
        for (Player p : players) {
            LinkMessageUtil.send(p, url, display);
        }
        msg(sender, "已向 " + players.size() + " 名玩家发送链接: " + url);
    }
}
