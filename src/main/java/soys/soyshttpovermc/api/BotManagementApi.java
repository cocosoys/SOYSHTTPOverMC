package soys.soyshttpovermc.api;

import soys.soyshttpovermc.bot.BotManager;
import soys.soyshttpovermc.bot.InternalBot;

/**
 * 能力组 6：Bot 管理（委托 {@link BotManager}）。
 * 由 {@link SoysHttpOverMcApi#getBotManagement()} 跳转获取。
 */
public interface BotManagementApi {

    /** 创建一个额外受管无头 Bot（独立通道 + 隧道）并回连本服 */
    BotManager.ManagedBot addBot(String name, String channel);

    /** 踢出并断开一个额外受管 Bot（主 Bot 不可踢） */
    void kickBot(String name) throws Exception;

    /** 在主 Bot 上注册自定义通道并监听其下行消息 */
    void registerChannel(String channel, InternalBot.RawMessageListener listener);

    /** 注销自定义通道监听 */
    void unregisterChannel(String channel);

    /** 获取额外受管 Bot（不存在返回 null） */
    BotManager.ManagedBot getBot(String name);
}
