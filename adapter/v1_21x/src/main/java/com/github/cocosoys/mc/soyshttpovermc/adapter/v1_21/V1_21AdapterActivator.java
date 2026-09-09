package com.github.cocosoys.mc.soyshttpovermc.adapter.v1_21;

import com.github.cocosoys.mc.soyshttpovermc.adapter.ServerVersion;
import com.github.cocosoys.mc.soyshttpovermc.adapter.spi.AdapterActivator;

/**
 * 1.21.x 版本适配激活入口（v1_21x 模块，目标 1.21.4）。
 *
 * <p>经 {@code META-INF/services/...AdapterActivator} 注册，由
 * {@link com.github.cocosoys.mc.soyshttpovermc.adapter.spi.AdapterActivators#findFor(ServerVersion)}
 * 在 1.21.x 服务器上选中并 {@link #activate}。</p>
 *
 * <p>本模块的差异点：</p>
 * <ul>
 *   <li><b>NMS 包</b>：无版本包前缀（1.17+ 去除），直接使用
 *       {@code net.minecraft.server.MinecraftServer}；</li>
 *   <li><b>ServerConnection 重命名</b>：1.20.5+ 中
 *       {@code ServerConnection} 改名为 {@code ServerConnectionListener}，
 *       {@link V1_21SocketSnifferAdapter} 已适配双通道候选名；</li>
 *   <li><b>Netty handler 重命名</b>：1.20.5+ 中
 *       {@code PacketSplitter}→{@code Varint21FrameDecoder}、
 *       {@code LegacyPingHandler}→{@code LegacyQueryHandler}；</li>
 *   <li><b>Java</b>：强制 Java 21 编译；</li>
 *   <li><b>api-version</b>：plugin.yml 标注 {@code 1.21}。</li>
 * </ul>
 */
public class V1_21AdapterActivator implements AdapterActivator {

    @Override
    public String id() {
        return "v1_21x";
    }

    @Override
    public boolean supports(ServerVersion version) {
        return version != null && version.major() == 1 && version.minor() == 21;
    }

    @Override
    public void activate(Object plugin, ServerVersion version) {
        // 预留装配钩子
    }

    @Override
    public void deactivate(Object plugin) {
        // 预留
    }
}
