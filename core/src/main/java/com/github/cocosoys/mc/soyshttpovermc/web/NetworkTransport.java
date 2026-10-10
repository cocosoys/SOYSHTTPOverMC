package com.github.cocosoys.mc.soyshttpovermc.web;

/**
 * 网络传输实例化入口。
 *
 * <p>开发者经 {@code WebPageApi.registerNetworkTransport(transport)} 注入自定义传输
 * （加密 / 签名校验 / 私有协议）后，<b>首页远程拉取（{@code web.home} 网络 URL）</b>
 * 自动优先委托本传输器 {@link #fetch(String)}，失败回退默认 {@code HttpURLConnection} 直连。</p>
 *
 * <p>网络页（{@link NetworkPage}）仍由其 {@link NetworkPage#load()} 自行实现传输——
 * 开发者可在自己的 load() 内通过 {@link NetworkTransports#fetch(String)} 复用已注册传输器。</p>
 */
public interface NetworkTransport {

    /**
     * 传输提供者唯一名称（日志/调试用）。
     */
    String name();

    /**
     * 按 URL 获取内容字节（自定义协议/加密在实现内处理；失败抛异常）。
     */
    byte[] fetch(String url) throws Exception;
}
