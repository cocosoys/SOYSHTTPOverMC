package com.github.cocosoys.mc.soyshttpovermc.adapter.v1_20;

import com.github.cocosoys.mc.soyshttpovermc.adapter.ServerVersion;
import com.github.cocosoys.mc.soyshttpovermc.adapter.spi.SocketSnifferAdapter;
import lombok.CustomLog;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

/**
 * 1.20.x 同端口嗅探器版本桥（初始化骨架，目标 1.20.4）。
 *
 * <p>1.20.x 使用标准 {@code io.netty}，NMS 无版本包前缀（1.17+ 去除）。
 * 1.20.4 的 NMS 结构：</p>
 * <pre>
 *   net.minecraft.server.MinecraftServer:
 *     public static MinecraftServer getServer()
 *     public net.minecraft.server.network.ServerConnection getServerConnection()
 *   net.minecraft.server.network.ServerConnection:
 *     private final List<ChannelFuture> listeningChannels
 *     private final List<NetworkManager> channels
 * </pre>
 *
 * <p>本实现采用「方法名 + 字段类型」双通道回退解析，不硬编码字段名。
 * 注意：1.20.5 中 {@code ServerConnection} 改名为 {@code ServerConnectionListener}，
 * 本模块不覆盖 1.20.5+（需单独 v1_20_5 模块）。</p>
 *
 * <p><b>当前状态</b>：模块初始化骨架，反射定位逻辑已就绪，运行期行为待
 * {@code servers/server-1.20.4} 起服冒烟核验。</p>
 */
@CustomLog
public class V1_20SocketSnifferAdapter implements SocketSnifferAdapter {

    /** 1.17+ 无版本包前缀，直接使用全限定类名。 */
    private static final String MINECRAFT_SERVER_CLASS = "net.minecraft.server.MinecraftServer";

    /** ServerConnection 定位候选方法名（1.20.4 仍为 getServerConnection）。 */
    private static final String[] CONN_METHODS = {"getServerConnection", "ai", "ab", "getConnection"};
    /** ServerConnection 定位候选字段名。 */
    private static final String[] CONN_FIELDS = {"serverConnection", "connection", "g", "p", "q"};

    @Override
    public String id() {
        return "v1_20x";
    }

    @Override
    public boolean supported() {
        // 1.20.x 使用标准 io.netty → 支持同端口嗅探
        return true;
    }

    @Override
    public List<?> locateListenerChannels() {
        Object serverConnection = findServerConnection();
        if (serverConnection == null) {
            return Collections.emptyList();
        }
        return extractPendingChannelFutureList(serverConnection);
    }

    @Override
    public void onInstall(Object plugin, ServerVersion version) {
        // 预留：可在此探测并缓存反射句柄
    }

    // ===== 反射定位 =====

    /**
     * 定位当前 1.20 服务器的 {@code ServerConnection} 实例。
     *
     * @return ServerConnection 实例；定位失败返回 null
     */
    private Object findServerConnection() {
        try {
            Class<?> serverClass = Class.forName(MINECRAFT_SERVER_CLASS);
            Object instance = serverClass.getMethod("getServer").invoke(null);
            if (instance == null) {
                return null;
            }
            Object conn = resolveViaMethods(serverClass, instance);
            if (conn == null) {
                conn = resolveViaFields(serverClass, instance);
            }
            if (conn != null) {
                log.infoT("log.adapter.v120.conn-found",
                        "[adapter/v1_20] 定位 ServerConnection 成功 (方法/字段双通道)");
                return conn;
            }
        } catch (ClassNotFoundException e) {
            log.warnT("log.adapter.v120.nms-not-found",
                    "[adapter/v1_20] 未找到 NMS MinecraftServer 类（1.20.5+ 结构变更需单独模块）", e);
        } catch (Throwable t) {
            log.warnT("log.adapter.v120.conn-failed",
                    "[adapter/v1_20] 定位 ServerConnection 失败", t);
        }
        return null;
    }

    private Object resolveViaMethods(Class<?> serverClass, Object instance) {
        for (String name : CONN_METHODS) {
            try {
                Method m = serverClass.getMethod(name);
                if (m.getReturnType().getName().contains("ServerConnection")) {
                    return m.invoke(instance);
                }
            } catch (Throwable ignored) {
                // 尝试下一个候选
            }
        }
        return null;
    }

    private Object resolveViaFields(Class<?> serverClass, Object instance) {
        for (String name : CONN_FIELDS) {
            try {
                Field f = serverClass.getDeclaredField(name);
                if (f.getType().getName().contains("ServerConnection")) {
                    f.setAccessible(true);
                    return f.get(instance);
                }
            } catch (Throwable ignored) {
                // 尝试下一个候选
            }
        }
        return null;
    }

    /**
     * 从 ServerConnection 提取「待注册监听 ChannelFuture」列表：
     * 遍历 List 类型字段，元素为 Netty ChannelFuture 时命中；全部为空时返回第一个 List 字段兜底。
     */
    private List<?> extractPendingChannelFutureList(Object serverConnection) {
        List<?> fallback = null;
        try {
            for (Field f : serverConnection.getClass().getDeclaredFields()) {
                if (!List.class.isAssignableFrom(f.getType())) {
                    continue;
                }
                f.setAccessible(true);
                Object v = f.get(serverConnection);
                if (!(v instanceof List)) {
                    continue;
                }
                List<?> list = (List<?>) v;
                if (fallback == null) {
                    fallback = list;
                }
                if (!list.isEmpty() && isNettyChannelFuture(list.get(0))) {
                    return list;
                }
            }
        } catch (Throwable t) {
            log.warnT("log.adapter.v120.channelfuture-failed",
                    "[adapter/v1_20] 提取监听 ChannelFuture 列表失败", t);
            return Collections.emptyList();
        }
        return fallback == null ? Collections.emptyList() : fallback;
    }

    /** 元素是否为 Netty ChannelFuture（1.20 使用标准 io.netty）。 */
    private boolean isNettyChannelFuture(Object o) {
        if (o == null) {
            return false;
        }
        String n = o.getClass().getName();
        return n.contains("netty") && n.contains("ChannelFuture");
    }
}
