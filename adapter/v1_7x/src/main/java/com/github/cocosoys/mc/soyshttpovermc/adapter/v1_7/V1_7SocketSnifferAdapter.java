package com.github.cocosoys.mc.soyshttpovermc.adapter.v1_7;

import com.github.cocosoys.mc.soyshttpovermc.adapter.ServerVersion;
import com.github.cocosoys.mc.soyshttpovermc.adapter.spi.SocketSnifferAdapter;
import lombok.CustomLog;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 1.7.x 同端口嗅探器版本桥。
 *
 * <p>1.7.2+ 已引入 Netty（重新打包为 {@code net.minecraft.util.io.netty.*}），可挂接服务端 pipeline。
 * 与 1.12.2 不同，<b>1.7.10 的 {@code MinecraftServer} 没有 {@code getServerConnection()}</b>：
 * 经 javap 对 craftbukkit-1.7.10 实证，其结构为</p>
 * <pre>
 *   MinecraftServer:
 *     public static MinecraftServer getServer()
 *     public ServerConnection ai()              ← 方法通道（混淆名）
 *     private final ServerConnection p          ← 字段通道（混淆名）
 *   ServerConnection:
 *     private final List e                      ← 待注册监听 ChannelFuture 列表
 *     private final List f                      ← 已激活 Channel 列表
 *     public void a(InetAddress, int)           ← bind/监听入口
 * </pre>
 *
 * <p>本实现采用<b>「方法名 + 字段类型」双通道回退解析</b>（而非硬编码 1.12.2 的字段 {@code g}）：
 * 先按 NMS 包前缀（v1_7_R1..R4）找 {@code getServer()} → 再按 方法 ai()/getServerConnection()
 * 或 字段 p/g/serverConnection 定位 ServerConnection → 最后按元素类型过滤 List 字段中的
 * ChannelFuture 列表。</p>
 *
 * <p><b>核验状态</b>：上述字段/方法名已按 craftbukkit-1.7.10.jar javap 实证；
 * 运行期行为待 {@code servers/server-1.7.10} 起服冒烟核验（P2）。</p>
 */
@CustomLog
public class V1_7SocketSnifferAdapter implements SocketSnifferAdapter {
    /** 1.7 各补丁的 NMS 包后缀（1.7.2=R1 … 1.7.10=R4）。 */
    private static final String[] NMS_SUFFIXES = {"v1_7_R1", "v1_7_R2", "v1_7_R3", "v1_7_R4"};

    /** ServerConnection 定位候选方法名（混淆名随版本变化，双通道回退）。 */
    private static final String[] CONN_METHODS = {"ai", "getServerConnection"};
    /** ServerConnection 定位候选字段名。 */
    private static final String[] CONN_FIELDS = {"p", "g", "serverConnection"};

    @Override
    public String id() {
        return "v1_7x";
    }

    @Override
    public boolean supported() {
        // 1.7.2+ 已引入 Netty → 支持同端口嗅探
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
     * 定位当前 1.7 服务器的 {@code ServerConnection} 实例。
     *
     * @return ServerConnection 实例；定位失败返回 null
     */
    private Object findServerConnection() {
        for (String suffix : NMS_SUFFIXES) {
            try {
                Class<?> serverClass = Class.forName("net.minecraft.server." + suffix + ".MinecraftServer");
                Object instance = serverClass.getMethod("getServer").invoke(null);
                if (instance == null) {
                    continue;
                }
                Object conn = resolveViaMethods(serverClass, instance);
                if (conn == null) {
                    conn = resolveViaFields(serverClass, instance);
                }
                if (conn != null) {
                    log.info("[adapter/v1_7] 定位 ServerConnection 成功: " + suffix
                            + " (方法/字段双通道)");
                    return conn;
                }
            } catch (ClassNotFoundException e) {
                // 该补丁包不存在，尝试下一个
            } catch (Throwable t) {
                log.warn("[adapter/v1_7] 定位 ServerConnection 失败(" + suffix + ")", t);
            }
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
            log.warn("[adapter/v1_7] 提取监听 ChannelFuture 列表失败", t);
            return Collections.emptyList();
        }
        return fallback == null ? Collections.emptyList() : fallback;
    }

    /**
     * 元素是否为 Netty ChannelFuture（1.7.10 内嵌 netty 重新打包为 net.minecraft.util.io.netty）。
     */
    private boolean isNettyChannelFuture(Object o) {
        if (o == null) {
            return false;
        }
        String n = o.getClass().getName();
        return n.contains("netty") && n.contains("ChannelFuture");
    }
}
