package com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.bridge.provider;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.bridge.spi.LoginProviderFactory;
import lombok.CustomLog;

import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.bridge.AuthLoginBridge;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.bridge.spi.LoginProvider;
import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.bridge.spi.LoginProviderContext;
import com.github.cocosoys.mc.soyshttpovermc.i18n.I18n;

import org.bukkit.configuration.ConfigurationSection;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import fr.xephi.authme.api.v3.AuthMeApi;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.events.LoginEvent;
import fr.xephi.authme.security.PasswordSecurity;
import fr.xephi.authme.security.crypts.HashedPassword;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/**
 * AuthMe 登录插件提供者（软依赖：仅在 AuthMe 已加载时由宿主实例化并注册到
 * {@link LoginProviderFactory}）。
 *
 * <p>覆盖 {@link LoginProvider} 两块能力：</p>
 * <ul>
 *   <li><b>纯账号密码校验</b>：{@link #verifyPassword} 走 AuthMe 底层纯数据库 hash 比对
 *       （反射 {@code AuthMe.database} → {@link DataSource#getAuth} +
 *       {@link PasswordSecurity#comparePassword(String, HashedPassword, String)} 三参版），
 *       不要求玩家在线、不依赖 AuthMeApi 单例；底层句柄在 {@link #init}（主线程）初始化；
 *       AuthMeApi.checkPassword 仅作兜底。</li>
 *   <li><b>玩家登录事件</b>：监听 {@link LoginEvent}，为该玩家签发会话令牌 + 一次性票据 + 发送网页登录链接；
 *       bridge 重建（/soyshttp reload）后经 {@link #bind} 重新绑定。</li>
 * </ul>
 */
@CustomLog
public class AuthMeLoginProvider implements LoginProvider, Listener {

    private LoginProviderContext context;
    private volatile boolean initialized;
    private volatile boolean shutdown;

    /** AuthMe 底层数据源（反射 AuthMe.database 字段）；null=未初始化/反射失败。 */
    private volatile DataSource authMeDataSource;
    /** AuthMe 密码比对器（反射 injector.getSingleton / AuthMeApi 单例字段）；null=不可用。 */
    private volatile PasswordSecurity authMePasswordSecurity;

    public AuthMeLoginProvider() {
    }

    // ===== LoginProvider =====

    @Override
    public String name() {
        return "authme";
    }

    @Override
    public String displayName() {
        return "AuthMe";
    }

    @Override
    public String description() {
        return I18n.t("provider.authme.description",
                "账号密码离线校验 + 玩家登录自动签发令牌 + 网页→游戏联动登录");
    }

    @Override
    public void reload(ConfigurationSection config) {
        if (config == null) {
            log.infoT("log.authme.no-config", "[AuthMeLoginProvider] 无专属配置，使用默认行为");
            return;
        }
        // 读取 gateway/providers/authme.yml 中的自定义配置项
        // 各配置项由本实现自行解析，不暴露给前端 API
        log.infoT("log.authme.config-loaded", "[AuthMeLoginProvider] 已加载专属配置: {0}", config.getKeys(false));
    }

    @Override
    public boolean isAvailable() {
        // 主线程调用（HTTP worker 线程 getPlugin 可能返回 null）
        return org.bukkit.Bukkit.getPluginManager().getPlugin("AuthMe") != null;
    }

    @Override
    public void init(LoginProviderContext ctx) {
        if (initialized) return; // 幂等（reload 重建 bridge 后再次调用不重复注册）
        initialized = true;
        this.context = ctx;
        Plugin host = ctx.getPlugin();
        host.getServer().getPluginManager().registerEvents(this, host);
        initAuthMeHandles();   // 主线程初始化底层句柄（worker 线程 getPlugin 可能返回 null）
        log.infoT("log.authme.connected", "登录插件 AuthMe 已接入（{0}）", description());
    }

    @Override
    public void shutdown() {
        shutdown = true;
    }

    @Override
    public boolean verifyPassword(String playerName, String password) {
        if (shutdown) return false;
        if (authMeDataSource != null && authMePasswordSecurity != null) {
            try {
                // AuthMe 玩家名统一小写存储
                String name = playerName.toLowerCase();
                PlayerAuth auth = authMeDataSource.getAuth(name);
                if (auth == null) {
                    log.warnT("log.authme.verify-account-missing", "AuthMe 离线校验：账号不存在或未注册: {0}", playerName);
                    return false;
                }
                HashedPassword hashed = auth.getPassword();
                if (hashed == null) {
                    log.warnT("log.authme.verify-no-password", "AuthMe 离线校验：账号无密码记录: {0}", playerName);
                    return false;
                }
                boolean ok = authMePasswordSecurity.comparePassword(password, hashed, name);
                if (!ok) {
                    log.warnT("log.authme.verify-bad-password", "AuthMe 离线校验：密码错误: {0}", playerName);
                }
                return ok;
            } catch (Throwable t) {
                log.warnT("log.authme.compare-fallback", "AuthMe 底层密码比对异常，回退 AuthMeApi: {0}", t);
            }
        }
        // 兜底：AuthMeApi.checkPassword（两参版同样为纯数据库比对，但依赖单例初始化，5.5.0-SNAPSHOT 可能为 null）
        try {
            AuthMeApi api = AuthMeApi.getInstance();
            if (api == null) {
                log.warnT("log.authme.api-null", "AuthMeApi.getInstance() 返回 null（AuthMe 单例未初始化），无法校验");
                return false;
            }
            return api.checkPassword(playerName, password);
        } catch (Throwable t) {
            log.warnT("log.authme.verify-exception", "AuthMe 密码校验异常: {0}", t);
            return false;
        }
    }

    // ===== 玩家登录事件：自动签发令牌 + 网页登录链接 =====

    @EventHandler
    public void onLogin(LoginEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        String name = player.getName();
        if (context == null || shutdown) return;

        AuthLoginBridge bridge = context.bridge();
        if (bridge == null) {
            log.warnT("log.authme.login-not-enabled", "AuthMe 登录事件到达但会话令牌颁发器未启用，跳过自动签发");
            return;
        }
        // 幂等绑定本提供者（bridge 重建后重新绑定；离线登录校验立即可用，不依赖本事件）
        bind(bridge);

        // 记录游戏端登录 IP（用于后续网页端 IP 匹配自动登录）
        String gameIp = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : null;
        if (gameIp != null) {
            bridge.recordGameLogin(name, gameIp);
        }

        // 玩家进游戏正常登录：先把他名下现存会话令牌升级为在线模式（离线 cookie 自动补全为在线语义），
        // 再签发在线令牌并生成一次性登录票据
        int upgraded = bridge.upgradePlayerToOnline(name);
        String token = bridge.issueToken(name);
//        String ticket = bridge.mintTicket(name);
//        String url = LinkMessageUtil.resolveUrl("/api/auth/login?ticket=" + ticket,
//                context.getMcHost(), context.getMcPort());
//        LinkMessageUtil.send(player, url, "&a[HTTP-Over-MC] 点击此处完成网页登录验证，获取访问令牌");
        if (upgraded > 0) {
            log.infoT("log.authme.login-upgraded", "玩家 {0} 进游戏登录：已将 {1} 个离线令牌升级为在线模式", name, upgraded);
        }
        log.infoT("log.authme.login-issued", "玩家 {0} 经 AuthMe 登录：已签发会话令牌并发送网页登录链接 (token={1}..., ip={2})", name,
                token.substring(0, Math.min(8, token.length())), gameIp);
    }

    // ===== 网页→游戏联动登录（PlayerJoinEvent / PlayerQuitEvent）=====

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (p == null || context == null) return;
        String name = p.getName();

        // 网页→游戏免登录：玩家在网页端已登录且 IP 匹配 → 强制 AuthMe 登录
        AuthLoginBridge bridge = context.bridge();
        if (bridge == null) return;
        String gameIp = p.getAddress() != null ? p.getAddress().getAddress().getHostAddress() : null;
        if (gameIp == null) return;
        if (bridge.isWebLoggedIn(name) && bridge.webIpMatches(name, gameIp)) {
            try {
                AuthMeApi api = AuthMeApi.getInstance();
                if (api != null && api.isRegistered(name)) {
                    api.forceLogin(p);
                    log.infoT("log.authme.web-ip-auto-login", "玩家 {0} 网页端已登录且 IP 匹配({1})，已强制 AuthMe 免登录", name, gameIp);
                } else {
                    log.infoT("log.authme.web-ip-auto-login-skip", "玩家 {0} 网页端已登录但未注册 AuthMe，跳过强制登录(ip={1})", name, gameIp);
                }
            } catch (Throwable t) {
                log.warnT("log.authme.web-ip-auto-login-fail", "玩家 {0} 网页→游戏免登录失败: {1}", name, t);
            }
        }
    }

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

    // ===== 底层句柄初始化（主线程）=====

    /**
     * 反射初始化 AuthMe 底层校验句柄（一次性，主线程）：
     * DataSource ← {@code AuthMe.database} 字段（5.4.0/5.5.0 一致，失败按类型扫描兜底）；
     * PasswordSecurity ← {@code AuthMe.injector.getSingleton(PasswordSecurity.class)}
     * （5.5.0 relocated 的 ch.jalu.injector，反射调用），兜底 AuthMeApi 单例字段。
     */
    private void initAuthMeHandles() {
        try {
            Plugin authme = org.bukkit.Bukkit.getPluginManager().getPlugin("AuthMe");
            if (authme == null) {
                log.warnT("log.authme.handle-plugin-null", "AuthMe 底层句柄初始化：getPlugin(\"AuthMe\") 返回 null（thread={0}），将回退 AuthMeApi 兜底校验",
                        Thread.currentThread().getName());
                return;
            }
            Class<?> cls = authme.getClass();

            // 1) DataSource：AuthMe.database（5.4.0/5.5.0 字段名一致）
            Object ds = getFieldValue(cls, authme, "database");
            if (ds instanceof DataSource) {
                authMeDataSource = (DataSource) ds;
            }
            if (authMeDataSource == null) {
                authMeDataSource = findFieldByType(cls, authme, DataSource.class);
            }

            // 2) PasswordSecurity：injector.getSingleton(PasswordSecurity.class)（5.5.0 relocated 包名，反射）
            Object injector = getFieldValue(cls, authme, "injector");
            if (injector != null) {
                try {
                    Method getSingleton = findMethodByName(injector.getClass(), "getSingleton");
                    if (getSingleton != null) {
                        Object ps = getSingleton.invoke(injector, PasswordSecurity.class);
                        if (ps instanceof PasswordSecurity) {
                            authMePasswordSecurity = (PasswordSecurity) ps;
                        }
                    }
                } catch (Throwable t) {
                    log.infoT("log.authme.injector-ps-fail", "AuthMe injector 获取 PasswordSecurity 失败: {0}", t);
                }
            }

            // 3) 兜底：AuthMeApi 单例的 passwordSecurity 字段
            if (authMePasswordSecurity == null) {
                try {
                    AuthMeApi api = AuthMeApi.getInstance();
                    if (api != null) {
                        Field f = AuthMeApi.class.getDeclaredField("passwordSecurity");
                        f.setAccessible(true);
                        Object ps = f.get(api);
                        if (ps instanceof PasswordSecurity) {
                            authMePasswordSecurity = (PasswordSecurity) ps;
                        }
                    }
                } catch (Throwable t) {
                    log.infoT("log.authme.api-ps-fail", "AuthMeApi 单例字段获取 PasswordSecurity 失败: {0}", t);
                }
            }

            log.infoT("log.authme.handle-status", "AuthMe 底层校验句柄: dataSource={0} passwordSecurity={1}（离线纯数据库密码校验{2}）",
                    authMeDataSource != null, authMePasswordSecurity != null,
                    authMeDataSource != null && authMePasswordSecurity != null
                            ? I18n.t("provider.authme.status-enabled", "已启用")
                            : I18n.t("provider.authme.status-fallback", "不可用，将回退 AuthMeApi"));
        } catch (Throwable t) {
            log.warnT("log.authme.init-fail", "AuthMe 底层句柄初始化失败: {0}", t);
        }
    }

    // ===== 反射工具 =====

    private static Object getFieldValue(Class<?> cls, Object target, String name) {
        try {
            Field f = cls.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(target);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 按字段类型扫描实例上的字段（结构变化兜底）。 */
    private static <T> T findFieldByType(Class<?> cls, Object target, Class<T> type) {
        Class<?> c = cls;
        while (c != null) {
            for (Field f : c.getDeclaredFields()) {
                try {
                    if (type.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        Object v = f.get(target);
                        if (type.isInstance(v)) {
                            return type.cast(v);
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
            c = c.getSuperclass();
        }
        return null;
    }

    /** 沿继承链查找形如 {@code <T> T getSingleton(Class<T>)} 的方法。 */
    private static Method findMethodByName(Class<?> cls, String name) {
        Class<?> c = cls;
        while (c != null) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == 1
                        && m.getParameterTypes()[0] == Class.class) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {
                    }
                    return m;
                }
            }
            c = c.getSuperclass();
        }
        return null;
    }

    /** 沿类继承链按方法名 + 参数个数查找方法（含父类 protected；兼容泛型擦除签名）。 */
    private static Method findMethodByArity(Class<?> clazz, String name, int arity) {
        Class<?> c = clazz;
        while (c != null) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == arity) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {
                    }
                    return m;
                }
            }
            c = c.getSuperclass();
        }
        return null;
    }
}
