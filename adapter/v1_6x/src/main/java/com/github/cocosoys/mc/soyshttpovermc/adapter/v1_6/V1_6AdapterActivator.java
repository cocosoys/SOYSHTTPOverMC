package com.github.cocosoys.mc.soyshttpovermc.adapter.v1_6;

import com.github.cocosoys.mc.soyshttpovermc.adapter.ServerVersion;
import com.github.cocosoys.mc.soyshttpovermc.adapter.spi.AdapterActivator;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 1.6.x 版本适配激活入口（v1_6x 模块）。
 *
 * <p>经 {@code META-INF/services/...AdapterActivator} 注册，由
 * {@link com.github.cocosoys.mc.soyshttpovermc.adapter.spi.AdapterActivators#findFor(ServerVersion)}
 * 在 1.6.x 服务器上选中并 {@link #activate}。</p>
 *
 * <p>本模块的差异点：</p>
 * <ul>
 *   <li><b>YAML UTF-8 兼容</b>：1.6.4 的 {@code YamlConfiguration#loadConfiguration(File)}
 *       用平台默认编码（GBK）读取 → 由 {@code Platform} 覆盖（{@link V1_6PlatformAdapter} 继承
 *       {@code PlatformBukkitImpl}，其 loadYaml/saveYaml 经 {@code V1_6YamlIo} 显式 UTF-8）；</li>
 *   <li><b>同端口嗅探</b>：1.6.4 <b>未引入 Netty</b>（1.7.2 起才有），不提供同端口嗅探 →
 *       {@link V1_6SocketSnifferAdapter#supported()} 返回 {@code false}，插件强制
 *       standalone-server 独立端口模式；</li>
 *   <li><b>身份</b>：1.6.4 无 UUID API，玩家身份统一用 {@code PlayerIdentity.key()}（name 回退）。</li>
 * </ul>
 */
public class V1_6AdapterActivator implements AdapterActivator {

    @Override
    public String id() {
        return "v1_6x";
    }

    @Override
    public boolean supports(ServerVersion version) {
        return version != null && version.major() == 1 && version.minor() == 6;
    }

    @Override
    public void activate(JavaPlugin plugin, ServerVersion version) {
        // Platform 覆盖（含 YAML UTF-8）经 ServiceLoader 已由 Platforms 优先生效；
        // 1.6.4 无嗅探桥；这里仅留装配钩子（后续 core 集成点）。
    }

    @Override
    public void deactivate(JavaPlugin plugin) {
        // 插件禁用 / 热重载时还原（预留）
    }
}
