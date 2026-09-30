package com.github.cocosoys.mc.soyshttpovermc.web.swagger;

import com.github.cocosoys.mc.soyshttpovermc.web.ApiRegistry;
import com.github.cocosoys.mc.soyshttpovermc.web.NetworkPage;

import java.nio.charset.StandardCharsets;

/**
 * OpenAPI 文档动态页面（{@code /swagger/api-docs}）。
 *
 * <p>每次请求实时调用 {@link SwaggerDocument#build} 反射当前路由表生成 JSON，
 * 因此 API 注册 / 注销（含附属插件 SoysExpansion 生命周期）后文档自动保持一致；
 * {@link #cacheTtlSeconds()} 返回 0，不做网关侧缓存。
 *
 * <p>访问受 {@link SwaggerGuardInterceptor} 保护（仅已登录的服务器 OP 可见）。
 */
public final class SwaggerApiDocsPage extends NetworkPage {

    private final ApiRegistry registry;
    private final String version;

    /**
     * @param registry 主插件 ApiRegistry（路由表快照来源）
     * @param version  插件版本号（文档 info.version）
     */
    public SwaggerApiDocsPage(ApiRegistry registry, String version) {
        this.registry = registry;
        this.version = version;
    }

    @Override
    public String name() {
        return "swagger-api-docs";
    }

    @Override
    public String path() {
        return "/swagger/api-docs";
    }

    @Override
    public byte[] load() throws Exception {
        return SwaggerDocument.build(registry, version).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String contentType() {
        return "application/json";
    }

    @Override
    public long cacheTtlSeconds() {
        return 0; // 动态生成：每次请求实时反映当前路由表
    }
}
