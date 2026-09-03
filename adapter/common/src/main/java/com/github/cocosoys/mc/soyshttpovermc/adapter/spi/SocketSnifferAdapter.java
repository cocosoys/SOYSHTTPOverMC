package com.github.cocosoys.mc.soyshttpovermc.adapter.spi;

import com.github.cocosoys.mc.soyshttpovermc.adapter.ServerVersion;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.List;

/**
 * 同端口 HTTP 嗅探器（SocketSniffer）的版本桥契约。
 *
 * <p>主工程 {@code web/http/sniffer/SocketSniffer} 通过反射挂接服务端 Netty pipeline
 * （{@code MinecraftServer#getServerConnection()} + ServerConnection 混淆字段），
 * 该内部结构随版本变化且 1.6.4 服务器根本没有 Netty。为此把「定位监听 Channel」的
 * 版本差异收敛到各版本模块实现本接口：</p>
 *
 * <ul>
 *   <li><b>1.7.10</b>：Netty 已引入（1.7.2+），可反射定位监听 Channel —— 用
 *       「方法名 + 字段类型」双通道回退解析，而非硬编码 1.12.2 混淆名 {@code g}。</li>
 *   <li><b>1.6.4</b>：服务器为旧阻塞 IO，无 Netty —— {@link #supported()} 返回
 *       {@code false}，插件必须禁用同端口嗅探，强制独立端口 standalone-server。</li>
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
     * 当前服务器是否支持同端口嗅探。
     *
     * @return {@code true}=可挂接服务端 Netty；{@code false}=必须走独立端口（如 1.6.4）
     */
    boolean supported();

    /**
     * 定位服务端正在监听 TCP 的 Channel 集合（{@code ChannelFuture} / {@code Channel} 的 Object 视图）。
     *
     * <p>仅当 {@link #supported()} 为 {@code true} 时被调用；返回空集合表示未找到。
     * 由各版本模块按「方法名 + 字段类型」双通道反射解析实现。</p>
     *
     * @return 监听 Channel 列表；未知/不可用时为空列表
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
