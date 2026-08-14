package soys.soyshttpovermc.http;

import soys.soyshttpovermc.bot.BotRuleController;
import soys.soyshttpovermc.bot.BotTier;
import soys.soyshttpovermc.link.McLink;
import soys.soyshttpovermc.proto.FrameProto;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class HttpMcTranslator {

    private final McLink mcLink;
    private final BotRuleController ruleController;

    public HttpMcTranslator(McLink mcLink, BotRuleController ruleController) {
        this.mcLink = mcLink;
        this.ruleController = ruleController;
    }

    /**
     * 把一次 HTTP 请求转换为 HttpRequestFrame，经由 McLink 发往服务端，
     * 阻塞等待对应的 HttpResponseFrame 返回（30s 超时保护）。
     * 规则控制器按请求头决定逻辑队列 tier，并以内部头 X-SOYS-TIER 携带，供服务端按优先级入队。
     */
    public FrameProto.HttpResponseFrame translate(String method, String path, Map<String, String> headers, byte[] body)
            throws Exception {
        BotTier tier = ruleController.selectTier(headers);
        // 注入内部头，标记该请求应进入的逻辑队列（服务端据此优先级入队）
        Map<String, String> h = new HashMap<>(headers);
        h.put("X-SOYS-TIER", tier.name());
        FrameProto.HttpRequestFrame req = FrameProto.HttpRequestFrame.newBuilder()
                .setRequestId(mcLink.nextRequestId())
                .setMethod(method)
                .setPath(path)
                .putAllHeaders(h)
                .setBody(com.google.protobuf.ByteString.copyFrom(body))
                .setFragmentIndex(0)
                .setTotalFragments(1)
                .build();
        return mcLink.request(req).get(30, TimeUnit.SECONDS);
    }
}
