package com.github.cocosoys.mc.soyshttpovermc.util;

import com.github.cocosoys.mc.soyshttpovermc.web.MimeTypes;
import com.github.cocosoys.mc.soyshttpovermc.web.proto.FrameProto;
import com.google.protobuf.ByteString;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 统一 HTTP 响应帧构造工具：收敛所有「JSON 成功/错误帧」与「302 跳转帧」的构造，
 * 消除后端散落的 HTML 字符串拼接（整改方案第 7 章）。
 *
 * <p>约定：所有错误/状态码响应体均为 {@link AjaxResult} 信封 {@code {code,msg,data}}，
 * 并携带真实 HTTP 状态码（401/403/404/426/429/500/502/503 等）；302 跳转同样输出
 * JSON 信封 + Location 头（浏览器原生跳转，无 text/plain 例外）。</p>
 */
public final class HttpFrames {

    private HttpFrames() {
    }

    /**
     * 通用 JSON 帧（application/json；可附加 Set-Cookie 等响应头）。
     */
    public static FrameProto.HttpResponseFrame json(int statusCode, AjaxResult ar) {
        return json(statusCode, ar, null);
    }

    /**
     * 通用 JSON 帧 + 附加响应头（如离线 cookie 升级的 Set-Cookie / X-Soys-New-Token）。
     */
    public static FrameProto.HttpResponseFrame json(int statusCode, AjaxResult ar, Map<String, String> extra) {
        FrameProto.HttpResponseFrame.Builder b = FrameProto.HttpResponseFrame.newBuilder()
                .setStatusCode(statusCode)
                .putHeaders("Content-Type", MimeTypes.forExt("json"))
                .setBody(ByteString.copyFrom(ar.toJson().getBytes(StandardCharsets.UTF_8)))
                .setFragmentIndex(0)
                .setTotalFragments(1);
        if (extra != null) {
            for (Map.Entry<String, String> e : extra.entrySet()) {
                b.putHeaders(e.getKey(), e.getValue());
            }
        }
        return b.build();
    }

    /**
     * 成功帧（200 + 自定义 msg）。
     */
    public static FrameProto.HttpResponseFrame jsonOk(String msg) {
        return json(200, AjaxResult.success(msg));
    }

    /**
     * 错误帧：真实 HTTP 状态码 + {@code {code:code,msg,...}} 信封（data=null）。
     */
    public static FrameProto.HttpResponseFrame jsonError(int code, String msg) {
        return json(code, AjaxResult.error(code, msg));
    }

    /**
     * 302 跳转帧（默认 302）：仅带 Location，浏览器原生跳转，body 为纯文本提示（非 HTML）。
     */
    public static FrameProto.HttpResponseFrame redirect(String loc) {
        return redirect(302, loc);
    }

    /**
     * 跳转帧（支持 301/302）：JSON 信封 body + Location 头，浏览器原生跳转（无 text/plain 例外）。
     */
    public static FrameProto.HttpResponseFrame redirect(int code, String loc) {
        int sc = code > 0 ? code : 302;
        String target = loc == null ? "/" : loc;
        return json(sc, new AjaxResult(sc, "Redirecting to " + target),
                java.util.Collections.singletonMap("Location", target));
    }
}
