package soys.soyshttpovermc.api;

import soys.soyshttpovermc.HttpResponse;

import java.util.Map;

/**
 * 能力组 9：跨服调用（群组服下，插件间经本插件隧道调用另一子服的 API）。
 * <p>调用经本服无头 Bot 隧道回环到本服 {@code McMessageHandler}，由其按 {@code X-Soys-Target-Server}
 * 头中继到目标服；目标服服务后将响应原路回程，按 request_id 关联完成本次调用。
 * 与 {@link HttpClientApi} 的区别：{@code HttpClientApi} 走真实网络或本服回环；
 * 本接口走<b>群组服隧道</b>，目标服无需对外暴露端口。</p>
 */
public interface CrossServerHttpClient {

    /** 跨服调用目标服（serverName）的 API/path，返回响应。 */
    HttpResponse callRemoteApi(String serverName, String method, String path,
                                Map<String, String> headers, byte[] body);

    HttpResponse sendGet(String serverName, String path);

    HttpResponse sendPost(String serverName, String path, byte[] body);
}
