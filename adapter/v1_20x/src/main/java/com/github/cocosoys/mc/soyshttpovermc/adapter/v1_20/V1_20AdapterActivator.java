package com.github.cocosoys.mc.soyshttpovermc.adapter.v1_20;

import com.github.cocosoys.mc.soyshttpovermc.adapter.ServerVersion;
import com.github.cocosoys.mc.soyshttpovermc.adapter.spi.AdapterActivator;

/**
 * 1.20.x 版本适配激活入口（v1_20x 模块，目标 1.20.4）。
 *
 * <p>经 {@code META-INF/services/...AdapterActivator} 注册，由
 * {@link com.github.cocosoys.mc.soyshttpovermc.adapter.spi.AdapterActivators#findFor(ServerVersion)}
 * 在 1.20.x 服务器上选中并 {@link #activate}。</p>
 *
 * <p>本模块的差异点：</p>
 * <ul>
 *   <li><b>NMS 包</b>：1.17+ 已去除版本包前缀，直接使用
 *       {@code net.minecraft.server.MinecraftServer} /
 *       {@code net.minecraft.server.network.ServerConnection}（1.20.4 仍为 ServerConnection，
 *       1.20.5+ 改名为 ServerConnectionListener，需单独模块适配）；</li>
 *   <li><b>Java</b>：1.20.x 最低要求 Java 17，本模块用 Java 17 编译；</li>
 *   <li><b>Netty</b>：标准 {@code io.netty}，服务端提供；</li>
 *   <li><b>同端口嗅探</b>：{@link V1_20SocketSnifferAdapter} 经无版本前缀 NMS 反射定位；</li>
 *   <li><b>api-version</b>：plugin.yml 标注 {@code 1.20}。</li>
 * </ul>
 */
public class V1_20AdapterActivator implements AdapterActivator {

    @Override
    public String id() {
        return "v1_20x";
    }

    @Override
    public boolean supports(ServerVersion version) {
        // 覆盖 1.20 ~ 1.20.4（1.20.5+ 破坏性变更需单独模块）
        return version != null && version.major() == 1 && version.minor() == 20
                && version.patch() <= 4;
    }

    @Override
    public void activate(Object plugin, ServerVersion version) {
        // Platform 覆盖经 ServiceLoader 已由 Platforms 优先生效；
        // 嗅探桥经 V1_20SocketSnifferAdapter 提供；这里仅留装配钩子。
    }

    @Override
    public void deactivate(Object plugin) {
        // 插件禁用 / 热重载时还原（预留）
    }
}
