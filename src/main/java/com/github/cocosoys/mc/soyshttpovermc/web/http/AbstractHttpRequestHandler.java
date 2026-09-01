package com.github.cocosoys.mc.soyshttpovermc.web.http;

import com.github.cocosoys.mc.soyshttpovermc.web.WebFrontendHandler;
import com.github.cocosoys.mc.soyshttpovermc.web.proto.FrameProto;

import java.util.Map;

/**
 * HTTP 请求处理器抽象基类。
 *
 * <p>子类只需实现 {@link #handleLocal(String, String, Map, byte[])} 方法处理本地请求。
 */
public abstract class AbstractHttpRequestHandler implements HttpRequestHandler {

    protected final WebFrontendHandler web;

    protected AbstractHttpRequestHandler(WebFrontendHandler web) {
        this.web = web;
    }

    @Override
    public FrameProto.HttpResponseFrame handle(String method, String path, Map<String, String> headers, byte[] body)
            throws Exception {
        return handleLocal(method, path, headers, body);
    }

    /**
     * 处理本地请求（由子类实现）。
     */
    protected abstract FrameProto.HttpResponseFrame handleLocal(String method, String path,
                                                                Map<String, String> headers, byte[] body)
            throws Exception;

    @Override
    public String policyPath(String path) {
        return path;
    }
}
