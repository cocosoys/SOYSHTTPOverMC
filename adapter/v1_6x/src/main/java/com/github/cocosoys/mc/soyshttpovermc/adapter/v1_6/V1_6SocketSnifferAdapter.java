package com.github.cocosoys.mc.soyshttpovermc.adapter.v1_6;

import com.github.cocosoys.mc.soyshttpovermc.adapter.ServerVersion;
import com.github.cocosoys.mc.soyshttpovermc.adapter.spi.SocketSnifferAdapter;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * 1.6.x 同端口嗅探器版本桥。
 *
 * <p><b>1.6.4 不支持同端口嗅探</b>：Netty 自 1.7.2 起才引入（重新打包为
 * {@code net.minecraft.util.io.netty.*}），1.6.4 的网络层为自研 NIO / Mina，不具备可挂接的
 * pipeline 通道列表，无法像 1.7+/1.12 那样解析 {@code ServerConnection} 的 ChannelFuture 列表。</p>
 *
 * <p>因此本实现遵循 adapter/common 统一规范：
 * {@link #supported()} 返回 {@code false} → 插件<b>禁止</b>启用同端口嗅探，
 * 强制 {@code standalone-server} 独立端口模式；{@link #locateListenerChannels()} 恒返回空。</p>
 */
public class V1_6SocketSnifferAdapter implements SocketSnifferAdapter {

    private static final Logger LOG = Logger.getLogger(V1_6SocketSnifferAdapter.class.getName());

    @Override
    public String id() {
        return "v1_6x";
    }

    @Override
    public boolean supported() {
        // 1.6.4 未引入 Netty → 不支持同端口嗅探
        return false;
    }

    @Override
    public List<?> locateListenerChannels() {
        return Collections.emptyList();
    }

    @Override
    public void onInstall(JavaPlugin plugin, ServerVersion version) {
        LOG.info("[adapter/v1_6] 1.6.4 无 Netty，同端口嗅探已禁用，强制 standalone-server 独立端口模式");
    }
}
