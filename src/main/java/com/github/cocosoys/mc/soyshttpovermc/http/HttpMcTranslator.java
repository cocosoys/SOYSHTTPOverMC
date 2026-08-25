package com.github.cocosoys.mc.soyshttpovermc.http;
import com.github.cocosoys.mc.soyshttpovermc.enums.BotTier;

import com.github.cocosoys.mc.soyshttpovermc.bot.BotRuleController;

import com.github.cocosoys.mc.soyshttpovermc.cross.CrossServerHub;
import com.github.cocosoys.mc.soyshttpovermc.link.McLink;
import com.github.cocosoys.mc.soyshttpovermc.proto.FrameProto;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class HttpMcTranslator {

    private final McLink mcLink;
    private final BotRuleController ruleController;
    /** 本服在群组服中的服务器名（独立服为空，不参与路由） */
    private String localServerName = "";

    public HttpMcTranslator(McLink mcLink, BotRuleController ruleController) {
        this.mcLink = mcLink;
        this.ruleController = ruleController;
    }

    public void setLocalServerName(String name) {
        this.localServerName = name == null ? "" : name;
    }

    /**
     * 拆解跨服路径前缀：群组服下把 {@code /server/<name>/<rest>}
     * 拆为 {@code [name, "/<rest>"]}；独立服（localServerName 为空）或无前缀时返回 {@code [null, path]}。
     */
    private String[] splitCrossPath(String path) {
        String realPath = path == null ? "/" : path;
        if (localServerName == null || localServerName.isEmpty()) {
            return new String[]{null, realPath};
        }
        String prefix = null;
        if (realPath.startsWith("/server/")) prefix = "/server/";
        if (prefix == null) return new String[]{null, realPath};
        int base = prefix.length();
        int i = realPath.indexOf('/', base);
        String name = (i < 0) ? realPath.substring(base) : realPath.substring(base, i);
        String rest = (i < 0 || i + 1 >= realPath.length()) ? "/" : realPath.substring(i);
        return new String[]{name.isEmpty() ? null : name, rest};
    }

    /**
     * 网关策略匹配用路径：群组服下剥掉跨服前缀后的真实业务路径，并<b>剥除 query</b>
     * （策略豁免/路径规则按路径匹配，query 不参与；否则 {@code /api/auth/login?ticket=..}
     * 等带参请求会因豁免模式 {@code /auth/login} 匹配失败而被误拒 401）。
     * <p>安全要点：网关的 {@code paths: [/api/*]} 等规则必须匹配这个路径，否则
     * {@code /server/<name>/api/**} 会因前缀不匹配而绕过鉴权/限流等策略。
     */
    public String policyPath(String path) {
        String p = splitCrossPath(path)[1];
        int q = p == null ? -1 : p.indexOf('?');
        return q >= 0 ? p.substring(0, q) : p;
    }

    /**
     * 把一次 HTTP 请求转换为 HttpRequestFrame，经由 McLink 发往服务端，
     * 阻塞等待对应的 HttpResponseFrame 返回（30s 超时保护）。
     * 规则控制器按请求头决定逻辑队列 tier，并以内部头 X-SOYS-TIER 携带，供服务端按优先级入队。
     * 群组服下：解析跨服路径前缀 {@code /server/<server>/...}
     * 设置目标服（X-Soys-Target-Server），并注入来源服（X-Soys-Source-Server）与链路追踪（X-Soys-Trace-Id）头。
     * 独立服（localServerName 为空）不解析此前缀，路径原样本地处理（特性关闭）。
     */
    public FrameProto.HttpResponseFrame translate(String method, String path, Map<String, String> headers, byte[] body)
            throws Exception {
        BotTier tier = ruleController.selectTier(headers);
        // 跨服路由（仅群组服参与）：/server/<server>/<rest>（前缀）
        // -> target=<server>，path 重写为 /<rest>。独立服(localServerName 为空)下不解析此前缀，
        // 路径原样走本地处理（即"单服模式该特性默认关闭"）。
        String[] split = splitCrossPath(path);
        String target = split[0];
        String realPath = split[1];
        // 注入内部头，标记该请求应进入的逻辑队列（服务端据此优先级入队）
        Map<String, String> h = new HashMap<>(headers);
        h.put("X-SOYS-TIER", tier.name());
        if (localServerName != null && !localServerName.isEmpty()) {
            h.put(CrossServerHub.HEADER_SOURCE, localServerName);
            h.put(CrossServerHub.HEADER_TRACE, CrossServerHub.newTraceId());
        }
        if (target != null) {
            h.put(CrossServerHub.HEADER_TARGET, target);
        }
        FrameProto.HttpRequestFrame req = FrameProto.HttpRequestFrame.newBuilder()
                .setRequestId(mcLink.nextRequestId())
                .setMethod(method)
                .setPath(realPath)
                .putAllHeaders(h)
                .setBody(com.google.protobuf.ByteString.copyFrom(body))
                .setFragmentIndex(0)
                .setTotalFragments(1)
                .build();
        return mcLink.request(req).get(30, TimeUnit.SECONDS);
    }
}
