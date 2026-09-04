package com.github.cocosoys.mc.soyshttpovermc.command;

import com.github.cocosoys.mc.soyshttpovermc.HttpOverMcPlugin;
import com.github.cocosoys.mc.soyshttpovermc.permission.CombinedPermissionService;
import com.github.cocosoys.mc.soyshttpovermc.permission.local.LocalPermissionStore;
import com.github.cocosoys.mc.soyshttpovermc.permission.local.SoysPermGroup;
import com.github.cocosoys.mc.soyshttpovermc.permission.local.SoysPermPermission;
import com.github.cocosoys.mc.soyshttpovermc.permission.local.SoysPermUser;
import com.github.cocosoys.mc.soyshttpovermc.permission.local.SoysPermUserGroup;
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
        return "/soyshttp perm —— 本地内置权限表（组/用户 CRUD + 查询），配套 permission.offline-fallback: local";
    }

    @Override
    public String detail() {
        return "本地内置权限表管理（offline-fallback=local 的配套表，同时服务 API 权限与网页权限）。\n"
                + "组：perm group create|delete|weight|add|remove|list\n"
                + "用户：perm user <玩家> group add|remove <组> / add|remove <权限> / list / expiry <epoch|clear>\n"
                + "调试：perm check <玩家> <权限>\n"
                + "节点规则：':' ≡ '.'（test:ping ≡ test.ping）；'-' 前缀=否定；'*' 全量通配；'a.*' 段级通配。\n"
                + "仅 op 可执行。";
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        CombinedPermissionService cps = plugin.getCombinedPermissionService();
        if (cps == null) {
            msg(sender, "§c权限服务尚未就绪，请稍后重试");
            return;
        }
        LocalPermissionStore store = cps.getLocalStore();
        if (args.length < 2) {
            msg(sender, "§e用法：/soyshttp perm group|user|check|reload ...（详见 /soyshttp help perm）");
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
                msg(sender, "§a已重新加载权限提供者组合（含本地表）");
                break;
            default:
                msg(sender, "§c未知子动作：§f" + action + " §c（支持 group/user/check/reload）");
        }
    }

    // ==================== 组 ====================

    private void groupCmd(CommandSender sender, LocalPermissionStore store, String[] args) {
        if (args.length < 3) {
            msg(sender, "§e用法：/soyshttp perm group create|delete|weight|add|remove|list ...");
            return;
        }
        String sub = args[2].toLowerCase();
        String id = args.length > 3 ? args[3] : "";
        switch (sub) {
            case "create": {
                if (id.isEmpty()) { msg(sender, "§c用法：/soyshttp perm group create <id> [weight] [display]"); return; }
                int weight = args.length > 4 ? parseInt(args[4], 0) : 0;
                String display = args.length > 5 ? args[5] : id;
                boolean ok = store.createGroup(id, weight, display, "");
                msg(sender, ok ? "§a权限组 §f" + id + " §a已创建/更新（weight=" + weight + ", display=" + display + "）"
                        : "§c创建权限组失败");
                break;
            }
            case "delete": {
                if (id.isEmpty()) { msg(sender, "§c用法：/soyshttp perm group delete <id>"); return; }
                if (store.getGroup(id) == null) { msg(sender, "§c权限组 §f" + id + " §c不存在"); return; }
                boolean ok = store.deleteGroup(id);
                msg(sender, ok ? "§a权限组 §f" + id + " §a已删除（含其权限与成员引用）" : "§c删除权限组失败");
                break;
            }
            case "weight": {
                if (id.isEmpty() || args.length < 5) { msg(sender, "§c用法：/soyshttp perm group weight <id> <weight>"); return; }
                int w = parseInt(args[4], -1);
                if (w < 0) { msg(sender, "§c权重须为非负整数"); return; }
                SoysPermGroup g = store.getGroup(id);
                if (g == null) { msg(sender, "§c权限组 §f" + id + " §c不存在"); return; }
                boolean ok = store.createGroup(id, w, g.getDisplay(), g.getDescription());
                msg(sender, ok ? "§a权限组 §f" + id + " §a权重已设为 §f" + w : "§c设置权重失败");
                break;
            }
            case "add":
            case "remove": {
                if (id.isEmpty() || args.length < 5) {
                    msg(sender, "§c用法：/soyshttp perm group " + sub + " <id> <权限>");
                    return;
                }
                if (store.getGroup(id) == null) { msg(sender, "§c权限组 §f" + id + " §c不存在"); return; }
                String node = args[4];
                boolean ok = "add".equals(sub)
                        ? store.addGroupPermission(id, node)
                        : store.removeGroupPermission(id, node);
                LocalPermissionStore.ParsedNode pn = LocalPermissionStore.parseNode(node);
                msg(sender, ok ? "§a权限组 §f" + id + " §a已" + ("add".equals(sub) ? "添加" : "移除")
                                + " §f" + (pn.negative ? "-" : "") + pn.node
                        : "§c操作失败（目标节点可能不存在）");
                break;
            }
            case "list": {
                if (!id.isEmpty()) {
                    SoysPermGroup g = store.getGroup(id);
                    if (g == null) { msg(sender, "§c权限组 §f" + id + " §c不存在"); return; }
                    msg(sender, "§a权限组 §f" + g.getId() + " §7(weight=" + g.getWeight()
                            + ", display=" + g.getDisplay() + ", desc=" + g.getDescription() + ")");
                    List<SoysPermPermission> perms = store.listGroupPermissions(id);
                    if (perms.isEmpty()) { sender.sendMessage("  §7（无权限）"); }
                    for (SoysPermPermission p : perms) {
                        sender.sendMessage("  §" + (p.isNegative() ? "c-" : "a") + p.getPermission());
                    }
                    List<SoysPermUserGroup> members = store.listGroupMembers(id);
                    if (!members.isEmpty()) {
                        StringBuilder sb = new StringBuilder("  §7成员: §f");
                        for (int i = 0; i < members.size(); i++) {
                            if (i > 0) sb.append(", ");
                            String mId = members.get(i).getUuid();
                            SoysPermUser mu = store.getUser(mId);
                            sb.append(mu != null && mu.getPlayer() != null && !mu.getPlayer().isEmpty()
                                    ? mu.getPlayer() : mId);
                        }
                        sender.sendMessage(sb.toString());
                    }
                } else {
                    List<SoysPermGroup> groups = store.listGroups();
                    if (groups.isEmpty()) { msg(sender, "§7（暂无权限组）"); return; }
                    msg(sender, "§a本地权限组（共 " + groups.size() + " 个）:");
                    for (SoysPermGroup g : groups) {
                        sender.sendMessage("  §f" + g.getId() + " §7weight=§f" + g.getWeight()
                                + " §7display=§f" + g.getDisplay());
                    }
                }
                break;
            }
            default:
                msg(sender, "§c未知组动作：§f" + sub + " §c（支持 create/delete/weight/add/remove/list）");
        }
    }

    // ==================== 用户 ====================

    private void userCmd(CommandSender sender, LocalPermissionStore store, String[] args) {
        if (args.length < 4) {
            msg(sender, "§e用法：/soyshttp perm user <玩家> group add|remove <组> / add|remove <权限> / list / expiry <epoch|clear>");
            return;
        }
        String player = args[2];
        String sub = args[3].toLowerCase();
        switch (sub) {
            case "group": {
                if (args.length < 6) { msg(sender, "§c用法：/soyshttp perm user <玩家> group add|remove <组>"); return; }
                String gsub = args[4].toLowerCase();
                String group = args[5];
                if (!"add".equals(gsub) && !"remove".equals(gsub)) {
                    msg(sender, "§c未知组动作：§f" + gsub + " §c（支持 add/remove）");
                    return;
                }
                if ("add".equals(gsub) && store.getGroup(group) == null) {
                    msg(sender, "§c权限组 §f" + group + " §c不存在（请先 group create）");
                    return;
                }
                boolean ok = "add".equals(gsub) ? store.addUserGroup(player, group) : store.removeUserGroup(player, group);
                msg(sender, ok ? "§a玩家 §f" + player + " §a已" + ("add".equals(gsub) ? "加入" : "移出")
                                + " 权限组 §f" + group
                        : "§c操作失败（目标关联可能不存在）");
                break;
            }
            case "add":
            case "remove": {
                if (args.length < 5) {
                    msg(sender, "§c用法：/soyshttp perm user <玩家> " + sub + " <权限>");
                    return;
                }
                String node = args[4];
                boolean ok = "add".equals(sub)
                        ? store.addUserPermission(player, node)
                        : store.removeUserPermission(player, node);
                LocalPermissionStore.ParsedNode pn = LocalPermissionStore.parseNode(node);
                msg(sender, ok ? "§a玩家 §f" + player + " §a已" + ("add".equals(sub) ? "添加" : "移除")
                                + " 直接权限 §f" + (pn.negative ? "-" : "") + pn.node
                        : "§c操作失败（目标权限可能不存在）");
                break;
            }
            case "list": {
                SoysPermUser u = store.getUser(player);
                if (u == null) { msg(sender, "§c玩家 §f" + player + " §c未在本地权限表登记"); return; }
                msg(sender, "§a玩家 §f" + player + " §7(过期=" + ("".equals(u.getExpiry()) ? "永久" : u.getExpiry()) + ")");
                List<String> groups = store.listUserGroups(player);
                sender.sendMessage("  所属组: " + (groups.isEmpty() ? "§7（无）" : "§f" + String.join("§7, §f", groups)));
                List<SoysPermPermission> eff = store.listEffectivePermissions(player);
                if (eff.isEmpty()) { sender.sendMessage("  §7（无任何生效权限）"); }
                for (SoysPermPermission p : eff) {
                    String src = SoysPermPermission.TYPE_USER.equals(p.getOwnerType()) ? "直接" : "组[" + p.getOwnerId() + "]";
                    sender.sendMessage("  §" + (p.isNegative() ? "c-" : "a") + p.getPermission() + " §7(" + src + ")");
                }
                break;
            }
            case "expiry": {
                if (args.length < 5) { msg(sender, "§c用法：/soyshttp perm user <玩家> expiry <epoch|clear>"); return; }
                boolean ok = store.setUserExpiry(player, args[4]);
                String exp = args[4];
                msg(sender, ok ? ("§a玩家 §f" + player + " §a整体过期已设为 §f"
                                + ("clear".equalsIgnoreCase(exp) || "0".equals(exp) ? "永久" : exp))
                        : "§c设置过期失败（须为 epoch 毫秒或 clear）");
                break;
            }
            default:
                msg(sender, "§c未知用户动作：§f" + sub + " §c（支持 group/add/remove/list/expiry）");
        }
    }

    // ==================== 调试 ====================

    private void checkCmd(CommandSender sender, LocalPermissionStore store, String[] args) {
        if (args.length < 4) { msg(sender, "§c用法：/soyshttp perm check <玩家> <权限>"); return; }
        String player = args[2];
        String node = args[3];
        String norm = LocalPermissionStore.normalize(node);
        boolean has = store.check(player, node);
        SoysPermUser u = store.getUser(player);
        StringBuilder sb = new StringBuilder();
        sb.append(has ? "§a通过" : "§c拒绝");
        sb.append("  §7[玩家=§f").append(player).append("§7, 归一节点=§f").append(norm).append("§7]");
        if (u == null) {
            sb.append(" §7(未登记)");
        } else if (store.isExpired(player)) {
            sb.append(" §7(已过期)");
        }
        msg(sender, "§e权限判定: " + sb);
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
