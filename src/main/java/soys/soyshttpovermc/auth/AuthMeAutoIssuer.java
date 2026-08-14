package soys.soyshttpovermc.auth;

import soys.soyshttpovermc.HttpOverMcPlugin;
import soys.soyshttpovermc.log.LogKit;
import soys.soyshttpovermc.util.LinkMessageUtil;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import fr.xephi.authme.api.v3.AuthMeApi;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.events.LoginEvent;
import fr.xephi.authme.security.PasswordSecurity;
import fr.xephi.authme.security.crypts.HashedPassword;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * AuthMe 软依赖接入器：
 * <ul>
 *   <li>仅在 AuthMe 插件已加载时由 {@link HttpOverMcPlugin} 实例化，故本类引用 AuthMe 类不会在无 AuthMe 时加载；</li>
 *   <li>监听 {@link LoginEvent}（玩家经 AuthMe 登录成功），为该玩家签发会话令牌并登记到 {@link AuthLoginBridge}；</li>
 *   <li>生成一次性登录票据，向玩家发送可点击链接（/auth/login?ticket=...）；</li>
 *   <li>向 {@link AuthLoginBridge} 注入密码校验器：<b>优先走 AuthMe 底层纯数据库 hash 比对</b>
 *       （反射 {@code AuthMe.database} → {@link DataSource#getAuth} + {@link PasswordSecurity#comparePassword}），
 *       只验证"账号存在 + 密码 hash 匹配"，<b>不要求玩家在线、不触发任何在线状态检查</b>；
 *       底层句柄不可用（AuthMe 内部结构变化）时回退 {@code AuthMeApi.checkPassword}（两参版经
 *       {@code dataSource.getPassword} 中转，同样为纯数据库比对，但依赖单例初始化，5.5.0-SNAPSHOT 可能为 null）。</li>
 * </ul>
 * 本类<b>不持有</b> bridge/issuer 引用：每次登录从 {@link HttpOverMcPlugin#getAuthLoginBridge()} 动态获取
 * 当前 bridge（含 `/soyshttp reload` 重建后的新实例），并幂等注入密码校验器，避免热重载后实例错位。
 */
public class AuthMeAutoIssuer implements Listener {

    private final HttpOverMcPlugin plugin;

    /** AuthMe 底层数据源（反射 AuthMe.database 字段）；null=未初始化/反射失败。 */
    private volatile DataSource authMeDataSource;
    /** AuthMe 密码比对器（反射 injector.getSingleton / AuthMeApi 单例字段）；null=不可用。 */
    private volatile PasswordSecurity authMePasswordSecurity;
    /** 底层句柄是否已尝试初始化（避免每个请求重复反射）。 */
    private boolean handlesInitialized;

    public AuthMeAutoIssuer(HttpOverMcPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        // 主线程立即初始化底层校验句柄：与 AuthMeBotIntegrator.init 同环境。
        // 不能等 HTTP worker 线程懒加载——Bukkit.getPluginManager().getPlugin() 在非主线程
        // 可能返回 null（插件列表遍历环境受限），导致静默回退 AuthMeApi 兜底路径。
        initAuthMeHandles();
        LogKit.info("[HTTP-Over-MC] AuthMe 接入已启用：玩家登录将自动签发会话令牌并发送网页登录链接");
    }

    /**
     * 把密码校验器绑定到指定登录桥（幂等）。<b>必须在 bridge 创建/重建后立即调用</b>——
     * 离线网页登录不依赖真实玩家的 {@link LoginEvent}，若等 onLogin 才注入，
     * 服务器从未有真实玩家登录时校验器为 null，所有 /api/auth/login 都会误报"账号或密码错误"。
     */
    public void bindTo(AuthLoginBridge bridge) {
        if (bridge != null) {
            bridge.setPasswordVerifier(this::checkPassword);
        }
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
        // 幂等绑定密码校验器（底层离线比对优先；bridge 重建后重新绑定）
        bindTo(bridge);

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

    /**
     * AuthMe 密码校验（仅在本类被实例化时 AuthMe 才存在，故可硬引用）。
     * <p>只做"账号 + 密码 hash"校验，玩家是否在线无关：
     * <ol>
     *   <li>反射拿 {@code AuthMe.database}（DataSource）与 PasswordSecurity，走
     *       {@code getAuth(name).getPassword()} + {@code comparePassword(password, hashed, name)} 纯数据库比对；</li>
     *   <li>底层句柄不可用时回退 {@code AuthMeApi.checkPassword}（两参版，同样纯数据库比对，但依赖单例初始化）。</li>
     * </ol>
     */
    private boolean checkPassword(String playerName, String password) {
        initAuthMeHandles();
        if (authMeDataSource != null && authMePasswordSecurity != null) {
            try {
                // AuthMe 玩家名统一小写存储
                String name = playerName.toLowerCase();
                PlayerAuth auth = authMeDataSource.getAuth(name);
                if (auth == null) {
                    LogKit.warn("[HTTP-Over-MC] AuthMe 离线校验：账号不存在或未注册: " + playerName);
                    return false;
                }
                HashedPassword hashed = auth.getPassword();
                if (hashed == null) {
                    LogKit.warn("[HTTP-Over-MC] AuthMe 离线校验：账号无密码记录: " + playerName);
                    return false;
                }
                boolean ok = authMePasswordSecurity.comparePassword(password, hashed, name);
                if (!ok) {
                    LogKit.warn("[HTTP-Over-MC] AuthMe 离线校验：密码错误: " + playerName);
                }
                return ok;
            } catch (Throwable t) {
                LogKit.warn("[HTTP-Over-MC] AuthMe 底层密码比对异常，回退 AuthMeApi: " + t, t);
            }
        }
        try {
            AuthMeApi api = AuthMeApi.getInstance();
            if (api == null) {
                LogKit.warn("[HTTP-Over-MC] AuthMeApi.getInstance() 返回 null（AuthMe 单例未初始化），无法校验");
                return false;
            }
            return api.checkPassword(playerName, password);
        } catch (Throwable t) {
            LogKit.warn("[HTTP-Over-MC] AuthMe 密码校验异常: " + t, t);
            return false;
        }
    }

    /**
     * 反射初始化 AuthMe 底层校验句柄（一次性）：
     * <ul>
     *   <li>DataSource：{@code AuthMe.database} 字段（5.4.0 / 5.5.0 一致），失败按类型扫描兜底；</li>
     *   <li>PasswordSecurity：优先 {@code AuthMe.injector.getSingleton(PasswordSecurity.class)}
     *       （5.5.0 relocated 的 ch.jalu.injector，反射调用）；其次 {@code AuthMeApi} 单例的
     *       {@code passwordSecurity} 字段（单例可能为 null）。</li>
     * </ul>
     */
    private void initAuthMeHandles() {
        if (handlesInitialized) return;
        synchronized (this) {
            if (handlesInitialized) return;
            handlesInitialized = true;
            try {
                Plugin p = plugin.getServer().getPluginManager().getPlugin("AuthMe");
                if (p == null) {
                    LogKit.warn("[HTTP-Over-MC] AuthMe 底层句柄初始化：getPlugin(\"AuthMe\") 返回 null"
                            + "（thread=" + Thread.currentThread().getName()
                            + "），将回退 AuthMeApi 兜底校验");
                    return;
                }
                Class<?> cls = p.getClass();

                // 1) DataSource：AuthMe.database（5.4.0/5.5.0 字段名一致）
                Object ds = getFieldValue(cls, p, "database");
                if (ds instanceof DataSource) {
                    authMeDataSource = (DataSource) ds;
                }
                if (authMeDataSource == null) {
                    authMeDataSource = findFieldByType(cls, p, DataSource.class);
                }

                // 2) PasswordSecurity：injector.getSingleton(PasswordSecurity.class)（5.5.0 relocated 包名，反射）
                Object injector = getFieldValue(cls, p, "injector");
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
                        LogKit.info("[HTTP-Over-MC] AuthMe injector 获取 PasswordSecurity 失败: " + t);
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
                        LogKit.info("[HTTP-Over-MC] AuthMeApi 单例字段获取 PasswordSecurity 失败: " + t);
                    }
                }

                LogKit.info("[HTTP-Over-MC] AuthMe 底层校验句柄: dataSource=" + (authMeDataSource != null)
                        + " passwordSecurity=" + (authMePasswordSecurity != null)
                        + "（离线纯数据库密码校验" + (authMeDataSource != null && authMePasswordSecurity != null
                            ? "已启用" : "不可用，将回退 AuthMeApi") + "）");
            } catch (Throwable t) {
                LogKit.warn("[HTTP-Over-MC] AuthMe 底层句柄初始化失败: " + t, t);
            }
        }
    }

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
}
