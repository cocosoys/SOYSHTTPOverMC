package com.github.cocosoys.mc.soyshttpovermc.adapter.v1_7;

import com.github.cocosoys.mc.soyshttpovermc.adapter.ServerVersion;
import com.github.cocosoys.mc.soyshttpovermc.adapter.spi.AdapterActivator;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 1.7.x 版本适配激活入口（v1_7x 模块）。
 *
 * <p>经 {@code META-INF/services/...AdapterActivator} 注册，由
 * {@link com.github.cocosoys.mc.soyshttpovermc.adapter.spi.AdapterActivators#findFor(ServerVersion)}
 * 在 1.7.x 服务器上选中并 {@link #activate}。</p>
 *
 * <p>本模块的差异点：</p>
 * <ul>
 *   <li><b>YAML UTF-8 兼容</b>：1.7.10 的 {@code YamlConfiguration#loadConfiguration(File)}
 *       用平台默认编码（GBK）读取 → 由 {@code Platform} 覆盖（{@link V1_7PlatformAdapter} 继承
 *       {@code PlatformBukkitImpl}，其 loadYaml/saveYaml 经 {@code YamlIo} 显式 UTF-8，本模块无需重复覆写）；</li>
 *   <li><b>同端口嗅探</b>：1.7.2+ 已引入 Netty，可挂接服务端 pipeline → {@link V1_7SocketSnifferAdapter}；
 *       MinecraftServer 无 {@code getServerConnection()}，需按 v1_7_R4 的「方法 ai() / 字段 p」双通道解析。</li>
 *   <li><b>身份</b>：1.7.10 有 {@code OfflinePlayer#getUniqueId()} → {@code PlayerIdentity.key()} 走 UUID 优先。</li>
 * </ul>
 */
public class V1_7AdapterActivator implements AdapterActivator {

    @Override
    public String id() {
        return "v1_7x";
    }

    @Override
    public boolean supports(ServerVersion version) {
        return version != null && version.major() == 1 && version.minor() == 7;
    }

    @Override
    public void activate(JavaPlugin plugin, ServerVersion version) {
        // Platform 覆盖（含 YAML UTF-8）经 ServiceLoader 已由 Platforms 优先生效；
        // 嗅探桥经 V1_7SocketSnifferAdapter 提供；这里仅留装配钩子（后续 core 集成点）。
    }

    @Override
    public void deactivate(JavaPlugin plugin) {
        // 插件禁用 / 热重载时还原（预留）
    }
}
