package com.github.cocosoys.mc.soyshttpovermc.web.gateway;

/**
 * 匿名端点探测器（认证门 ↔ 注解式 API 注册表的桥接接口）：
 * 供 {@code AuthPolicy} 查询某请求路径是否命中 {@code @Anonymous} 注解端点，
 * 命中则认证门放行（免凭证），使「匿名」语义由注解单一声明。
 *
 * <p>由 {@code ApiRegistry} 实现并注入网关；gateway 层只依赖本接口，不感知具体注册表实现。</p>
 */
public interface AnonymousProbe {

    /**
     * 请求（方法 + 实际路径，含 /api 前缀，如 /api/prod-api/captchaImage）是否命中
     * 方法级或类级 {@code @Anonymous} 标注的端点。
     *
     * @param httpMethod HTTP 方法（GET/POST/...），大小写不敏感
     * @param path       实际请求路径（可能含 query，内部自动剥离）
     */
    boolean isAnonymous(String httpMethod, String path);
}
