package soys.soyshttpovermc.api.impl;

import soys.soyshttpovermc.api.BotManagementApi;
import soys.soyshttpovermc.bot.BotManager;
import soys.soyshttpovermc.bot.InternalBot;
import soys.soyshttpovermc.exception.BotException;
import soys.soyshttpovermc.exception.ExceptionBus;
import soys.soyshttpovermc.i18n.I18n;

/**
 * 能力组 6：Bot 管理（委托 {@link BotManager}）。
 * 由 {@link SoysHttpOverMcApiImpl} 组合并对外暴露；实现 {@link BotManagementApi}。
 */
public class BotManagementImpl implements BotManagementApi {

    private final BotManager botManager;

    public BotManagementImpl(BotManager botManager) {
        this.botManager = botManager;
    }

    @Override
    public BotManager.ManagedBot addBot(String name, String channel) {
        try {
            return botManager.addBot(name, channel);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new BotException("E_ADD_BOT", "exception.bot.add-fail", "创建受管 Bot 失败(name={0}): {1}", ex, name, ex.getMessage()));
        }
    }

    @Override
    public BotManager.ManagedBot addBot(String name) {
        try {
            return botManager.addBot(name);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new BotException("E_ADD_BOT", "exception.bot.add-fail", "创建受管 Bot 失败(name={0}): {1}", ex, name, ex.getMessage()));
        }
    }

    @Override
    public void kickBot(String name) {
        try {
            botManager.kickBot(name);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new BotException("E_KICK_BOT", "exception.bot.kick-fail", "踢出 Bot 失败(name={0}): {1}", ex, name, ex.getMessage()));
        }
    }

    @Override
    public void registerChannel(String channel, InternalBot.RawMessageListener listener) {
        try {
            botManager.registerChannel(channel, listener);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new BotException("E_REG_CHANNEL", "exception.bot.register-channel-fail", "注册通道失败(channel={0}): {1}", ex, channel, ex.getMessage()));
        }
    }

    @Override
    public void unregisterChannel(String channel) {
        try {
            botManager.unregisterChannel(channel);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new BotException("E_UNREG_CHANNEL", "exception.bot.unregister-channel-fail", "注销通道失败(channel={0}): {1}", ex, channel, ex.getMessage()));
        }
    }

    @Override
    public BotManager.ManagedBot getBot(String name) {
        return botManager.getBot(name);
    }
}
