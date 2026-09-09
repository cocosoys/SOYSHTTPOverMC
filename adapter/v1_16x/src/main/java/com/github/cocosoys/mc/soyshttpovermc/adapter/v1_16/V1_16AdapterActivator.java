package com.github.cocosoys.mc.soyshttpovermc.adapter.v1_16;

import com.github.cocosoys.mc.soyshttpovermc.adapter.ServerVersion;
import com.github.cocosoys.mc.soyshttpovermc.adapter.spi.AdapterActivator;

/**
 * 1.16.x 版本适配激活入口（v1_16x 模块）。
 *
 * <p>经 {@code META-INF/services/...AdapterActivator} 注册，由
 * {@link com.github.cocosoys.mc.soyshttpovermc.adapter.spi.AdapterActivators#findFor(ServerVersion)}
 * 在 1.16.x 服务器上选中并 {@link #activate}。</p>
 *
 * <p>本模块的差异点：</p>
 * <ul>
 *   <li><b>NMS 包</b>：1.16.5 为 {@code v1_16_R3}（1.16.1=R1, 1.16.2-3=R2, 1.16.4-5=R3），
 *       仍保留版本包前缀（1.17+ 才去除）；</li>
 *   <li><b>Netty</b>：1.16 已使用标准 {@code io.netty}（非 1.7 时代的 relocate 版），
 *       服务端直接提供，无需 runtime 内嵌；</li>
 *   <li><b>同端口嗅探</b>：{@code MinecraftServer#getServerConnection()} 方法在 1.16 仍存在，
 *       {@link V1_16SocketSnifferAdapter} 经方法名+字段类型双通道反射定位；</li>
 *   <li><b>PluginMessage</b>：通道名 {@code httpproxy:main} 已符合 1.13+ {@code namespace:key}
 *       强制格式，无需修改；</li>
 *   <li><b>api-version</b>：plugin.yml 标注 {@code 1.13}，走现代 API 路径。</li>
 * </ul>
 */
public class V1_16AdapterActivator implements AdapterActivator {

    @Override
    public String id() {
        return "v1_16x";
    }

    @Override
    public boolean supports(ServerVersion version) {
        return version != null && version.major() == 1 && version.minor() == 16;
    }

    @Override
    public void activate(Object plugin, ServerVersion version) {
        // Platform 覆盖经 ServiceLoader 已由 Platforms 优先生效；
        // 嗅探桥经 V1_16SocketSnifferAdapter 提供；这里仅留装配钩子（后续 core 集成点）。
    }

    @Override
    public void deactivate(Object plugin) {
        // 插件禁用 / 热重载时还原（预留）
    }
}
