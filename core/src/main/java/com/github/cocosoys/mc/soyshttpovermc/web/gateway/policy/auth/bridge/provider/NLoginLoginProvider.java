package com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.bridge.provider;

import com.github.cocosoys.mc.soyshttpovermc.i18n.I18n;
import com.github.cocosoys.mc.soyshttpovermc.util.LinkMessageUtil;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.bridge.AuthLoginBridge;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.bridge.spi.LoginProvider;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.bridge.spi.LoginProviderContext;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.bridge.spi.LoginProviderFactory;
import com.nickuc.openlogin.bukkit.api.events.AsyncAuthenticateEvent;
import com.nickuc.openlogin.common.api.OpenLoginAPI;
import lombok.CustomLog;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

/**
 * nLogin / OpeNLogin 登录插件提供者（软依赖：仅在 OpeNLogin 已加载时由宿主实例化并注册到
 * {@link LoginProviderFactory}）。
 *
 * <p>覆盖 {@link LoginProvider} 两块能力：</p>
 * <ul>
 *   <li><b>纯账号密码校验</b>：{@link #verifyPassword} 走 OpeNLogin 官方 API
 *       {@code OpenLoginAPI.getApi().comparePassword(player, password)}——纯数据库比对，
 *       不要求玩家在线；{@code isRegistered} 前置判定账号存在。</li>
 *   <li><b>玩家登录事件</b>：监听 {@link AsyncAuthenticateEvent}（玩家在游戏内完成
 *       {@code /login} 认证成功时触发），为该玩家签发会话令牌 + 一次性票据 + 发送网页登录链接；
 *       bridge 重建（/soyshttp reload）后经 {@link #bind} 重新绑定。</li>
 * </ul>
 *
 * <p><b>已知限制</b>：OpeNLogin 官方 API 未提供 forceLogin 类强制登录能力
 * （{@code OLBukkitAPI} 仅有 getAccount/comparePassword/isRegistered/update），
 * 因此"网页→游戏免登录"（AuthMe 的 PlayerJoinEvent 强制登录）在本提供者不可用；
 * 玩家进服后在游戏内完成认证即可触发本提供者的签发链路。旧版 nLogin
 * （vexsoftware，包名/API 全不同、已停更）不兼容，本提供者仅识别 OpeNLogin。</p>
 */
@CustomLog
public class NLoginLoginProvider implements LoginProvider, Listener {

    private LoginProviderContext context;
    private volatile boolean initialized;
    private volatile boolean shutdown;

    public NLoginLoginProvider() {
    }

    // ===== LoginProvider =====

    @Override
    public String name() {
        return "nlogin";
    }

    @Override
    public String displayName() {
        return "nLogin / OpeNLogin";
    }

    @Override
    public String description() {
        return I18n.t("provider.nlogin.description",
                "账号密码离线校验 + 玩家登录自动签发令牌 + 网页登录链接");
    }

    @Override
    public void reload(ConfigurationSection config) {
        if (config == null) {
            log.infoT("log.nlogin.no-config", "[NLoginLoginProvider] 无专属配置，使用默认行为");
            return;
        }
        // 读取 gateway/providers/nlogin.yml 中的自定义配置项
        // 各配置项由本实现自行解析，不暴露给前端 API
        log.infoT("log.nlogin.config-loaded", "[NLoginLoginProvider] 已加载专属配置: {0}", config.getKeys(false));
    }

    @Override
    public boolean isAvailable() {
        // 主线程调用（HTTP worker 线程 getPlugin 可能返回 null）；旧版 nLogin 插件名也一并识别
        return org.bukkit.Bukkit.getPluginManager().getPlugin("OpeNLogin") != null
                || org.bukkit.Bukkit.getPluginManager().getPlugin("nLogin") != null;
    }

    @Override
    public void init(LoginProviderContext ctx) {
        if (initialized) return; // 幂等（reload 重建 bridge 后再次调用不重复注册）
        initialized = true;
        this.context = ctx;
        Plugin host = ctx.getPlugin();
        host.getServer().getPluginManager().registerEvents(this, host);
        log.infoT("log.nlogin.connected", "登录插件 nLogin / OpeNLogin 已接入（{0}）", description());
    }

    @Override
    public void shutdown() {
        shutdown = true;
    }

    @Override
    public boolean verifyPassword(String playerName, String password) {
        if (shutdown) return false;
        try {
            OpenLoginAPI api = OpenLoginAPI.getApi();
            if (api == null) {
                log.warnT("log.nlogin.api-null", "OpeNLogin API 未初始化（getApi()=null），无法校验");
                return false;
            }
            if (!api.isRegistered(playerName)) {
                log.warnT("log.nlogin.verify-account-missing", "OpeNLogin 离线校验：账号不存在或未注册: {0}", playerName);
                return false;
            }
            boolean ok = api.comparePassword(playerName, password);
            if (!ok) {
                log.warnT("log.nlogin.verify-bad-password", "OpeNLogin 离线校验：密码错误: {0}", playerName);
            }
            return ok;
        } catch (Throwable t) {
            log.warnT("log.nlogin.verify-exception", "OpeNLogin 密码校验异常: {0}", t);
            return false;
        }
    }

    // ===== 玩家登录事件：自动签发令牌 + 网页登录链接 =====

    @EventHandler
    public void onAuthenticate(AsyncAuthenticateEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        String name = player.getName();
        if (context == null || shutdown) return;

        AuthLoginBridge bridge = context.bridge();
        if (bridge == null) {
            log.warnT("log.nlogin.login-not-enabled", "OpeNLogin 登录事件到达但会话令牌颁发器未启用，跳过自动签发");
            return;
        }
        // 幂等绑定本提供者（bridge 重建后重新绑定；离线登录校验立即可用，不依赖本事件）
        bind(bridge);

        // 记录游戏端登录 IP（用于后续网页端 IP 匹配自动登录）
        String gameIp = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : null;
        if (gameIp != null) {
            bridge.recordGameLogin(name, gameIp);
        }

        // 玩家在游戏内认证成功：先把他名下现存会话令牌升级为在线模式，
        // 再签发在线令牌并生成一次性登录票据
        int upgraded = bridge.upgradePlayerToOnline(name);
        String token = bridge.issueToken(name);
        // 游戏端→网页端绑定票据（auto.login.ticket.in-game-link，默认 true）：发送可点击的网页登录链接。
        // 票据 TTL=auto.login.ticket.ttl（默认 60s）、一次性（使用即销毁，防重放）；该链接同时是
        // 设备绑定通道（前端可凭 ticket 提交设备指纹完成绑定，实现 Cookie+指纹双因子免登录）。
        if (bridge.isTicketLinkEnabled()) {
            try {
                String ticket = bridge.mintTicket(name);
                String url = LinkMessageUtil.resolveUrl("/api/auth/login?ticket=" + ticket,
                        context.getMcHost(), context.getMcPort());
                LinkMessageUtil.send(player, url, "&a[HTTP-Over-MC] 点击此处完成网页登录验证，获取访问令牌");
            } catch (Throwable t) {
                log.warnT("log.nlogin.ticket-send-fail", "发送网页登录链接失败: {0}", t);
            }
        }
        if (upgraded > 0) {
            log.infoT("log.nlogin.login-upgraded", "玩家 {0} 进游戏登录：已将 {1} 个离线令牌升级为在线模式", name, upgraded);
        }
        log.infoT("log.nlogin.login-issued", "玩家 {0} 经 OpeNLogin 登录：已签发会话令牌并发送网页登录链接 (token={1}..., ip={2})", name,
                token.substring(0, Math.min(8, token.length())), gameIp);
    }

    // ===== 退出清理 =====

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        String name = event.getPlayer().getName();
        if (name != null && context != null) {
            // 清除游戏端登录记录（玩家退出后不再用于网页端自动登录）
            AuthLoginBridge bridge = context.bridge();
            if (bridge != null) {
                bridge.clearGameLogin(name);
            }
        }
    }
}
