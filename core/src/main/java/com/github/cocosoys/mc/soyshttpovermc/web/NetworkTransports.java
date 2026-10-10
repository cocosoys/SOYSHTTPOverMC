package com.github.cocosoys.mc.soyshttpovermc.web;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 全局网络传输注册表：开发者经 {@code WebPageApi.registerNetworkTransport(transport)} 注入的
 * 自定义传输器（加密 / 签名校验 / 私有协议）在此统一登记，供首页远程拉取等内置网络读取点委托。
 *
 * <p>优先级：已注册传输器按注册顺序命中——第一个 {@link NetworkTransport#fetch(String)} 不抛异常的结果即返回；
 * 全部失败 / 无注册 → 调用方回退默认 {@code HttpURLConnection} 直连。</p>
 */
public final class NetworkTransports {

    private static final List<NetworkTransport> REGISTRY = new CopyOnWriteArrayList<>();

    private NetworkTransports() {
    }

    /** 登记传输器（重复注册同名不报错，按注册顺序依次尝试）。 */
    public static void register(NetworkTransport transport) {
        if (transport != null) {
            REGISTRY.add(transport);
        }
    }

    /** 清除全部传输器（主插件 onDisable / reload 时调用，避免旧实例泄漏）。 */
    public static void clear() {
        REGISTRY.clear();
    }

    /** 已注册传输器数量（调试/日志）。 */
    public static int size() {
        return REGISTRY.size();
    }

    /**
     * 经已注册传输器拉取 URL 内容；任一传输器成功即返回。
     *
     * @return 传输字节；无注册传输器或全部失败返回 null（调用方回退默认直连）
     */
    public static byte[] fetch(String url) {
        if (url == null || REGISTRY.isEmpty()) {
            return null;
        }
        RuntimeException last = null;
        for (NetworkTransport t : REGISTRY) {
            try {
                byte[] body = t.fetch(url);
                if (body != null) {
                    return body;
                }
            } catch (Exception e) {
                last = new RuntimeException("transport " + t.name() + " fetch 失败: " + e.getMessage(), e);
            }
        }
        if (last != null) {
            throw last;
        }
        return null;
    }
}
