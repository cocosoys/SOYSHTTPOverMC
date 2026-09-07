package com.github.cocosoys.mc.soyshttpovermc.adapter.spi;

import com.github.cocosoys.mc.soyshttpovermc.adapter.ServerVersion;

/**
 * 版本适配模块统一激活契约。
 *
 * <p>每个版本模块（v1_6x / v1_7x…）实现本接口，并在 <b>自身 jar 的
 * {@code META-INF/services/com.github.cocosoys.mc.soyshttpovermc.adapter.spi.AdapterActivator}</b>
 * 文件中注册全限定类名。插件启动时经 {@link AdapterActivators#findFor(ServerVersion)} 匹配
 * 当前服务器版本并调用 {@link #activate}。</p>
 *
 * <p>职责：在激活时完成本版本特有的装配（注册 Platform SPI 覆盖、安装嗅探桥、
 * 强制 standalone-server 降级、初始化版本专用配置等）；{@link #deactivate} 负责还原
 * （插件禁用 / 热重载场景）。</p>
 */
public interface AdapterActivator {

    /**
     * 模块标识（唯一，用于日志与诊断）：如 {@code v1_6x} / {@code v1_7x}。
     */
    String id();

    /**
     * 是否匹配当前服务器版本。
     *
     * @param version 当前服务器版本（经 {@link ServerVersion#current()} 解析）
     */
    boolean supports(ServerVersion version);

    /**
     * 激活装配。
     *
     * @param plugin  宿主插件（{@code org.bukkit.plugin.java.JavaPlugin} 实例的 Object 视图；
     *                本模块零 Bukkit 编译期依赖，实现方按需强转）
     * @param version 当前服务器版本
     */
    void activate(Object plugin, ServerVersion version);

    /**
     * 反激活 / 还原（插件禁用或重载时）。
     *
     * @param plugin 宿主插件（{@code JavaPlugin} 实例的 Object 视图）
     */
    void deactivate(Object plugin);
}
