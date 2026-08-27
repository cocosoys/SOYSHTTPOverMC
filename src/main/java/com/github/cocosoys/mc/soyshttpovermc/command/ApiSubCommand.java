package com.github.cocosoys.mc.soyshttpovermc.command;

import org.bukkit.command.CommandSender;

import com.github.cocosoys.mc.soyshttpovermc.HttpOverMcPlugin;
import com.github.cocosoys.mc.soyshttpovermc.web.ApiRegistry;
import com.github.cocosoys.mc.soyshttpovermc.api.event.ApiInfo;
import com.github.cocosoys.mc.soyshttpovermc.i18n.I18n;
import com.github.cocosoys.mc.soyshttpovermc.util.StringListUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        return I18n.t("command.api.usage", "/soyshttp api [插件名] —— 查看已注册的注解式 API 端点");
    }

    @Override
    public String detail() {
        return I18n.t("command.api.detail",
                "/soyshttp api [插件名] —— 查看已注册的注解式 API 端点（便于开发者自查路由）。\n"
                + "  无参数      列出全部端点（HTTP 方法 + 路径 + 端点名 + 所属插件 + 所需权限）。\n"
                + "  <插件名>    仅列出该插件注册的端点。");
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        ApiRegistry reg = plugin.getApiRegistry();
        if (reg == null) {
            msgT(sender, "command.api.uninit", "API 注册表未初始化");
            return;
        }
        List<ApiInfo> all = reg.listEndpoints();
        String filter = null;
        int page = 1;
        for (int i = 1; i < args.length; i++) {
            if (isNumeric(args[i])) {
                page = parseIntSafe(args[i]); // 数字参数视为页码
            } else {
                filter = args[i];           // 非数字参数视为插件名过滤
            }
        }
        // 按 ownerPlugin 分组聚合：同一插件的所有端点集中到一段连续列表，
        // 不再因注册顺序交错而在上方重复“—— 插件 X ——”前缀。
        LinkedHashMap<String, List<ApiInfo>> groups = new LinkedHashMap<>();
        for (ApiInfo info : all) {
            if (filter != null && !filter.equalsIgnoreCase(info.getOwnerPlugin())) continue;
            groups.computeIfAbsent(info.getOwnerPlugin(), k -> new ArrayList<>()).add(info);
        }
        List<String> content = new ArrayList<>();
        for (Map.Entry<String, List<ApiInfo>> g : groups.entrySet()) {
            content.add(I18n.t("command.api.group-header", "  §7—— 插件 §e{0} §7——", g.getKey()));
            for (ApiInfo info : g.getValue()) {
                String perm = info.getPermission().isEmpty() ? ""
                        : I18n.t("command.api.perm-suffix", " §7(权限={0})", info.getPermission());
                content.add("  §a" + info.getHttpMethod() + " §e" + info.getPath()
                        + " §7[" + info.getApiName() + "]" + perm);
            }
        }
        if (content.isEmpty()) {
            String empty = filter == null
                    ? I18n.t("command.api.empty-none", "无已注册端点")
                    : I18n.t("command.api.empty-filter", "无插件 {0} 的端点", filter);
            SubCommand.sendColored(sender, I18n.t("command.api.empty-line", "  §7（{0}）", empty));
            return;
        }
        // 分页（借助 StringListUtil.PagedResult）：同插件聚合后整体翻页，页尾标注页码与总量。
        int pageSize = StringListUtil.DEFAULT_PAGE_SIZE;
        int totalItems = content.size();
        int totalPages = Math.max(1, (totalItems + pageSize - 1) / pageSize);
        int cur = Math.max(1, Math.min(page, totalPages));
        String header = filter == null ? null : I18n.t("command.api.page-filter-header", "  §7（插件 {0}）", filter);
        String footer = I18n.t("command.api.page-footer",
                "  §7· 第 {0}/{1} 页 · 共 {2} 个端点 · /{3} api <页码> 翻页 ·",
                cur, totalPages, totalItems, label);
        for (String line : StringListUtil.page(header, content, footer, cur, pageSize).lines) {
            SubCommand.sendColored(sender, line);
        }
    }

    private static boolean isNumeric(String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return true;
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }
}
