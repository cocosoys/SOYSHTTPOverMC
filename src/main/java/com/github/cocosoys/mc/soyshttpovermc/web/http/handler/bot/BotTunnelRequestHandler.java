package com.github.cocosoys.mc.soyshttpovermc.web.http.handler.bot;

import com.github.cocosoys.mc.soyshttpovermc.web.http.HttpMcTranslator;
import com.github.cocosoys.mc.soyshttpovermc.web.http.HttpRequestHandler;
import com.github.cocosoys.mc.soyshttpovermc.web.proto.FrameProto;

import java.util.Map;

/**
 * Bot 隧道模式（兼容旧模式）：经 HttpMcTranslator 将 HTTP 请求转换为 PluginMessage，
 * 通过 InternalBot 回环发送到服务端，再由 McMessageHandler + RequestScheduler 处理。
 *
 * <p>这是 SOYSHTTPOverMC 最初的传输方式，延迟较高（100ms+），因为需要经过两次 Bukkit 主线程调度。
 * 保留此模式仅用于向后兼容和跨服路由（BungeeCord/Velocity 场景）。</p>
 */
public class BotTunnelRequestHandler implements HttpRequestHandler {

    private final HttpMcTranslator translator;

    public BotTunnelRequestHandler(HttpMcTranslator translator) {
        this.translator = translator;
    }

    @Override
    public FrameProto.HttpResponseFrame handle(String method, String path, Map<String, String> headers, byte[] body)
            throws Exception {
        return translator.translate(method, path, headers, body);
    }

    @Override
    public String policyPath(String path) {
        return translator.policyPath(path);
    }

    @Override
    public String name() {
        return "bot-tunnel";
    }
}
