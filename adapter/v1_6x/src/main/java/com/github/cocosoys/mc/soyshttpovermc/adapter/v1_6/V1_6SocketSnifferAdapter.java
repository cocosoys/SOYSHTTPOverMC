package com.github.cocosoys.mc.soyshttpovermc.adapter.v1_6;

import lombok.CustomLog;
import com.github.cocosoys.mc.soyshttpovermc.adapter.ServerVersion;
import com.github.cocosoys.mc.soyshttpovermc.adapter.spi.SocketSnifferAdapter;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.List;

/**
 * 1.6.x 同端口嗅探器版本桥。
 *
 * <p><b>1.6.4 支持同端口嗅探</b>（但实现方式与 1.7+/1.12 不同）：1.6.4 未引入 Netty
 * （Netty 自 1.7.2 起才重新打包为 {@code net.minecraft.util.io.netty.*}），网络层为自研
 * 阻塞 NIO（{@code DedicatedServerConnectionThread} 持有 {@code ServerSocket d}），
 * 因此无法像 1.7+/1.12 那样解析 {@code ServerConnection} 的 ChannelFuture 列表做
 * pipeline 注入。改为经 {@link V1_6HttpSniffer}（连接级接入）反射替换监听 ServerSocket、
 * 在 accept 侧首包嗅探分流——同一 MC 端口同时服务 HTTP / HTTPS / MC，即为同端口嗅探。</p>
 *
 * <p>因此本实现遵循 adapter/common 统一规范：
 * {@link #supported()} 返回 {@code true}（表示支持同端口嗅探，连接级接入实现）；
 * {@link #locateListenerChannels()} 恒返回空——连接级接入不依赖 netty pipeline，
 * 由 {@link V1_6HttpSnifferInstaller} 装配，无需定位监听 Channel。</p>
 */
@CustomLog
public class V1_6SocketSnifferAdapter implements SocketSnifferAdapter {

    @Override
    public String id() {
        return "v1_6x";
    }

    @Override
    public boolean supported() {
        // 1.6.4 经「连接级接入」支持同端口嗅探（反射替换 ServerSocket + 首包分流）
        return true;
    }

    @Override
    public List<?> locateListenerChannels() {
        // 连接级接入不依赖 netty pipeline，无需定位监听 Channel；返回空
        return Collections.emptyList();
    }

    @Override
    public void onInstall(JavaPlugin plugin, ServerVersion version) {
        log.info("[adapter/v1_6] 同端口嗅探由连接级接入实现（ServerSocket 首包分流），supported=true");
    }
}
