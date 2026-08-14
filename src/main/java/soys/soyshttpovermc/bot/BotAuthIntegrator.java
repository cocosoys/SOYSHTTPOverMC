package soys.soyshttpovermc.bot;

import org.bukkit.entity.Player;

/**
 * Bot 与软依赖登录插件（如 AuthMe）的集成器抽象（"自动热装填"机制）：
 * 隧道 Bot 作为普通玩家进服会被登录插件拦截/踢出，本集成器在 Bot 进服时自动执行
 * 免登录装填（如 {@code AuthMeApi.forceLogin}），Bot 退出后登录状态随玩家下线自然失效——
 * <b>不直接写入软依赖插件的配置文件</b>，随 Bot 生命周期自动装填/移除。
 *
 * <p>扩展新登录插件 = 继承本类实现 {@link #onBotJoin}，并注册到 {@code HttpOverMcPlugin}。
 */
public abstract class BotAuthIntegrator {

    /** 集成器唯一标识（日志用）。 */
    public abstract String name();

    /**
     * Bot 玩家进服（登录完成）时调用（主线程）。
     * 默认实现为空；子类在此执行免登录装填（如 forceLogin / 加入内存免登录名单）。
     */
    public void onBotJoin(Player botPlayer) {
    }

    /**
     * Bot 玩家退出时调用（主线程）。
     * 默认实现为空；子类可在此清理（AuthMe 等登录态随玩家下线自动失效，一般无需处理）。
     */
    public void onBotQuit(String botName) {
    }
}
