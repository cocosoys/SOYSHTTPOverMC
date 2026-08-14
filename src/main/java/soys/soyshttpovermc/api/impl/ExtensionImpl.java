package soys.soyshttpovermc.api.impl;

import soys.soyshttpovermc.HttpOverMcPlugin;
import soys.soyshttpovermc.api.ExtensionApi;
import soys.soyshttpovermc.command.SoysHttpCommand;
import soys.soyshttpovermc.command.SubCommand;
import soys.soyshttpovermc.exception.ApiException;
import soys.soyshttpovermc.exception.ExceptionBus;
import soys.soyshttpovermc.gateway.policy.auth.bridge.spi.LoginProvider;
import soys.soyshttpovermc.gateway.policy.auth.bridge.spi.LoginProviderFactory;

/**
 * 能力组 8：扩展接入实现（委托 {@link LoginProviderFactory} / {@link SoysHttpCommand}）。
 * 由 {@link SoysHttpOverMcApiImpl} 组合并对外暴露。
 */
public class ExtensionImpl implements ExtensionApi {

    private final HttpOverMcPlugin plugin;

    public ExtensionImpl(HttpOverMcPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void registerLoginProvider(LoginProvider provider) {
        try {
            LoginProviderFactory.register(provider);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new ApiException("E_REGISTER_LOGIN_PROVIDER",
                    "注册登录插件提供者失败: " + ex.getMessage(), ex));
        }
    }

    @Override
    public void registerSubCommand(SubCommand subCommand) {
        SoysHttpCommand cmd = plugin.getCommandExecutor();
        if (cmd == null) {
            throw ExceptionBus.fire(new ApiException("E_REGISTER_SUB_COMMAND",
                    "/soyshttp 命令尚未初始化（请在宿主 onEnable 完成后注册子指令）"));
        }
        cmd.registerSubCommand(subCommand);
    }
}
