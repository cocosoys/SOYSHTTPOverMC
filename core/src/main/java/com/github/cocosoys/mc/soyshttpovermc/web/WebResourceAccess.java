package com.github.cocosoys.mc.soyshttpovermc.web;

import java.util.List;
import java.util.Map;

/**
 * web 资源访问上下文：客户端请求命中已登记 web 资源（插件登记网页 / 网络页 / 静态资源）时构造，
 * 由 {@link com.github.cocosoys.mc.soyshttpovermc.api.event.WebResourcesEvent.WebResourcesAccessEvent}
 * 经 Bukkit 事件总线携带给监听器。
 *
 * <p><b>copy 语义</b>：本对象为每次请求新建的快照——{@link #accessed()} 与 {@link #current()}
 * 均为值拷贝（不暴露注册表内部），监听器修改不会影响已登记内容；{@link #result()} 为独立可写对象，
 * 仅影响本次响应。</p>
 *
 * <p><b>线程</b>：在 HTTP worker 线程触发（与 {@code GatewayRequestEvent} 异步事件一致），
 * 如需主线程操作请自行调度（如 {@code Bukkit.getScheduler().runTask}）。</p>
 */
public final class WebResourceAccess {

    private final String method;
    private final Map<String, String> headers;
    private final String path;
    private final String query;
    private final List<ResourceAccess> accessed;
    private final ResourceAccess current;
    private final ResourceAccessResult result;

    /**
     * @param accessed 本次请求已访问的所有资源快照列表（请求级累积；单请求通常 1 项，保留链式扩展）
     */
    public WebResourceAccess(String method, Map<String, String> headers, String path, String query,
                             List<ResourceAccess> accessed, ResourceAccess current, ResourceAccessResult result) {
        this.method = method;
        this.headers = headers;
        this.path = path;
        this.query = query;
        this.accessed = accessed == null ? java.util.Collections.<ResourceAccess>emptyList() : accessed;
        this.current = current;
        this.result = result;
    }

    /**
     * HTTP 方法（大写，如 GET / POST）。
     */
    public String method() {
        return method;
    }

    /**
     * 请求头（原始 Map 快照引用；只读约定，勿修改）。
     */
    public Map<String, String> headers() {
        return headers;
    }

    /**
     * 请求路径（去 query 的 cleanPath，如 /plugins/Foo/index.html）。
     */
    public String path() {
        return path;
    }

    /**
     * 原始 query 串（不含 ?；无 query 为 null）。
     */
    public String query() {
        return query;
    }

    /**
     * <b>所有被请求的资源列表</b>：本次请求已访问的资源快照（正常 1 项；监听器链式改写路径时累积）。
     */
    public List<ResourceAccess> accessed() {
        return accessed;
    }

    /**
     * <b>当前被请求的单个资源</b>（= accessed 末尾项；无资源时 null）。
     */
    public ResourceAccess current() {
        return current;
    }

    /**
     * 可写结果：redirect（跳转拦截）/ deny（拒绝）/ replace（替换内容）；调用后短路本次请求。
     */
    public ResourceAccessResult result() {
        return result;
    }
}
