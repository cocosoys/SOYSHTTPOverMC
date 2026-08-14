package soys.soyshttpovermc.auth;

import soys.soyshttpovermc.HttpOverMcPlugin;
import soys.soyshttpovermc.log.LogKit;
import soys.soyshttpovermc.util.LinkMessageUtil;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import fr.xephi.authme.api.v3.AuthMeApi;
import fr.xephi.authme.events.LoginEvent;

/**
 * AuthMe 软依赖接入器：
 * <ul>
 *   <li>仅在 AuthMe 插件已加载时由 {@link HttpOverMcPlugin} 实例化，故本类引用 AuthMe 类不会在无 AuthMe 时加载；</li>
 *   <li>监听 {@link LoginEvent}（玩家经 AuthMe 登录成功），为该玩家签发会话令牌并登记到 {@link AuthLoginBridge}；</li>
 *   <li>生成一次性登录票据，向玩家发送可点击链接（/auth/login?ticket=...）；</li>
 *   <li>向 {@link AuthLoginBridge} 注入密码校验器（AuthMeApi.checkPassword），由浏览器二次验证密码后才下发 Cookie。</li>
 * </ul>
 * 本类<b>不持有</b> bridge/issuer 引用：每次登录从 {@link HttpOverMcPlugin#getAuthLoginBridge()} 动态获取
 * 当前 bridge（含 `/soyshttp reload` 重建后的新实例），并幂等注入密码校验器，避免热重载后实例错位。
 */
public class AuthMeAutoIssuer implements Listener {

    private final HttpOverMcPlugin plugin;

    public AuthMeAutoIssuer(HttpOverMcPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        LogKit.info("[HTTP-Over-MC] AuthMe 接入已启用：玩家登录将自动签发会话令牌并发送网页登录链接");
    }

    @EventHandler
    public void onLogin(LoginEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        String name = player.getName();

        AuthLoginBridge bridge = plugin.getAuthLoginBridge();
        if (bridge == null) {
            LogKit.warn("[HTTP-Over-MC] AuthMe 登录事件到达但会话令牌颁发器未启用，跳过自动签发");
            return;
        }
        // 幂等注入密码校验器（AuthMeApi.checkPassword；bridge 重建后需要重新注入）
        bridge.setPasswordVerifier(this::checkPassword);

        // 玩家进游戏正常登录：先把他名下现存会话令牌升级为在线模式（离线 cookie 自动补全为在线语义），
        // 再签发在线令牌（同一 token 可作 X-API-Key / Bearer / Cookie）并生成一次性登录票据
        int upgraded = bridge.upgradePlayerToOnline(name);
        String token = bridge.issueToken(name);
        String ticket = bridge.mintTicket(name);
        String url = LinkMessageUtil.resolveUrl("/auth/login?ticket=" + ticket, plugin.getMcHost(), plugin.getMcPort());
        LinkMessageUtil.send(player, url, "&a[HTTP-Over-MC] 点击此处完成网页登录验证，获取访问令牌");
        if (upgraded > 0) {
            LogKit.info("[HTTP-Over-MC] 玩家 " + name + " 进游戏登录：已将 " + upgraded + " 个离线令牌升级为在线模式");
        }

        LogKit.info("[HTTP-Over-MC] 玩家 " + name + " 经 AuthMe 登录：已签发会话令牌并发送网页登录链接 (token="
                + token.substring(0, Math.min(8, token.length())) + "...)");
    }

    /** AuthMe 密码校验（仅在本类被实例化时 AuthMe 才存在，故可硬引用）。 */
    private boolean checkPassword(String playerName, String password) {
        try {
            return AuthMeApi.getInstance().checkPassword(playerName, password);
        } catch (Throwable t) {
            LogKit.warn("[HTTP-Over-MC] AuthMe 密码校验异常: " + t, t);
            return false;
        }
    }
}
