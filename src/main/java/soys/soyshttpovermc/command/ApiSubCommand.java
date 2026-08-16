package soys.soyshttpovermc.command;

import org.bukkit.command.CommandSender;

import soys.soyshttpovermc.HttpOverMcPlugin;
import soys.soyshttpovermc.ApiRegistry;
import soys.soyshttpovermc.api.event.ApiInfo;

import java.util.List;

/**
 * /soyshttp api —— 查看当前已注册的注解式 API 端点（镜像 /soyshttp pages，便于开发者自查路由）。
 *
 * <ul>
 *   <li>无参数：列出全部端点（方法 + 路径 + 端点名 + owner，含所需权限）；</li>
 *   <li>带参数 {@code /shttp api <插件名>}：仅列出该插件注册的端点。</li>
 * </ul>
 */
public class ApiSubCommand extends SubCommand {

    public ApiSubCommand(HttpOverMcPlugin plugin) {
        super(plugin);
    }

    @Override
    public String name() {
        return "api";
    }

    @Override
    public String usage() {
        return "/soyshttp api [插件名] —— 查看已注册的注解式 API 端点";
    }

    @Override
    public String detail() {
        return "/soyshttp api [插件名] —— 查看已注册的注解式 API 端点（便于开发者自查路由）。\n"
                + "  无参数      列出全部端点（HTTP 方法 + 路径 + 端点名 + 所属插件 + 所需权限）。\n"
                + "  <插件名>    仅列出该插件注册的端点。";
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        ApiRegistry reg = plugin.getApiRegistry();
        if (reg == null) {
            msg(sender, "API 注册表未初始化");
            return;
        }
        List<ApiInfo> all = reg.listEndpoints();
        String filter = args.length > 1 ? args[1] : null;
        int shown = 0;
        String curOwner = "\u0000";
        for (ApiInfo info : all) {
            if (filter != null && !filter.equalsIgnoreCase(info.getOwnerPlugin())) continue;
            if (!info.getOwnerPlugin().equals(curOwner)) {
                curOwner = info.getOwnerPlugin();
                sender.sendMessage("  §7—— 插件 §e" + curOwner + " §7——");
            }
            String perm = info.getPermission().isEmpty() ? "" : " §7(权限=" + info.getPermission() + ")";
            sender.sendMessage("  §a" + info.getHttpMethod() + " §e" + info.getPath()
                    + " §7[" + info.getApiName() + "]" + perm);
            shown++;
        }
        if (shown == 0) {
            sender.sendMessage("  §7（" + (filter == null ? "无已注册端点" : "无插件 " + filter + " 的端点") + "）");
        } else {
            msg(sender, "共 " + shown + " 个端点" + (filter == null ? "" : "（插件 " + filter + "）"));
        }
    }
}
