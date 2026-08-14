package soys.soyshttpovermc.api;

import soys.soyshttpovermc.HttpResponse;

import java.util.Map;

/**
 * 能力组 7：HTTP 请求（对外真实请求 + 对内回环调本插件 API）。
 * 由 {@link SoysHttpOverMcApi#getHttpClient()} 跳转获取。
 */
public interface HttpClientApi {

    /** 对外发起真实 HTTP 请求（任意方法/URL/头/体），返回状态码+响应头+响应体；连接失败抛 {@code HttpClientException} */
    HttpResponse sendHttp(String method, String url, Map<String, String> headers, byte[] body);

    HttpResponse sendGet(String url);

    HttpResponse sendGet(String url, Map<String, String> headers);

    HttpResponse sendPost(String url, byte[] body);

    HttpResponse sendPost(String url, Map<String, String> headers, byte[] body);

    /** 对内回环：直接调用本插件自身已注册的 API（绕过网络，等价于本地分发）；分发异常抛 {@code HttpClientException} */
    Object callLocalApi(String method, String path, Map<String, String> headers, byte[] body);
}
