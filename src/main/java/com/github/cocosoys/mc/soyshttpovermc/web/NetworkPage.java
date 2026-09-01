package com.github.cocosoys.mc.soyshttpovermc.web;

/**
 * 网络文件/网络网页页面抽象：表示一个<b>按需加载</b>（可能经网络传输）的页面/文件。
 *
 * <p>开发者实现本抽象并调用 {@code WebPageApi.registerNetworkPage(owner, page)} 注册后，
 * 访问 {@link #path()} 时网关调用 {@link #load()} 获取内容字节（Content-Type 取
 * {@link #contentType()}，未指定则按 path 扩展名推断）。典型用途：<b>自定义加密传输</b>——
 * 开发者在 {@link #load()} 内完成 拉取 + 解密 + 验签，网关不感知传输细节。</p>
 *
 * <p>缓存：{@link #cacheTtlSeconds()} 返回 &gt;0 时网关缓存内容（TTL 内不重复 load）；
 * 返回 &lt;=0 则每次请求都调用 {@link #load()}（开发者自控缓存/时效）。</p>
 */
public abstract class NetworkPage {

    /**
     * 唯一名称（日志/调试用）。
     */
    public abstract String name();

    /**
     * 访问路径（如 {@code /remote/main}；注册时自动补 {@code /plugins/&lt;插件名&gt;} 前缀，
     * 与普通登记页一致；支持 .html 后缀智能匹配）。
     */
    public abstract String path();

    /**
     * 按需加载内容（网络传输发生于此，可含解密/验签；失败抛异常 → 网关返回 502 JSON）。
     * 返回 null/空字节同样按失败处理。
     */
    public abstract byte[] load() throws Exception;

    /**
     * Content-Type（null=按 path 扩展名推断）。
     */
    public String contentType() {
        return null;
    }

    /**
     * 内容缓存 TTL（秒；&lt;=0 = 不缓存，每次请求重新 load）。
     */
    public long cacheTtlSeconds() {
        return 60;
    }
}
