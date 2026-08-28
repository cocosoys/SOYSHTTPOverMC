package com.github.cocosoys.mc.soyshttpovermc.web.http;

import com.github.cocosoys.mc.soyshttpovermc.web.http.handler.bot.BotTunnelRequestHandler;
import com.github.cocosoys.mc.soyshttpovermc.web.http.handler.direct.DirectRequestHandler;
import com.github.cocosoys.mc.soyshttpovermc.web.http.handler.memory.MemoryQueueRequestHandler;
import com.github.cocosoys.mc.soyshttpovermc.web.http.handler.netty.NettyEventLoopRequestHandler;
import com.github.cocosoys.mc.soyshttpovermc.web.proto.FrameProto;

import java.util.Map;

/**
 * HTTP 请求后端处理器接口。
 *
 * <p>定义 HTTP 请求从网关层到业务处理层的传输方式。四种实现：
 * <ul>
 *   <li>{@link DirectRequestHandler} —— 直接调用 WebFrontendHandler（同进程，最低延迟）</li>
 *   <li>{@link NettyEventLoopRequestHandler} —— 提交到独立 Netty EventLoop 处理（默认）</li>
 *   <li>{@link MemoryQueueRequestHandler} —— 提交到内存队列，worker 线程处理</li>
 *   <li>{@link BotTunnelRequestHandler} —— 经 Bot 隧道 + PluginMessage 传输（兼容旧模式）</li>
 * </ul>
 *
 * <p>前三种用于同端口嗅探模式（SocketSniffer），第四种为兼容旧的 Bot 隧道模式。
 * 独立 HTTP 服务器模式（{@link StandaloneHttpServer}）不实现此接口，因为它是独立的网络入口。</p>
 */
public interface HttpRequestHandler {

    /**
     * 处理一次 HTTP 请求，返回响应帧。
     *
     * @param method  HTTP 方法（GET/POST 等）
     * @param path    请求路径（含 query string）
     * @param headers 请求头
     * @param body    请求体（可能为空）
     * @return HTTP 响应帧
     * @throws Exception 处理过程中的异常
     */
    FrameProto.HttpResponseFrame handle(String method, String path, Map<String, String> headers, byte[] body)
            throws Exception;

    /** 关闭后端处理器，释放资源（线程池、连接等）。 */
    default void shutdown() {}

    /** 后端模式名称（用于日志和状态显示）。 */
    String name();

    /**
     * 网关策略路径转换。默认返回原路径；Bot 隧道模式需覆盖此方法，
     * 将外部路径转换为隧道内部路径（如 /api/xxx → /tunnel/api/xxx）。
     */
    default String policyPath(String path) {
        return path;
    }
}
