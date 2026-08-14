package soys.soyshttpovermc.api;

import soys.soyshttpovermc.command.SubCommand;
import soys.soyshttpovermc.gateway.policy.auth.bridge.spi.LoginProvider;

/**
 * 能力组 8：扩展接入（登录插件提供者 / /soyshttp 子指令）。
 *
 * <p>由 {@link SoysHttpOverMcApi#getExtension()} 跳转获取。为第三方开发者提供：</p>
 * <ul>
 *   <li>{@link #registerLoginProvider(LoginProvider)} —— 注册新的登录插件提供者
 *       （实现 {@link LoginProvider} SPI 后一行接入，网关自动完成密码校验/免登录/玩家登录签发）；</li>
 *   <li>{@link #registerSubCommand(SubCommand)} —— 注册新的 {@code /soyshttp}（或简写 {@code /shttp}）
 *       子指令（继承 {@link SubCommand} 实现 4 个方法即可，op 校验 / help 聚合自动完成）。</li>
 * </ul>
 *
 * <p>API 访问监听事件：监听 Bukkit 事件 {@code soys.soyshttpovermc.api.event.ApiAccessEvent}
 * 及按请求类型细分的 {@code ApiGetEvent} / {@code ApiPostEvent} / {@code ApiPutEvent} /
 * {@code ApiDeleteEvent} / {@code ApiPatchEvent} / {@code ApiOtherEvent}，事件直接携带
 * 玩家名（playerName）与玩家实体（player，离线为 null）。</p>
 */
public interface ExtensionApi {

    /** 注册登录插件提供者（LoginProvider SPI；建议在检测到对应插件已加载后调用）。 */
    void registerLoginProvider(LoginProvider provider);

    /** 注册 /soyshttp 子指令（需在宿主 onEnable 完成后调用，否则命令未初始化会抛异常）。 */
    void registerSubCommand(SubCommand subCommand);
}
