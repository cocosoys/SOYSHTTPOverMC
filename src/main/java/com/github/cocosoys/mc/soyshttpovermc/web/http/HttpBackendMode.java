package com.github.cocosoys.mc.soyshttpovermc.web.http;

/**
 * HTTP 后端传输模式枚举。
 *
 * <p>定义四种 HTTP 请求从网关层到业务处理层的传输方式：
 * <ul>
 *   <li>{@link #DIRECT} —— 直接调用 WebFrontendHandler（同进程，最低延迟，阻塞 IO 线程）</li>
 *   <li>{@link #NETTY_EVENTLOOP} —— 提交到独立 Netty EventLoop 处理（默认，推荐）</li>
 *   <li>{@link #MEMORY_QUEUE} —— 提交到内存队列，worker 线程处理（支持背压）</li>
 *   <li>{@link #STANDALONE_SERVER} —— 独立端口 Netty HTTP 服务器（不占用 MC 端口，无嗅探器）</li>
 * </ul>
 *
 * <p>前三种用于同端口嗅探模式（SocketSniffer），第四种为独立服务器模式。
 * 默认使用 {@link #NETTY_EVENTLOOP}。</p>
 */
public enum HttpBackendMode {

    /** 直接调用：在 Netty IO 线程直接调用 WebFrontendHandler，延迟最低但阻塞 IO 线程。 */
    DIRECT("direct"),

    /** Netty EventLoop（默认）：提交到独立 Netty EventLoop 处理，延迟低且不阻塞 IO 线程。 */
    NETTY_EVENTLOOP("netty-eventloop"),

    /** 内存队列：提交到有界 ArrayBlockingQueue，worker 线程处理，支持背压。 */
    MEMORY_QUEUE("memory-queue"),

    /** 独立 HTTP 服务器：在独立端口启动 Netty HTTP 服务器，不占用 MC 端口，无需嗅探器。 */
    STANDALONE_SERVER("standalone-server");

    private final String configName;

    HttpBackendMode(String configName) {
        this.configName = configName;
    }

    public String configName() {
        return configName;
    }

    /** 从配置字符串解析模式，无法识别时返回默认 NETTY_EVENTLOOP。 */
    public static HttpBackendMode from(String name) {
        if (name == null || name.trim().isEmpty()) {
            return NETTY_EVENTLOOP;
        }
        String lower = name.trim().toLowerCase().replace('_', '-').replace(' ', '-');
        for (HttpBackendMode mode : values()) {
            if (mode.configName.equals(lower) || mode.name().toLowerCase().equals(lower)) {
                return mode;
            }
        }
        return NETTY_EVENTLOOP;
    }

    /** 是否使用同端口嗅探器（除 standalone-server 外都需要 SocketSniffer）。 */
    public boolean usesSniffer() {
        return this != STANDALONE_SERVER;
    }
}
