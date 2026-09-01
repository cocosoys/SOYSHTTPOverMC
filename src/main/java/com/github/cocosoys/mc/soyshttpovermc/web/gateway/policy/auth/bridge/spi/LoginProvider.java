package com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.bridge.spi;

import com.github.cocosoys.mc.soyshttpovermc.web.gateway.policy.auth.bridge.AuthLoginBridge;
import org.bukkit.configuration.ConfigurationSection;

/**
 * 登录插件提供者抽象（SPI）：把"账号密码校验 / 玩家登录事件"统一抽象，
 * 快速接入任意登录插件（AuthMe、XAuth、nLogin、自有注册表等）。
 *
 * <p><b>接入新登录插件 = 实现本接口并注册到 {@link LoginProviderFactory}</b>，
 * 网关自动完成：密码校验 → 网页登录窗 → 会话令牌 → 权限镜像 全链路。</p>
 *
 * <h3>实现要点</h3>
 * <ul>
 *   <li>{@link #isAvailable()}：插件是否已加载（在<b>主线程</b>被调用——Spigot 的
 *       {@code getPluginManager().getPlugin()} 在 HTTP worker 线程可能返回 null）；</li>
 *   <li>{@link #init(LoginProviderContext)}：主线程调用（bridge 已创建），在此注册事件监听器
 *       与初始化底层句柄（如 AuthMe 的 DataSource/PasswordSecurity 反射），并<b>幂等</b>；</li>
 *   <li>{@link #verifyPassword(String, String)}：<b>纯账号密码校验，不得要求玩家在线</b>
 *       （离线网页登录依赖它）——实现应从数据库取 hash 做纯比对，而非依赖在线缓存/单例；</li>
 *   <li>{@link #shutdown()}：插件卸载清理。</li>
 * </ul>
 *
 * <p>软依赖约定：实现类若引用登录插件类型（如 {@code fr.xephi.authme.*}），必须<b>延迟加载</b>——
 * 由 {@code HttpOverMcPlugin} 在检测到对应插件已加载后才 {@code new} 并注册（防 NoClassDefFoundError）。</p>
 */
public interface LoginProvider {

    /**
     * 提供者唯一标识（小写英文，如 authme / xauth）。
     */
    String name();

    /**
     * 展示名（日志/页面用，如 AuthMe）。
     */
    String displayName();

    /**
     * 描述（接入方式说明）。
     */
    String description();

    /**
     * 对应登录插件是否已加载可用（主线程调用）。
     */
    boolean isAvailable();

    /**
     * 纯账号密码校验（不要求玩家在线）。
     * 账号不存在 / 密码错误 / 本提供者未就绪 → false。
     */
    boolean verifyPassword(String playerName, String password);

    /**
     * 从 {@code gateway/providers/<name>.yml} 加载自定义配置。
     * 每个 auth 实现必须完成自己的 config.yml 配置，以便对不同类型的登录插件进行更高度的自定义。
     * <p>默认不加载配置；子类按需覆盖。</p>
     *
     * @param config 本提供者的专属配置段（{@code gateway/providers/<name>.yml} 文件内容）
     */
    default void reload(ConfigurationSection config) {
    }

    /**
     * 主线程初始化（bridge 已创建后由网关调用）：注册事件监听器、初始化底层句柄。
     * <b>必须幂等</b>（/soyshttp reload 重建 bridge 后会再次调用，不得重复注册监听器）。
     */
    default void init(LoginProviderContext context) {
    }

    /**
     * 插件卸载清理。
     */
    default void shutdown() {
    }

    /**
     * 绑定到登录桥：bridge 创建/重建后把本提供者设为桥的校验来源。
     * 默认实现直接调用 {@link AuthLoginBridge#setLoginProvider(LoginProvider)}。
     */
    default void bind(AuthLoginBridge bridge) {
        if (bridge != null) {
            bridge.setLoginProvider(this);
        }
    }
}
