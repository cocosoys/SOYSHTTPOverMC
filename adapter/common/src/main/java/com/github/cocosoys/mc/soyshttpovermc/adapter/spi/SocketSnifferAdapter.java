package com.github.cocosoys.mc.soyshttpovermc.adapter.spi;

import com.github.cocosoys.mc.soyshttpovermc.adapter.ServerVersion;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.List;

/**
 * 同端口 HTTP 嗅探器（SocketSniffer）的版本桥契约。
 *
 * <p>「同端口嗅探」= 在 MC 监听端口上同时识别并分流 HTTP / HTTPS / MC 流量，
 * 服务端网络内部结构随版本差异很大，各版本模块据此实现本接口：</p>
 *
 * <ul>
 *   <li><b>1.7.10 / 1.12.2</b>：Netty pipeline 注入。主工程
 *       {@code web/http/sniffer/SocketSniffer} 经反射挂接
 *       （{@code MinecraftServer#getServerConnection()} + ServerConnection 混淆字段），
 *       由 {@link #locateListenerChannels()} 定位监听 Channel 后向 pipeline 插桩。</li>
 *   <li><b>1.6.4</b>：无 Netty（1.7.2 起才引入，重新打包为 {@code net.minecraft.util.io.netty.*}），
 *       网络层为自研阻塞 NIO / 阻塞 Socket。改用「连接级接入」实现同端口嗅探——
 *       反射替换 {@code DedicatedServerConnectionThread} 的监听 ServerSocket，在 accept 侧
 *       首包分流（明文 HTTP / TLS / MC），经 {@code HttpSnifferInstaller} 装配；
 *       因此 {@link #supported()} 同样返回 {@code true}，但 {@link #locateListenerChannels()}
 *       恒返回空（不依赖 netty pipeline，无需定位监听 Channel）。</li>
 * </ul>
 *
 * <p>返回值刻意使用 {@code Object} / {@code List<?>}，避免本模块对 Netty 的编译期依赖；
 * 消费者（core，运行于 1.12.2）拿到后自行强转。</p>
 */
public interface SocketSnifferAdapter {

    /**
     * 模块标识（与 AdapterActivator#id 一致的版本段）。
     */
    String id();

    /**
     * 当前服务器是否支持同端口嗅探（无论实现方式：netty pipeline 桥或连接级接入）。
     *
     * @return {@code true}=支持同端口嗅探（1.7+/1.12 走 netty 桥，1.6.4 走连接级接入）；
     *         {@code false}=只能独立端口 standalone-server
     */
    boolean supported();

    /**
     * 定位服务端正在监听 TCP 的 Channel 集合（{@code ChannelFuture} / {@code Channel} 的 Object 视图）。
     *
     * <p>仅对 netty pipeline 实现（1.7+/1.12）有意义：当 {@link #supported()} 为
     * {@code true} 时由主工程 netty 嗅探器调用；返回空集合表示未找到。
     * 连接级接入实现（1.6.4）不依赖 netty，恒返回空且不会被调用。</p>
     *
     * @return 监听 Channel 列表；未知/不可用/非 netty 实现时为空列表
     */
    List<?> locateListenerChannels();

    /**
     * 安装钩子：在嗅探器装配前调用（可预置反射句柄、探测结构并缓存）。
     */
    default void onInstall(JavaPlugin plugin, ServerVersion version) {
        // 默认无操作
    }

    /**
     * 卸载钩子：插件禁用 / 热重载时还原。
     */
    default void onUninstall() {
        // 默认无操作
    }

    /**
     * 空实现（{@code supported()==false} 时的占位）。
     */
    final class Unsupported implements SocketSnifferAdapter {

        private final String id;

        public Unsupported(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public boolean supported() {
            return false;
        }

        @Override
        public List<?> locateListenerChannels() {
            return Collections.emptyList();
        }
    }
}
