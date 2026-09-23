package com.github.cocosoys.mc.soyshttpovermc.adapter.v1_16;

import com.github.cocosoys.mc.soyshttpovermc.adapter.ServerVersion;
import com.github.cocosoys.mc.soyshttpovermc.adapter.spi.SocketSnifferAdapter;
import lombok.CustomLog;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

/**
 * 1.16.x 同端口嗅探器版本桥（初始化骨架）。
 *
 * <p>1.16.x 使用标准 {@code io.netty}（非 relocate），可挂接服务端 pipeline。
 * 1.16.5 的 NMS 结构（{@code v1_16_R3}）：</p>
 * <pre>
 *   MinecraftServer:
 *     public static MinecraftServer getServer()
 *     public ServerConnection getServerConnection()   ← 1.12.2 同名方法，1.16 仍保留
 *   ServerConnection:
 *     private final List&lt;ChannelFuture&gt; listeningChannels  ← 待注册监听 ChannelFuture 列表
 *     private final List&lt;NetworkManager&gt; channels           ← 已激活连接列表
 * </pre>
 *
 * <p>本实现采用「方法名 + 字段类型」双通道回退解析（与 v1_7x 同策略），
 * 不硬编码字段名，以容忍 1.16 各小版本（R1/R2/R3）的混淆差异。</p>
 *
 * <p><b>当前状态</b>：模块初始化骨架，反射定位逻辑已就绪，运行期行为待
 * {@code servers/server-1.16.5} 起服冒烟核验。</p>
 */
@CustomLog
public class V1_16SocketSnifferAdapter implements SocketSnifferAdapter {

    /** 1.16 各补丁的 NMS 包后缀（1.16=R1, 1.16.1=R1, 1.16.2-3=R2, 1.16.4-5=R3）。 */
    private static final String[] NMS_SUFFIXES = {"v1_16_R1", "v1_16_R2", "v1_16_R3"};

    /** ServerConnection 定位候选方法名。 */
    private static final String[] CONN_METHODS = {"getServerConnection", "ai", "ab"};
    /** ServerConnection 定位候选字段名。 */
    private static final String[] CONN_FIELDS = {"serverConnection", "g", "p", "q"};

    @Override
    public String id() {
        return "v1_16x";
    }

    @Override
    public boolean supported() {
        // 1.16.x 使用标准 io.netty → 支持同端口嗅探
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
     * 定位当前 1.16 服务器的 {@code ServerConnection} 实例。
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
                    log.infoT("log.adapter.v116.conn-found",
                            "[adapter/v1_16] 定位 ServerConnection 成功: {0} (方法/字段双通道)", suffix);
                    return conn;
                }
            } catch (ClassNotFoundException e) {
                // 该补丁包不存在，尝试下一个
            } catch (Throwable t) {
                log.warnT("log.adapter.v116.conn-failed",
                        "[adapter/v1_16] 定位 ServerConnection 失败({0})", suffix, t);
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
            log.warnT("log.adapter.v116.channelfuture-failed",
                    "[adapter/v1_16] 提取监听 ChannelFuture 列表失败", t);
            return Collections.emptyList();
        }
        return fallback == null ? Collections.emptyList() : fallback;
    }

    /** 元素是否为 Netty ChannelFuture（1.16 使用标准 io.netty）。 */
    private boolean isNettyChannelFuture(Object o) {
        if (o == null) {
            return false;
        }
        String n = o.getClass().getName();
        return n.contains("netty") && n.contains("ChannelFuture");
    }
}
