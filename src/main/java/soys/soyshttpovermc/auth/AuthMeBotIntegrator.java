package soys.soyshttpovermc.auth;

import soys.soyshttpovermc.HttpOverMcPlugin;
import soys.soyshttpovermc.bot.BotAuthIntegrator;
import soys.soyshttpovermc.log.LogKit;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/**
 * AuthMe 免登录集成器（软依赖，仅在 AuthMe 已加载时由 {@link HttpOverMcPlugin} 实例化）：
 * 隧道 Bot 作为普通玩家进服会被 AuthMe 拦下（30s 登录超时踢出），本集成器在受管 Bot 进服时
 * 把 Bot 名<b>运行时注入 AuthMe 的免登录名单（UNRESTRICTED_NAMES）内存</b>，Bot 退出时同步移除——
 * <b>不写入 AuthMe 的 config.yml</b>（纯内存热装填，随 Bot 生命周期自动装填/移除）。
 *
 * <p>实现方式（纯内存，不写配置文件）：AuthMe 5.x 的免登录名单在 configme（relocated）中，
 * 无公开内存 API，故通过反射：{@code AuthMe.settings}（私有字段）→
 * {@code SettingsManagerImpl.getConfigurationData()}（protected）→
 * {@code ConfigurationData.setValue(RestrictionSettings.UNRESTRICTED_NAMES, Set)}——
 * 仅改内存值，AuthMe 的 {@code settings.getProperty(UNRESTRICTED_NAMES)} 判定立即生效，不落盘。
 * 若反射链路任一环失败（版本变更），回退：直接调用 forceLogin（对未注册玩家无效，会打告警）。
 */
public class AuthMeBotIntegrator extends BotAuthIntegrator implements Listener {

    private final HttpOverMcPlugin plugin;

    /** ConfigurationData 实例（relocated configme，反射持有） */
    private volatile Object configData;
    /** RestrictionSettings.UNRESTRICTED_NAMES 属性（Property<Set<String>>） */
    private volatile Object unrestrictedProperty;
    private volatile boolean ready = false;

    public AuthMeBotIntegrator(HttpOverMcPlugin plugin) {
        this.plugin = plugin;
        init();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        LogKit.info("[HTTP-Over-MC] AuthMe Bot 免登录集成已启用（内存热装填 UNRESTRICTED_NAMES，不写 AuthMe 配置）");
    }

    @Override
    public String name() {
        return "authme";
    }

    /** 初始化反射通道：AuthMe.settings 字段 → getConfigurationData() → UNRESTRICTED_NAMES 属性。 */
    private void init() {
        try {
            Plugin authme = plugin.getServer().getPluginManager().getPlugin("AuthMe");
            if (authme == null) return;
            Field settingsField = authme.getClass().getDeclaredField("settings");
            settingsField.setAccessible(true);
            Object settings = settingsField.get(authme); // fr.xephi.authme.settings.Settings

            Method getCfg = findMethodByArity(settings.getClass(), "getConfigurationData", 0);
            Object cfgData = getCfg.invoke(settings); // ConfigurationData

            Class<?> restrictionSettings = Class.forName("fr.xephi.authme.settings.properties.RestrictionSettings");
            Field propField = restrictionSettings.getDeclaredField("UNRESTRICTED_NAMES");
            Object property = propField.get(null); // Property<Set<String>>

            this.configData = cfgData;
            this.unrestrictedProperty = property;
            this.ready = true;
            LogKit.info("[HTTP-Over-MC] AuthMe 免登录内存通道就绪（UNRESTRICTED_NAMES 反射可用）");
        } catch (Throwable t) {
            this.ready = false;
            LogKit.warn("[HTTP-Over-MC] AuthMe 免登录内存通道初始化失败，回退 forceLogin（未注册 Bot 可能仍被踢）: " + t);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (p == null || !isManaged(p.getName())) return;
        if (mutateUnrestricted(true, p.getName())) {
            LogKit.info("[HTTP-Over-MC] AuthMe 免登录名单已热装填: " + p.getName());
        } else {
            // 反射不可用时的兜底：forceLogin（未注册玩家 AuthMe 可能不买账）
            try {
                Class<?> api = Class.forName("fr.xephi.authme.api.v3.AuthMeApi");
                Method forceLogin = api.getMethod("forceLogin", Player.class);
                forceLogin.invoke(api.getMethod("getInstance").invoke(null), p);
                LogKit.warn("[HTTP-Over-MC] 已用 forceLogin 兜底（内存名单不可用）: " + p.getName());
            } catch (Throwable t) {
                LogKit.warn("[HTTP-Over-MC] forceLogin 兜底也失败: " + t);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        String name = event.getPlayer().getName();
        if (name != null && isManaged(name) && mutateUnrestricted(false, name)) {
            LogKit.info("[HTTP-Over-MC] AuthMe 免登录名单已移除: " + name);
        }
    }

    /** 插件卸载时清理：移除全部受管 Bot 名（AuthMe 内存名单恢复干净）。 */
    public void unregisterAll() {
        if (!ready || plugin.getBotManager() == null) return;
        for (String name : plugin.getBotManager().getBotNames()) {
            mutateUnrestricted(false, name);
        }
        LogKit.info("[HTTP-Over-MC] AuthMe 免登录名单已全部清理（unregisterAll）");
    }

    /**
     * 纯内存增删 AuthMe UNRESTRICTED_NAMES：
     * ConfigurationData.getValue(property) 取当前 Set → 拷贝修改 → setValue(property, 新Set)。
     */
    private boolean mutateUnrestricted(boolean add, String name) {
        if (!ready || name == null) return false;
        try {
            Object cfg = configData;
            Object prop = unrestrictedProperty;
            Method getValue = findMethodByArity(cfg.getClass(), "getValue", 1);
            if (getValue == null) return false;
            Object current = getValue.invoke(cfg, prop);
            @SuppressWarnings("unchecked")
            Set<String> set = current == null ? new HashSet<String>() : new HashSet<String>((Set<String>) current);
            boolean changed = add ? set.add(name) : set.remove(name);
            if (!changed) return false;
            Method setValue = findMethodByArity(cfg.getClass(), "setValue", 2);
            if (setValue == null) return false;
            setValue.invoke(cfg, prop, set);
            return true;
        } catch (Throwable t) {
            LogKit.warn("[HTTP-Over-MC] AuthMe 免登录名单热装填失败: " + t);
            return false;
        }
    }

    /** 沿类继承链按方法名 + 参数个数查找方法（含父类 protected；兼容泛型擦除签名）。 */
    private static Method findMethodByArity(Class<?> clazz, String name, int arity) {
        Class<?> c = clazz;
        while (c != null) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == arity) {
                    m.setAccessible(true);
                    return m;
                }
            }
            c = c.getSuperclass();
        }
        return null;
    }

    /** 是否为受管 Bot（主 Bot + 额外受管 Bot）。 */
    private boolean isManaged(String name) {
        return plugin.getBotManager() != null && plugin.getBotManager().isManagedBot(name);
    }
}
