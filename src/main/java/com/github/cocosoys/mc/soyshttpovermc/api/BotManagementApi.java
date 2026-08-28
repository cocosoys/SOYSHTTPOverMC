package com.github.cocosoys.mc.soyshttpovermc.api;

import com.github.cocosoys.mc.soyshttpovermc.web.http.handler.bot.manager.BotManager;
import com.github.cocosoys.mc.soyshttpovermc.web.http.handler.bot.manager.InternalBot;

/**
 * 能力组 6：Bot 管理（委托 {@link BotManager}）。
 * 由 {@link SoysHttpOverMcApi#getBotManagement()} 跳转获取。
 */
public interface BotManagementApi {

    /**
     * 创建一个额外受管无头 Bot（独立通道 + 隧道）并回连本服。
     * 名称<b>完全自定义</b>（任意 ≤16 字符的合法离线名）；若名称以 {@code bot.name-prefix}（默认 "__bot__"）开头，
     * 将自动受 bot 专属账号保护（登录 IP 白名单 + 进服对真实玩家隐藏，见 {@code BotGuardian}）。
     */
    BotManager.ManagedBot addBot(String name, String channel);

    /**
     * 创建一个额外受管无头 Bot，复用主通道（多 Bot 分摊 / 双隧道冗余）。名称完全自定义，
     * 带 bot 前缀名称同样自动受专属保护。若同名已存在则返回既有项。
     */
    BotManager.ManagedBot addBot(String name);

    /** 踢出并断开一个额外受管 Bot（主 Bot 不可踢） */
    void kickBot(String name) throws Exception;

    /** 在主 Bot 上注册自定义通道并监听其下行消息 */
    void registerChannel(String channel, InternalBot.RawMessageListener listener);

    /** 注销自定义通道监听 */
    void unregisterChannel(String channel);

    /** 获取额外受管 Bot（不存在返回 null） */
    BotManager.ManagedBot getBot(String name);
}
