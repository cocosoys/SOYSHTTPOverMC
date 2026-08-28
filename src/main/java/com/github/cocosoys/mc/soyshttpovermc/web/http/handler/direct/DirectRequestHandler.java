package com.github.cocosoys.mc.soyshttpovermc.web.http.handler.direct;

import com.github.cocosoys.mc.soyshttpovermc.web.WebFrontendHandler;
import com.github.cocosoys.mc.soyshttpovermc.web.http.AbstractHttpRequestHandler;
import com.github.cocosoys.mc.soyshttpovermc.web.proto.FrameProto;

import java.util.Map;

/**
 * 直接调用模式：在当前线程（Netty IO 线程）直接调用 WebFrontendHandler.handle()。
 *
 * <p>延迟最低（<1ms），但会阻塞 Netty IO 线程，高并发下可能影响其他连接的处理。
 * 适用于低并发、追求最低延迟的场景。</p>
 */
public class DirectRequestHandler extends AbstractHttpRequestHandler {

    public DirectRequestHandler(WebFrontendHandler web) {
        super(web);
    }

    @Override
    protected FrameProto.HttpResponseFrame handleLocal(String method, String path,
                                                          Map<String, String> headers, byte[] body) {
        return web.handle(method, path, headers, body);
    }

    @Override
    public String name() {
        return "direct";
    }
}
