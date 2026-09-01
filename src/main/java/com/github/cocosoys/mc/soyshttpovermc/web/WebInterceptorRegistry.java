package com.github.cocosoys.mc.soyshttpovermc.web;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 请求级拦截器注册中心（有序执行，按注册顺序）。
 * WebFrontendHandler 在网关策略之后、业务路由之前调用。
 */
public class WebInterceptorRegistry {

    private final List<WebInterceptor> interceptors = new CopyOnWriteArrayList<>();

    /**
     * 注册一个拦截器（同名覆盖：先移除同名再追加，保证顺序可控）。
     */
    public void register(WebInterceptor interceptor) {
        if (interceptor == null || interceptor.name() == null) return;
        interceptors.removeIf(i -> i.name() != null && i.name().equals(interceptor.name()));
        interceptors.add(interceptor);
    }

    /**
     * 注销（按名称）。
     */
    public boolean unregister(String name) {
        return interceptors.removeIf(i -> i.name() != null && i.name().equals(name));
    }

    /**
     * 当前拦截器列表（只读快照）。
     */
    public List<WebInterceptor> all() {
        return java.util.Collections.unmodifiableList(interceptors);
    }

    public boolean isEmpty() {
        return interceptors.isEmpty();
    }
}
