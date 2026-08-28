package com.github.cocosoys.mc.soyshttpovermc.api.impl;

import com.github.cocosoys.mc.soyshttpovermc.web.HttpResponse;
import com.github.cocosoys.mc.soyshttpovermc.HttpOverMcPlugin;
import com.github.cocosoys.mc.soyshttpovermc.api.CrossServerHttpClient;
import com.github.cocosoys.mc.soyshttpovermc.proxy.cross.CrossServerHub;
import com.github.cocosoys.mc.soyshttpovermc.i18n.I18n;
import com.github.cocosoys.mc.soyshttpovermc.web.http.handler.bot.manager.link.McLink;
import com.github.cocosoys.mc.soyshttpovermc.web.proto.FrameProto;

import com.google.protobuf.ByteString;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 能力组 9 实现：跨服调用。经本服 McLink 隧道触发中继，目标服服务后响应原路回程。
 */
public class CrossServerHttpClientImpl implements CrossServerHttpClient {

    private final HttpOverMcPlugin plugin;
    private final McLink mcLink;

    public CrossServerHttpClientImpl(HttpOverMcPlugin plugin, McLink mcLink) {
        this.plugin = plugin;
        this.mcLink = mcLink;
    }

    @Override
    public HttpResponse callRemoteApi(String serverName, String method, String path,
                                      Map<String, String> headers, byte[] body) {
        if (serverName == null || serverName.isEmpty()) {
            throw new IllegalArgumentException(I18n.t("exception.cross.server-name-empty", "serverName 不能为空"));
        }
        String local = plugin.getServerName();
        String trace = CrossServerHub.newTraceId();
        Map<String, String> h = new HashMap<>(headers == null ? java.util.Collections.<String, String>emptyMap() : headers);
        if (local != null && !local.isEmpty()) {
            h.put(CrossServerHub.HEADER_SOURCE, local);
        }
        h.put(CrossServerHub.HEADER_TRACE, trace);
        h.put(CrossServerHub.HEADER_TARGET, serverName);
        try {
            FrameProto.HttpRequestFrame req = FrameProto.HttpRequestFrame.newBuilder()
                    .setRequestId(mcLink.nextRequestId())
                    .setMethod(method == null ? "GET" : method)
                    .setPath(path == null ? "/" : path)
                    .putAllHeaders(h)
                    .setBody(ByteString.copyFrom(body == null ? new byte[0] : body))
                    .setFragmentIndex(0)
                    .setTotalFragments(1)
                    .build();
            FrameProto.HttpResponseFrame resp = mcLink.request(req).get(30, TimeUnit.SECONDS);
            return toHttpResponse(resp);
        } catch (Exception e) {
            throw new RuntimeException(I18n.t("exception.cross.call-fail", "跨服调用 {0} {1} {2} 失败: {3}", serverName, method, path, e.getMessage()), e);
        }
    }

    @Override
    public HttpResponse sendGet(String serverName, String path) {
        return callRemoteApi(serverName, "GET", path, null, null);
    }

    @Override
    public HttpResponse sendPost(String serverName, String path, byte[] body) {
        return callRemoteApi(serverName, "POST", path, null, body);
    }

    private static HttpResponse toHttpResponse(FrameProto.HttpResponseFrame resp) {
        Map<String, String> hs = new HashMap<>();
        for (Map.Entry<String, String> e : resp.getHeadersMap().entrySet()) {
            // 剥离内部跨服头，避免泄漏给调用方
            if (e.getKey().startsWith("X-Soys-")) continue;
            hs.put(e.getKey().toLowerCase(), e.getValue());
        }
        return new HttpResponse(resp.getStatusCode(), hs, resp.getBody().toByteArray());
    }
}
