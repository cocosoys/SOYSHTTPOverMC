package soys.soyshttpovermc.http;

import soys.soyshttpovermc.link.McLink;
import soys.soyshttpovermc.proto.FrameProto;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class HttpMcTranslator {

    private final McLink mcLink;

    public HttpMcTranslator(McLink mcLink) {
        this.mcLink = mcLink;
    }

    /**
     * 把一次 HTTP 请求转换为 HttpRequestFrame，经由 McLink 发往服务端，
     * 阻塞等待对应的 HttpResponseFrame 返回（第一版无超时保护，仅做基本上限）。
     */
    public FrameProto.HttpResponseFrame translate(String method, String path, Map<String, String> headers, byte[] body)
            throws Exception {
        FrameProto.HttpRequestFrame req = FrameProto.HttpRequestFrame.newBuilder()
                .setRequestId(mcLink.nextRequestId())
                .setMethod(method)
                .setPath(path)
                .putAllHeaders(headers)
                .setBody(com.google.protobuf.ByteString.copyFrom(body))
                .setFragmentIndex(0)
                .setTotalFragments(1)
                .build();
        return mcLink.request(req).get(30, TimeUnit.SECONDS);
    }
}
