package com.github.cocosoys.mc.soyshttpovermc.command;

import com.github.cocosoys.mc.soyshttpovermc.HttpOverMcPlugin;
import com.github.cocosoys.mc.soyshttpovermc.i18n.I18n;
import com.github.cocosoys.mc.soyshttpovermc.permission.CombinedPermissionService;
import com.github.cocosoys.mc.soyshttpovermc.permission.local.LocalPermissionStore;
import com.github.cocosoys.mc.soyshttpovermc.spring.entity.SoysPermGroup;
import com.github.cocosoys.mc.soyshttpovermc.spring.entity.SoysPermPermission;
import com.github.cocosoys.mc.soyshttpovermc.spring.entity.SoysPermUser;
import com.github.cocosoys.mc.soyshttpovermc.spring.entity.SoysPermUserGroup;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * /soyshttp perm —— 本地内置权限表管理（配套 {@code permission.offline-fallback: local}）。
 *
 * <p>语法：</p>
 * <pre>
 *   /soyshttp perm group create &lt;id&gt; [weight] [display]    创建/更新权限组
 *   /soyshttp perm group delete &lt;id&gt;                       删除权限组（连带权限与成员引用）
 *   /soyshttp perm group weight &lt;id&gt; &lt;weight&gt;             设置组权重
 *   /soyshttp perm group add &lt;id&gt; &lt;权限&gt;                 组加权限（- 前缀=否定；: ≡ .）
 *   /soyshttp perm group remove &lt;id&gt; &lt;权限&gt;               组删权限
 *   /soyshttp perm group list [id]                         列出组（或某组权限与成员）
 *   /soyshttp perm user &lt;玩家&gt; group add|remove &lt;组&gt;      用户归属组管理
 *   /soyshttp perm user &lt;玩家&gt; add|remove &lt;权限&gt;          用户直接权限管理
 *   /soyshttp perm user &lt;玩家&gt; list                        展示用户生效权限与过期
 *   /soyshttp perm user &lt;玩家&gt; expiry &lt;epoch|clear&gt;       设置/清除用户整体过期
 *   /soyshttp perm check &lt;玩家&gt; &lt;权限&gt;                     调试：归一化后判定该玩家是否拥有权限
 *   /soyshttp perm reload                                  重新加载权限提供者组合
 * </pre>
 *
 * <p>仅 op 可执行。节点归一规则：{@code ':' ≡ '.'}（{@code test:ping} ≡ {@code test.ping}）；
 * {@code -} 前缀=否定；通配支持 {@code *}（全量）与 {@code a.*}（段级尾通配）。</p>
 */
public class PermSubCommand extends SubCommand {

    public PermSubCommand(HttpOverMcPlugin plugin) {
        super(plugin);
    }

    @Override
    public String name() {
        return "perm";
    }

    @Override
    public String usage() {
        return I18n.t("command.perm.usage-short",
                "/soyshttp perm —— 本地内置权限表（组/用户 CRUD + 查询），配套 permission.offline-fallback: local");
    }

    @Override
    public String detail() {
        return I18n.t("command.perm.detail",
                "本地内置权限表管理（offline-fallback=local 的配套表，同时服务 API 权限与网页权限）。\n"
                        + "组：perm group create|delete|weight|add|remove|list\n"
                        + "用户：perm user <玩家> group add|remove <组> / add|remove <权限> / list / expiry <epoch|clear>\n"
                        + "调试：perm check <玩家> <权限>\n"
                        + "节点规则：':' ≡ '.'（test:ping ≡ test.ping）；'-' 前缀=否定；'*' 全量通配；'a.*' 段级通配。\n"
                        + "仅 op 可执行。");
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        CombinedPermissionService cps = plugin.getCombinedPermissionService();
        if (cps == null) {
            msgT(sender, "command.perm.service-not-ready", "§c权限服务尚未就绪，请稍后重试");
            return;
        }
        LocalPermissionStore store = cps.getLocalStore();
        if (args.length < 2) {
            msgT(sender, "command.perm.usage", "§e用法：/soyshttp perm group|user|check|reload ...（详见 /soyshttp help perm）");
            return;
        }
        String action = args[1].toLowerCase();
        switch (action) {
            case "group":
                groupCmd(sender, store, args);
                break;
            case "user":
                userCmd(sender, store, args);
                break;
            case "check":
                checkCmd(sender, store, args);
                break;
            case "reload":
                cps.reloadProviders();
                msgT(sender, "command.perm.reloaded", "§a已重新加载权限提供者组合（含本地表）");
                break;
            default:
                msgT(sender, "command.perm.unknown-action", "§c未知子动作：§f{0} §c（支持 group/user/check/reload）", action);
        }
    }

    // ==================== 组 ====================

    private void groupCmd(CommandSender sender, LocalPermissionStore store, String[] args) {
        if (args.length < 3) {
            msgT(sender, "command.perm.group.usage", "§e用法：/soyshttp perm group create|delete|weight|add|remove|list ...");
            return;
        }
        String sub = args[2].toLowerCase();
        String id = args.length > 3 ? args[3] : "";
        switch (sub) {
            case "create": {
                if (id.isEmpty()) {
                    msgT(sender, "command.perm.group.create-usage", "§c用法：/soyshttp perm group create <id> [weight] [display]");
                    return;
                }
                int weight = args.length > 4 ? parseInt(args[4], 0) : 0;
                String display = args.length > 5 ? args[5] : id;
                boolean ok = store.createGroup(id, weight, display, "");
                if (ok) {
                    msgT(sender, "command.perm.group.created",
                            "§a权限组 §f{0} §a已创建/更新（weight={1}, display={2}）", id, weight, display);
                } else {
                    msgT(sender, "command.perm.group.create-failed", "§c创建权限组失败");
                }
                break;
            }
            case "delete": {
                if (id.isEmpty()) {
                    msgT(sender, "command.perm.group.delete-usage", "§c用法：/soyshttp perm group delete <id>");
                    return;
                }
                if (store.getGroup(id) == null) {
                    msgT(sender, "command.perm.group.not-found", "§c权限组 §f{0} §c不存在", id);
                    return;
                }
                boolean ok = store.deleteGroup(id);
                if (ok) {
                    msgT(sender, "command.perm.group.deleted", "§a权限组 §f{0} §a已删除（含其权限与成员引用）", id);
                } else {
                    msgT(sender, "command.perm.group.delete-failed", "§c删除权限组失败");
                }
                break;
            }
            case "weight": {
                if (id.isEmpty() || args.length < 5) {
                    msgT(sender, "command.perm.group.weight-usage", "§c用法：/soyshttp perm group weight <id> <weight>");
                    return;
                }
                int w = parseInt(args[4], -1);
                if (w < 0) {
                    msgT(sender, "command.perm.group.weight-invalid", "§c权重须为非负整数");
                    return;
                }
                SoysPermGroup g = store.getGroup(id);
                if (g == null) {
                    msgT(sender, "command.perm.group.not-found", "§c权限组 §f{0} §c不存在", id);
                    return;
                }
                boolean ok = store.createGroup(id, w, g.getDisplay(), g.getDescription());
                if (ok) {
                    msgT(sender, "command.perm.group.weight-set", "§a权限组 §f{0} §a权重已设为 §f{1}", id, w);
                } else {
                    msgT(sender, "command.perm.group.weight-failed", "§c设置权重失败");
                }
                break;
            }
            case "add":
            case "remove": {
                if (id.isEmpty() || args.length < 5) {
                    msgT(sender, "command.perm.group.node-usage", "§c用法：/soyshttp perm group {0} <id> <权限>", sub);
                    return;
                }
                if (store.getGroup(id) == null) {
                    msgT(sender, "command.perm.group.not-found", "§c权限组 §f{0} §c不存在", id);
                    return;
                }
                String node = args[4];
                boolean ok = "add".equals(sub)
                        ? store.addGroupPermission(id, node)
                        : store.removeGroupPermission(id, node);
                LocalPermissionStore.ParsedNode pn = LocalPermissionStore.parseNode(node);
                String shown = (pn.negative ? "-" : "") + pn.node;
                if (ok) {
                    if ("add".equals(sub)) {
                        msgT(sender, "command.perm.group.node-added", "§a权限组 §f{0} §a已添加 §f{1}", id, shown);
                    } else {
                        msgT(sender, "command.perm.group.node-removed", "§a权限组 §f{0} §a已移除 §f{1}", id, shown);
                    }
                } else {
                    msgT(sender, "command.perm.group.node-failed", "§c操作失败（目标节点可能不存在）");
                }
                break;
            }
            case "list": {
                if (!id.isEmpty()) {
                    SoysPermGroup g = store.getGroup(id);
                    if (g == null) {
                        msgT(sender, "command.perm.group.not-found", "§c权限组 §f{0} §c不存在", id);
                        return;
                    }
                    msgT(sender, "command.perm.group.detail",
                            "§a权限组 §f{0} §7(weight={1}, display={2}, desc={3})",
                            g.getId(), g.getWeight(), g.getDisplay(), g.getDescription());
                    List<SoysPermPermission> perms = store.listGroupPermissions(id);
                    if (perms.isEmpty()) {
                        sender.sendMessage(I18n.t("command.perm.group.no-perms", "  §7（无权限）"));
                    }
                    for (SoysPermPermission p : perms) {
                        sender.sendMessage(I18n.t("command.perm.group.perm-line", "  §{0}{1}",
                                p.isNegative() ? "c-" : "a", p.getPermission()));
                    }
                    List<SoysPermUserGroup> members = store.listGroupMembers(id);
                    if (!members.isEmpty()) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < members.size(); i++) {
                            if (i > 0) sb.append(", ");
                            String mId = members.get(i).getUuid();
                            SoysPermUser mu = store.getUser(mId);
                            sb.append(mu != null && mu.getPlayer() != null && !mu.getPlayer().isEmpty()
                                    ? mu.getPlayer() : mId);
                        }
                        sender.sendMessage(I18n.t("command.perm.group.members", "  §7成员: §f{0}", sb.toString()));
                    }
                } else {
                    List<SoysPermGroup> groups = store.listGroups();
                    if (groups.isEmpty()) {
                        msgT(sender, "command.perm.group.empty", "§7（暂无权限组）");
                        return;
                    }
                    msgT(sender, "command.perm.group.list-header", "§a本地权限组（共 {0} 个）:", groups.size());
                    for (SoysPermGroup g : groups) {
                        sender.sendMessage(I18n.t("command.perm.group.list-line", "  §f{0} §7weight=§f{1} §7display=§f{2}",
                                g.getId(), g.getWeight(), g.getDisplay()));
                    }
                }
                break;
            }
            default:
                msgT(sender, "command.perm.group.unknown-sub",
                        "§c未知组动作：§f{0} §c（支持 create/delete/weight/add/remove/list）", sub);
        }
    }

    // ==================== 用户 ====================

    private void userCmd(CommandSender sender, LocalPermissionStore store, String[] args) {
        if (args.length < 4) {
            msgT(sender, "command.perm.user.usage",
                    "§e用法：/soyshttp perm user <玩家> group add|remove <组> / add|remove <权限> / list / expiry <yyyy-MM-dd HH:mm:ss|clear>");
            return;
        }
        String player = args[2];
        String sub = args[3].toLowerCase();
        switch (sub) {
            case "group": {
                if (args.length < 6) {
                    msgT(sender, "command.perm.user.group-usage", "§c用法：/soyshttp perm user <玩家> group add|remove <组>");
                    return;
                }
                String gsub = args[4].toLowerCase();
                String group = args[5];
                if (!"add".equals(gsub) && !"remove".equals(gsub)) {
                    msgT(sender, "command.perm.user.group-unknown", "§c未知组动作：§f{0} §c（支持 add/remove）", gsub);
                    return;
                }
                if ("add".equals(gsub) && store.getGroup(group) == null) {
                    msgT(sender, "command.perm.user.group-not-exist", "§c权限组 §f{0} §c不存在（请先 group create）", group);
                    return;
                }
                boolean ok = "add".equals(gsub) ? store.addUserGroup(player, group) : store.removeUserGroup(player, group);
                if (ok) {
                    if ("add".equals(gsub)) {
                        msgT(sender, "command.perm.user.group-added", "§a玩家 §f{0} §a已加入 权限组 §f{1}", player, group);
                    } else {
                        msgT(sender, "command.perm.user.group-removed", "§a玩家 §f{0} §a已移出 权限组 §f{1}", player, group);
                    }
                } else {
                    msgT(sender, "command.perm.user.group-failed", "§c操作失败（目标关联可能不存在）");
                }
                break;
            }
            case "add":
            case "remove": {
                if (args.length < 5) {
                    msgT(sender, "command.perm.user.node-usage", "§c用法：/soyshttp perm user <玩家> {0} <权限>", sub);
                    return;
                }
                String node = args[4];
                boolean ok = "add".equals(sub)
                        ? store.addUserPermission(player, node)
                        : store.removeUserPermission(player, node);
                LocalPermissionStore.ParsedNode pn = LocalPermissionStore.parseNode(node);
                String shown = (pn.negative ? "-" : "") + pn.node;
                if (ok) {
                    if ("add".equals(sub)) {
                        msgT(sender, "command.perm.user.node-added", "§a玩家 §f{0} §a已添加 直接权限 §f{1}", player, shown);
                    } else {
                        msgT(sender, "command.perm.user.node-removed", "§a玩家 §f{0} §a已移除 直接权限 §f{1}", player, shown);
                    }
                } else {
                    msgT(sender, "command.perm.user.node-failed", "§c操作失败（目标权限可能不存在）");
                }
                break;
            }
            case "list": {
                SoysPermUser u = store.getUser(player);
                if (u == null) {
                    msgT(sender, "command.perm.user.not-registered", "§c玩家 §f{0} §c未在本地权限表登记", player);
                    return;
                }
                String expiry = u.getExpiry() == null
                        ? I18n.t("command.perm.user.expiry-forever", "永久")
                        : com.github.cocosoys.mc.soyshttpovermc.orm.convertor.BeanCodec.formatDate(u.getExpiry());
                msgT(sender, "command.perm.user.detail", "§a玩家 §f{0} §7(过期={1})", player, expiry);
                List<String> groups = store.listUserGroups(player);
                String groupsLine = groups.isEmpty()
                        ? I18n.t("command.perm.user.no-groups", "§7（无）")
                        : "§f" + String.join("§7, §f", groups);
                sender.sendMessage(I18n.t("command.perm.user.groups", "  所属组: {0}", groupsLine));
                List<SoysPermPermission> eff = store.listEffectivePermissions(player);
                if (eff.isEmpty()) {
                    sender.sendMessage(I18n.t("command.perm.user.no-effective", "  §7（无任何生效权限）"));
                }
                for (SoysPermPermission p : eff) {
                    String src = SoysPermPermission.TYPE_USER.equals(p.getOwnerType())
                            ? I18n.t("command.perm.user.src-direct", "直接")
                            : I18n.t("command.perm.user.src-group", "组[{0}]", p.getOwnerId());
                    sender.sendMessage(I18n.t("command.perm.user.effective-line", "  §{0}{1} §7({2})",
                            p.isNegative() ? "c-" : "a", p.getPermission(), src));
                }
                break;
            }
            case "expiry": {
                if (args.length < 5) {
                    msgT(sender, "command.perm.user.expiry-usage", "§c用法：/soyshttp perm user <玩家> expiry <yyyy-MM-dd HH:mm:ss|clear>");
                    return;
                }
                boolean ok = store.setUserExpiry(player, args[4]);
                String exp = args[4];
                boolean forever = "clear".equalsIgnoreCase(exp) || "0".equals(exp);
                if (ok) {
                    if (forever) {
                        msgT(sender, "command.perm.user.expiry-set-forever", "§a玩家 §f{0} §a整体过期已设为 §f永久", player);
                    } else {
                        msgT(sender, "command.perm.user.expiry-set", "§a玩家 §f{0} §a整体过期已设为 §f{1}", player, exp);
                    }
                } else {
                    msgT(sender, "command.perm.user.expiry-failed", "§c设置过期失败（须为 yyyy-MM-dd HH:mm:ss 或 clear）");
                }
                break;
            }
            default:
                msgT(sender, "command.perm.user.unknown-sub",
                        "§c未知用户动作：§f{0} §c（支持 group/add/remove/list/expiry）", sub);
        }
    }

    // ==================== 调试 ====================

    private void checkCmd(CommandSender sender, LocalPermissionStore store, String[] args) {
        if (args.length < 4) {
            msgT(sender, "command.perm.check.usage", "§c用法：/soyshttp perm check <玩家> <权限>");
            return;
        }
        String player = args[2];
        String node = args[3];
        String norm = LocalPermissionStore.normalize(node);
        boolean has = store.check(player, node);
        SoysPermUser u = store.getUser(player);
        StringBuilder sb = new StringBuilder();
        sb.append(has
                ? I18n.t("command.perm.check.pass", "§a通过")
                : I18n.t("command.perm.check.deny", "§c拒绝"));
        sb.append(I18n.t("command.perm.check.ctx", "  §7[玩家=§f{0}§7, 归一节点=§f{1}§7]", player, norm));
        if (u == null) {
            sb.append(I18n.t("command.perm.check.unregistered", " §7(未登记)"));
        } else if (store.isExpired(player)) {
            sb.append(I18n.t("command.perm.check.expired", " §7(已过期)"));
        }
        msgT(sender, "command.perm.check.result", "§e权限判定: {0}", sb.toString());
    }

    // ==================== tab 补全 ====================

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length <= 2) {
            out.addAll(Arrays.asList("group", "user", "check", "reload"));
            return out;
        }
        String action = args[1].toLowerCase();
        if (args.length == 3) {
            if ("group".equals(action)) {
                out.addAll(Arrays.asList("create", "delete", "weight", "add", "remove", "list"));
            } else if ("user".equals(action)) {
                out.addAll(Arrays.asList("group", "add", "remove", "list", "expiry"));
            } else if ("check".equals(action)) {
                // 候选玩家由调度层统一过滤，这里补全在线玩家名
                for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                    out.add(p.getName());
                }
            }
        } else if (args.length == 4 && "user".equals(action)) {
            out.addAll(Arrays.asList("group", "add", "remove", "list", "expiry"));
        } else if (args.length == 5 && "user".equals(action) && "group".equalsIgnoreCase(args[3])) {
            out.addAll(Arrays.asList("add", "remove"));
        }
        return out;
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
