package com.github.cocosoys.mc.soyshttpovermc.adapter.v1_26;

import com.github.cocosoys.mc.soyshttpovermc.adapter.ServerVersion;
import com.github.cocosoys.mc.soyshttpovermc.adapter.spi.SocketSnifferAdapter;
import lombok.CustomLog;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

/**
 * 26.x 同端口嗅探器版本桥（初始化骨架，目标 Minecraft 26.2）。
 *
 * <p>26.x 使用标准 {@code io.netty}，NMS 无版本包前缀。
 * 基于 1.20.5+ 架构（{@code ServerConnectionListener}），
 * 采用「类名双通道 + 方法名 + 字段类型」三级回退解析。</p>
 *
 * <p><b>当前状态</b>：模块初始化骨架，反射定位逻辑已就绪，运行期行为待
 * {@code servers/server-26.2} 起服冒烟核验。</p>
 */
@CustomLog
public class V1_26SocketSnifferAdapter implements SocketSnifferAdapter {

    private static final String MINECRAFT_SERVER_CLASS = "net.minecraft.server.MinecraftServer";

    /** 基于 1.20.5+ 架构的候选类名（1.26 可能进一步变更，待核验）。 */
    private static final String[] CONN_CLASS_NAMES = {
            "net.minecraft.server.network.ServerConnectionListener",
            "net.minecraft.server.network.ServerConnection"
    };

    private static final String[] CONN_METHODS = {
            "getServerConnection", "getConnection", "ai", "ab", "a"
    };
    private static final String[] CONN_FIELDS = {
            "serverConnection", "connection", "g", "p", "q", "e"
    };

    @Override
    public String id() {
        return "v1_26x";
    }

    @Override
    public boolean supported() {
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
        // 预留
    }

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
                log.infoT("log.adapter.v126.conn-found",
                        "[adapter/v1_26] 定位 ServerConnection/Listener 成功: {0}",
                        conn.getClass().getSimpleName());
                return conn;
            }
        } catch (ClassNotFoundException e) {
            log.warnT("log.adapter.v126.nms-not-found",
                    "[adapter/v1_26] 未找到 NMS MinecraftServer 类", e);
        } catch (Throwable t) {
            log.warnT("log.adapter.v126.conn-failed",
                    "[adapter/v1_26] 定位 ServerConnection 失败", t);
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
            }
        }
        return null;
    }

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
            log.warnT("log.adapter.v126.channelfuture-failed",
                    "[adapter/v1_26] 提取监听 ChannelFuture 列表失败", t);
            return Collections.emptyList();
        }
        return fallback == null ? Collections.emptyList() : fallback;
    }

    private boolean isNettyChannelFuture(Object o) {
        if (o == null) {
            return false;
        }
        String n = o.getClass().getName();
        return n.contains("netty") && n.contains("ChannelFuture");
    }
}
