package com.github.cocosoys.mc.soyshttpovermc.command;

import com.github.cocosoys.mc.soyshttpovermc.HttpOverMcPlugin;
import com.github.cocosoys.mc.soyshttpovermc.i18n.I18n;
import com.github.cocosoys.mc.soyshttpovermc.permission.local.ApiKeyStore;
import com.github.cocosoys.mc.soyshttpovermc.spring.entity.SoysApiKey;
import com.github.cocosoys.mc.soyshttpovermc.spring.entity.SoysPermPermission;
import com.github.cocosoys.mc.soyshttpovermc.orm.convertor.BeanCodec;
import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * /soyshttp apikey —— X-API-Key 本地表管理（认证门 + 权限门共用 soys_api_key 表）。
 *
 * <p>语法：</p>
 * <pre>
 *   /soyshttp apikey create [备注]                      生成新 key（明文仅本次展示；库中只存哈希+指纹）
 *   /soyshttp apikey list                               列出全部 key（指纹/绑定/启用/过期/使用次数）
 *   /soyshttp apikey info &lt;id|指纹&gt;                     查看单个 key 详情
 *   /soyshttp apikey remove &lt;id|指纹&gt;                   删除 key（连带其权限记录）
 *   /soyshttp apikey enable|disable &lt;id|指纹&gt;           启用 / 停用
 *   /soyshttp apikey expiry &lt;id|指纹&gt; &lt;yyyy-MM-dd HH:mm:ss|clear&gt;   设置 / 清除过期
 *   /soyshttp apikey bind &lt;id|指纹&gt; &lt;玩家|UUID&gt;         绑定玩家（归一到 UUID；一期仅存储）
 *   /soyshttp apikey unbind &lt;id|指纹&gt;                   解除玩家绑定
 *   /soyshttp apikey grant &lt;id|指纹&gt; &lt;权限&gt;             加权限（- 前缀=否定；: ≡ .）
 *   /soyshttp apikey revoke &lt;id|指纹&gt; &lt;权限&gt;            删权限
 *   /soyshttp apikey perms &lt;id|指纹&gt;                    列出权限记录
 * </pre>
 *
 * <p>仅 op 可执行。密钥明文在创建时仅展示一次；日常管理用 8 位指纹（fingerprint）标识。
 * 权限判定复用本地权限节点（ownerType=APIKEY，支持否定/通配/过期），规则同
 * {@code /soyshttp perm}（':' ≡ '.'，'-' 前缀=否定，'*' 全量，'a.*' 段级通配）。</p>
 */
public class ApiKeySubCommand extends SubCommand {

    public ApiKeySubCommand(HttpOverMcPlugin plugin) {
        super(plugin);
    }

    @Override
    public String name() {
        return "apikey";
    }

    @Override
    public String usage() {
        return I18n.t("command.apikey.usage-short",
                "/soyshttp apikey —— X-API-Key 本地表管理（生成/启停/过期/绑定/权限，认证门与权限门共用）");
    }

    @Override
    public String detail() {
        return I18n.t("command.apikey.detail",
                "X-API-Key 本地表管理（soys_api_key；认证门 AuthPolicy + 权限门共用，替代旧 auth.yml 静态 keys）。\n"
                        + "create [备注] —— 生成新 key（明文仅本次展示；库中只存 SHA-256 哈希 + 8 位指纹）\n"
                        + "list —— 列出全部 key（指纹/绑定/启用/过期/次数）\n"
                        + "info <id|指纹> —— 查看单个 key 详情\n"
                        + "remove <id|指纹> —— 删除 key（连带权限记录）\n"
                        + "enable|disable <id|指纹> —— 启用 / 停用\n"
                        + "expiry <id|指纹> <yyyy-MM-dd HH:mm:ss|clear> —— 设置 / 清除过期\n"
                        + "bind <id|指纹> <玩家|UUID> —— 绑定玩家（归一到 UUID；一期仅存储）\n"
                        + "unbind <id|指纹> —— 解除玩家绑定\n"
                        + "grant|revoke <id|指纹> <权限> —— 加 / 删权限（- 前缀=否定；: ≡ .）\n"
                        + "perms <id|指纹> —— 列出权限记录\n"
                        + "仅 op 可执行；日常管理用指纹标识，勿泄露明文。");
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        ApiKeyStore store = new ApiKeyStore(
                new com.github.cocosoys.mc.soyshttpovermc.spring.impl.LocalPermStorageImpl());
        if (args.length < 2) {
            msgT(sender, "command.apikey.usage", "§e用法：/soyshttp apikey create|list|info|remove|enable|disable|expiry|bind|unbind|grant|revoke|perms ...（详见 /soyshttp help apikey）");
            return;
        }
        String action = args[1].toLowerCase();
        switch (action) {
            case "create":
                createCmd(sender, store, args);
                break;
            case "list":
                listCmd(sender, store);
                break;
            case "info":
                infoCmd(sender, store, args);
                break;
            case "remove":
                removeCmd(sender, store, args);
                break;
            case "enable":
                enableCmd(sender, store, args, true);
                break;
            case "disable":
                enableCmd(sender, store, args, false);
                break;
            case "expiry":
                expiryCmd(sender, store, args);
                break;
            case "bind":
                bindCmd(sender, store, args, true);
                break;
            case "unbind":
                bindCmd(sender, store, args, false);
                break;
            case "grant":
                permCmd(sender, store, args, true);
                break;
            case "revoke":
                permCmd(sender, store, args, false);
                break;
            case "perms":
                permsCmd(sender, store, args);
                break;
            default:
                msgT(sender, "command.apikey.unknown-action", "§c未知子动作：§f{0} §c（支持 create/list/info/remove/enable/disable/expiry/bind/unbind/grant/revoke/perms）", action);
        }
    }

    // ==================== 子命令 ====================

    private void createCmd(CommandSender sender, ApiKeyStore store, String[] args) {
        String remark = args.length > 2 ? joinArgs(args, 2) : "";
        String plain;
        try {
            plain = store.generate(remark);
        } catch (Throwable t) {
            msgT(sender, "command.apikey.create-failed", "§c生成 X-API-Key 失败：{0}", t.getMessage());
            return;
        }
        // 明文仅本次展示；指纹供日常管理使用
        String fp = com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.util.AuthUtils.fingerprint(plain);
        msg(sender, I18n.t("command.apikey.created",
                "§a已生成 X-API-Key（明文仅本次展示，请立即保存；库中只存哈希）：\n§f  {0}\n§e  指纹（日常管理用，勿泄露明文）：{1}\n§e  权限管理：/soyshttp apikey grant {1} <权限>",
                plain, fp));
    }

    private void listCmd(CommandSender sender, ApiKeyStore store) {
        List<SoysApiKey> all = store.list();
        if (all.isEmpty()) {
            msgT(sender, "command.apikey.list-empty", "§e暂无 X-API-Key；/soyshttp apikey create [备注] 生成一个");
            return;
        }
        StringBuilder sb = new StringBuilder(I18n.t("command.apikey.list-header", "§6X-API-Key 列表（共 {0} 个）:", all.size())).append('\n');
        for (SoysApiKey k : all) {
            sb.append("  §f").append(k.getFingerprint())
                    .append("§7 启=").append(k.isEnabled() ? "§a是" : "§c否")
                    .append("§7 绑定=").append(k.getUuid() == null ? "-" : k.getPlayer() == null ? k.getUuid() : k.getPlayer())
                    .append("§7 过期=").append(k.getExpiry() == null ? "永久" : BeanCodec.formatDate(k.getExpiry()))
                    .append("§7 次数=").append(k.getUsedCount())
                    .append("§7 ").append(k.getRemark() == null ? "" : k.getRemark());
            if (all.indexOf(k) < all.size() - 1) sb.append('\n');
        }
        sendColored(sender, sb.toString());
    }

    private void infoCmd(CommandSender sender, ApiKeyStore store, String[] args) {
        SoysApiKey k = requireKey(sender, store, args, 2);
        if (k == null) return;
        msg(sender, I18n.t("command.apikey.info",
                "§6X-API-Key 详情：\n§f  主键: {0}\n§f  指纹: {1}\n§f  启用: {2}\n§f  绑定: {3}\n§f  过期: {4}\n§f  最后使用: {5}\n§f  次数: {6}\n§f  创建: {7}\n§f  备注: {8}",
                k.getId(), k.getFingerprint(),
                k.isEnabled() ? "是" : "否",
                k.getUuid() == null ? "未绑定" : (k.getPlayer() == null ? k.getUuid() : k.getPlayer() + " (" + k.getUuid() + ")"),
                k.getExpiry() == null ? "永久" : BeanCodec.formatDate(k.getExpiry()),
                k.getLastUsedAt() == null ? "-" : BeanCodec.formatDate(k.getLastUsedAt()),
                k.getUsedCount(),
                k.getCreateTime() == null ? "-" : BeanCodec.formatDate(k.getCreateTime()),
                k.getRemark() == null ? "" : k.getRemark()));
    }

    private void removeCmd(CommandSender sender, ApiKeyStore store, String[] args) {
        SoysApiKey k = requireKey(sender, store, args, 2);
        if (k == null) return;
        if (store.remove(k.getId())) {
            msgT(sender, "command.apikey.removed", "§a已删除 X-API-Key（指纹 §f{0}§a，连带其权限记录）", k.getFingerprint());
        } else {
            msgT(sender, "command.apikey.remove-failed", "§c删除失败");
        }
    }

    private void enableCmd(CommandSender sender, ApiKeyStore store, String[] args, boolean enable) {
        SoysApiKey k = requireKey(sender, store, args, 2);
        if (k == null) return;
        if (store.setEnabled(k.getId(), enable)) {
            msgT(sender, enable ? "command.apikey.enabled" : "command.apikey.disabled",
                    enable ? "§a已启用 X-API-Key（指纹 §f{0}§a）" : "§c已停用 X-API-Key（指纹 §f{0}§c；认证门将拒绝）",
                    k.getFingerprint());
        } else {
            msgT(sender, "command.apikey.update-failed", "§c更新失败");
        }
    }

    private void expiryCmd(CommandSender sender, ApiKeyStore store, String[] args) {
        SoysApiKey k = requireKey(sender, store, args, 2);
        if (k == null) return;
        if (args.length < 4) {
            msgT(sender, "command.apikey.expiry-usage", "§c用法：/soyshttp apikey expiry <id|指纹> <yyyy-MM-dd HH:mm:ss|clear>");
            return;
        }
        String input = joinArgs(args, 3);
        if (store.setExpiry(k.getId(), input)) {
            SoysApiKey after = store.findById(k.getId());
            msgT(sender, "command.apikey.expiry-set", "§a已设置过期：§f{0}",
                    after == null || after.getExpiry() == null ? "永久" : BeanCodec.formatDate(after.getExpiry()));
        } else {
            msgT(sender, "command.apikey.expiry-invalid", "§c设置失败：时间格式应为 yyyy-MM-dd HH:mm:ss，或输入 clear 清除过期");
        }
    }

    private void bindCmd(CommandSender sender, ApiKeyStore store, String[] args, boolean bind) {
        SoysApiKey k = requireKey(sender, store, args, 2);
        if (k == null) return;
        if (bind) {
            if (args.length < 4) {
                msgT(sender, "command.apikey.bind-usage", "§c用法：/soyshttp apikey bind <id|指纹> <玩家|UUID>");
                return;
            }
            String target = joinArgs(args, 3);
            if (store.bind(k.getId(), target)) {
                SoysApiKey after = store.findById(k.getId());
                msgT(sender, "command.apikey.bound", "§a已绑定玩家 §f{0}§a（UUID §f{1}§a；一期仅存储，权限判定仍按 key 自身节点）",
                        after == null || after.getPlayer() == null ? target : after.getPlayer(),
                        after == null ? "?" : after.getUuid());
            } else {
                msgT(sender, "command.apikey.update-failed", "§c更新失败");
            }
        } else {
            if (store.unbind(k.getId())) {
                msgT(sender, "command.apikey.unbound", "§a已解除玩家绑定（key 仍独立有效）");
            } else {
                msgT(sender, "command.apikey.update-failed", "§c更新失败");
            }
        }
    }

    private void permCmd(CommandSender sender, ApiKeyStore store, String[] args, boolean grant) {
        SoysApiKey k = requireKey(sender, store, args, 2);
        if (k == null) return;
        if (args.length < 4) {
            msgT(sender, "command.apikey.perm-usage", "§c用法：/soyshttp apikey {0} <id|指纹> <权限>", grant ? "grant" : "revoke");
            return;
        }
        String node = joinArgs(args, 3);
        if (grant) {
            if (store.addPermission(k.getId(), node)) {
                msgT(sender, "command.apikey.granted", "§a已为指纹 §f{0}§a 添加权限：§f{1}", k.getFingerprint(), node.trim());
            } else {
                msgT(sender, "command.apikey.perm-failed", "§c添加权限失败（节点为空或 key 不存在）");
            }
        } else {
            if (store.removePermission(k.getId(), node)) {
                msgT(sender, "command.apikey.revoked", "§a已移除权限：§f{0}", node.trim());
            } else {
                msgT(sender, "command.apikey.perm-failed", "§c移除权限失败（节点为空或 key 不存在）");
            }
        }
    }

    private void permsCmd(CommandSender sender, ApiKeyStore store, String[] args) {
        SoysApiKey k = requireKey(sender, store, args, 2);
        if (k == null) return;
        List<SoysPermPermission> perms = store.listPermissions(k.getId());
        if (perms.isEmpty()) {
            msgT(sender, "command.apikey.perms-empty", "§e指纹 §f{0}§e 暂无权限记录（grant 添加；- 前缀=否定）", k.getFingerprint());
            return;
        }
        StringBuilder sb = new StringBuilder(I18n.t("command.apikey.perms-header", "§6指纹 {0} 的权限记录（共 {1} 条）:", k.getFingerprint(), perms.size())).append('\n');
        for (int i = 0; i < perms.size(); i++) {
            SoysPermPermission p = perms.get(i);
            sb.append("  §f").append(p.isNegative() ? "-" : "").append(p.getPermission());
            if (i < perms.size() - 1) sb.append('\n');
        }
        sendColored(sender, sb.toString());
    }

    // ==================== 辅助 ====================

    private SoysApiKey requireKey(CommandSender sender, ApiKeyStore store, String[] args, int idx) {
        if (args.length <= idx || args[idx].trim().isEmpty()) {
            msgT(sender, "command.apikey.key-required", "§c请指定 <id|指纹>（/soyshttp apikey list 查看）");
            return null;
        }
        SoysApiKey k = store.findByIdOrFingerprint(args[idx].trim());
        if (k == null) {
            msgT(sender, "command.apikey.key-not-found", "§c未找到该 X-API-Key：{0}", args[idx]);
            return null;
        }
        return k;
    }

    private static String joinArgs(String[] args, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (i > from) sb.append(' ');
            sb.append(args[i]);
        }
        return sb.toString();
    }
}
