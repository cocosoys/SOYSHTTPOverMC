package com.github.cocosoys.mc.soyshttpovermc.bot;
import lombok.CustomLog;

import com.github.cocosoys.mc.soyshttpovermc.i18n.I18n;
import com.github.cocosoys.mc.soyshttpovermc.enums.BotHideMode;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;

import java.net.InetAddress;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Bot 专属账号守卫：
 * <ul>
 *   <li><b>登录 IP 白名单</b>（{@code bot.allowed-login-ips}）：bot 专属账号（受管 Bot 名，
 *       或以 {@code bot.name-prefix} 前缀开头的任意名称）只能从白名单 IP 登录；其它 IP 登录
 *       一律在 {@link PlayerLoginEvent} 阶段拒绝，提示「该账号为 bot 专属账号」——
 *       防止 Bot 被特殊方式强制踢出后，有人抢注其名称登录顶替隧道。</li>
 *   <li><b>进服隐藏</b>（{@code bot.hide-mode}）：Bot 进服时对每个在线真实玩家
 *       {@code hidePlayer(bot)}；真实玩家进服时若 Bot 在线同样对其隐藏。
 *       预留 {@code playerinfo-remove} 模式（发 PacketPlayOutPlayerInfo(REMOVE_PLAYER) 给所有客户端），
 *       当前为占位空函数，切换配置即切换入口。</li>
 * </ul>
 */
@CustomLog
public class BotGuardian implements Listener {

    private final BotManager botManager;
    private final String namePrefix;
    private final Set<String> allowedIps;
    private final BotHideMode hideMode;

    public BotGuardian(BotManager botManager, String namePrefix, Set<String> allowedIps, String hideMode) {
        this.botManager = botManager;
        this.namePrefix = namePrefix == null ? "" : namePrefix;
        this.allowedIps = allowedIps == null ? Collections.emptySet() : new HashSet<>(allowedIps);
        this.hideMode = BotHideMode.from(hideMode);
    }

    /** 是否 bot 专属账号名：受管 Bot 名，或以配置前缀开头的任意名称。 */
    public boolean isBotDedicatedName(String name) {
        if (name == null || name.isEmpty()) return false;
        if (botManager != null && botManager.isManagedBot(name)) return true;
        return !namePrefix.isEmpty() && name.startsWith(namePrefix);
    }

    /** 该 IP 是否在白名单（含回环等价：127.0.0.1 / ::1 / localhost 互通匹配）。 */
    private boolean ipAllowed(String hostAddress) {
        if (hostAddress == null || hostAddress.isEmpty()) return false;
        for (String ip : allowedIps) {
            String a = ip == null ? "" : ip.trim();
            if (a.isEmpty()) continue;
            if (a.equalsIgnoreCase(hostAddress)) return true;
            // 回环等价：白名单写 127.0.0.1 时，IPv6 回环 ::1 也算放行（反之亦然）
            if (isLoopback(a) && isLoopback(hostAddress)) return true;
        }
        return false;
    }

    private static boolean isLoopback(String ip) {
        return "127.0.0.1".equals(ip) || "::1".equals(ip) || "localhost".equalsIgnoreCase(ip)
                || "0:0:0:0:0:0:0:1".equals(ip);
    }

    /** 登录阶段拦截：bot 专属账号名 + 非白名单 IP → 拒绝并提示。 */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onLogin(PlayerLoginEvent e) {
        try {
            String name = e.getPlayer().getName();
            if (!isBotDedicatedName(name)) {
                return;
            }
            InetAddress addr = e.getAddress();
            String ip = addr == null ? "" : addr.getHostAddress();
            if (ipAllowed(ip)) {
                log.infoT("log.bot.login-allowed", "Bot 专属账号登录放行: {0} ip={1}", name, ip);
                return;
            }
            log.warnT("log.bot.login-blocked", "拦截 bot 专属账号登录（IP 不在白名单）: name={0} ip={1} 白名单={2}", name, ip, allowedIps);
            e.disallow(PlayerLoginEvent.Result.KICK_OTHER,
                    I18n.t("exception.bot.login-forbidden", "该账号为 bot 专属账号，禁止登录（来源 IP 不在白名单）"));
        } catch (Throwable t) {
            log.warnT("log.bot.onlogin-error", "BotGuardian.onLogin 异常: {0}", t);
        }
    }

    /** 进服隐藏：Bot 进服 → 对每个在线真实玩家隐藏；真实玩家进服 → 对每个在线 Bot 隐藏。 */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        try {
            Player joined = e.getPlayer();
            Set<String> botNames = botManager == null ? Collections.emptySet() : botManager.getBotNames();
            boolean joinedIsBot = botNames.contains(joined.getName());
            for (Player online : joined.getServer().getOnlinePlayers()) {
                if (online == joined) continue;
                boolean onlineIsBot = botNames.contains(online.getName());
                if (joinedIsBot && !onlineIsBot) {
                    hide(online, joined);          // Bot 进服 → 对真实玩家隐藏 Bot
                } else if (!joinedIsBot && onlineIsBot) {
                    hide(joined, online);          // 真实玩家进服 → 对其隐藏已在线 Bot
                }
            }
        } catch (Throwable t) {
            log.warnT("log.bot.onjoin-error", "BotGuardian.onJoin 异常: {0}", t);
        }
    }

    /** 按 hide-mode 执行隐藏。 */
    private void hide(Player viewer, Player target) {
        try {
            if (hideMode == BotHideMode.PLAYERINFO_REMOVE) {
                sendPlayerInfoRemove(target);
                return;
            }
            viewer.hidePlayer(target);
        } catch (Throwable t) {
            log.warnT("log.bot.hide-fail", "隐藏 Bot 失败 viewer={0} target={1}: {2}", viewer.getName(), target.getName(), t);
        }
    }

    /**
     * 预留：向所有客户端发送 {@code PacketPlayOutPlayerInfo(REMOVE_PLAYER)} 把 Bot 从玩家列表移除。
     * 未来实现（需 NMS 反射，1.12.2 为 {@code net.minecraft.server.v1_12_R1.PacketPlayOutPlayerInfo}）；
     * 当前为占位空函数，配置 {@code bot.hide-mode: playerinfo-remove} 后仅打日志，不生效。
     */
    private void sendPlayerInfoRemove(Player bot) {
        log.infoT("log.bot.playerinfo-remove-reserved", "[预留] hide-mode=playerinfo-remove：未来以 PacketPlayOutPlayerInfo(REMOVE_PLAYER) 向所有客户端移除 Bot {0}（当前为空实现）", bot.getName());
    }
}
