package com.github.cocosoys.mc.soyshttpovermc.gateway.policy.auth.bridge.spi;

import com.github.cocosoys.mc.soyshttpovermc.HttpOverMcPlugin;
import com.github.cocosoys.mc.soyshttpovermc.gateway.policy.auth.bridge.AuthLoginBridge;
import com.github.cocosoys.mc.soyshttpovermc.bot.BotManager;

/**
 * 登录插件提供者的运行上下文：提供插件实例 / 登录桥 / Bot 管理器等访问入口，
 * 避免 SPI 实现直接依赖 {@link HttpOverMcPlugin} 具体类（解耦 + 便于测试/文档化）。
 *
 * <p>由 {@link HttpOverMcPlugin} 在 onEnable 创建一次并交给 {@link LoginProviderFactory} 持有，
 * 各提供者通过 {@code LoginProviderFactory.context()} 获取。</p>
 */
public class LoginProviderContext {

    private final HttpOverMcPlugin plugin;

    public LoginProviderContext(HttpOverMcPlugin plugin) {
        this.plugin = plugin;
    }

    /** 宿主插件（注册事件监听器 / 读取配置等）。 */
    public HttpOverMcPlugin getPlugin() {
        return plugin;
    }

    /** 当前登录桥（可能为 null：session-token 颁发器未启用时）。 */
    public AuthLoginBridge bridge() {
        return plugin.getAuthLoginBridge();
    }

    /** MC 服务器地址（HTTP 链接构造用）。 */
    public String getMcHost() {
        return plugin.getMcHost();
    }

    /** MC 服务器端口（HTTP 链接构造用）。 */
    public int getMcPort() {
        return plugin.getMcPort();
    }

    /** 是否为受管隧道 Bot（免登录热装填的判定目标）。 */
    public boolean isManagedBot(String playerName) {
        BotManager bm = plugin.getBotManager();
        return bm != null && bm.isManagedBot(playerName);
    }
}
