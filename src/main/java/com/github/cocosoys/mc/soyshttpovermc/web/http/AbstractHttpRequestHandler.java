package com.github.cocosoys.mc.soyshttpovermc.web.http;

import com.github.cocosoys.mc.soyshttpovermc.proxy.ServerRegistry;
import com.github.cocosoys.mc.soyshttpovermc.web.WebFrontendHandler;
import com.github.cocosoys.mc.soyshttpovermc.web.proto.FrameProto;

import java.util.Map;

/**
 * HTTP 请求处理器抽象基类，包含跨服路由逻辑。
 *
 * <p>子类只需实现 {@link #handleLocal(String, String, Map, byte[])} 方法处理本地请求，
 * 跨服请求由基类自动检测并通过 {@link CrossServerHttpForwarder} 转发。
 *
 * <p>跨服请求路径格式：{@code /server/<server-name>/<original-path>}
 * 目标是本服时自动剥离前缀后本地处理；目标是其他服时通过 HTTP 客户端转发。
 */
public abstract class AbstractHttpRequestHandler implements HttpRequestHandler {

    protected final WebFrontendHandler web;
    protected final CrossServerHttpForwarder crossServerForwarder;
    protected final String localServerName;

    protected AbstractHttpRequestHandler(WebFrontendHandler web, ServerRegistry registry, String localServerName) {
        this.web = web;
        this.localServerName = localServerName == null ? "" : localServerName;
        this.crossServerForwarder = (registry != null)
                ? new CrossServerHttpForwarder(registry, this.localServerName)
                : null;
    }

    @Override
    public FrameProto.HttpResponseFrame handle(String method, String path, Map<String, String> headers, byte[] body)
            throws Exception {
        // 跨服路由检测
        if (crossServerForwarder != null && crossServerForwarder.isCrossServerRequest(path)) {
            String target = crossServerForwarder.parseTargetServer(path);
            // 目标是本服，剥离前缀后本地处理
            if (target != null && target.equals(localServerName)) {
                String localPath = crossServerForwarder.stripPrefix(path);
                return handleLocal(method, localPath, headers, body);
            }
            // 目标是其他服，通过 HTTP 转发
            return crossServerForwarder.forward(method, path, headers, body);
        }
        // 本地请求
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
        // 跨服请求的策略路径：剥离 /server/<name>/ 前缀，使网关策略按实际路径匹配
        if (crossServerForwarder != null && crossServerForwarder.isCrossServerRequest(path)) {
            return crossServerForwarder.stripPrefix(path);
        }
        return path;
    }
}
