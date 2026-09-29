package com.github.cocosoys.mc.soyshttpovermc.spring.entity.vo;

import com.github.cocosoys.mc.soyshttpovermc.web.contract.ContractInjector;
import lombok.Data;

/**
 * 前端契约注入对象（__SOYS_CONTEXT__.js 的 JSON 原语）。
 *
 * <p>由 {@link ContractInjector#buildContextJson} 构造并序列化，键名即字段名（camelCase，
 * 与前端契约约定一一对应）。仅含部署环境原语，不含 origin 拼接、不含群组服 serverPrefix；
 * 值在响应时按当前服务器实际配置推导（换服务器 / 改 api-prefix 自动适配）。
 */
@Data
public class SoysContextContractVO {

    /**
     * 传输协议（http / https，按 TLS 是否启用推导）。
     */
    private String scheme;

    /**
     * 服务器地址（host，不含端口；public-host → mc.host → server-ip 回退）。
     */
    private String host;

    /**
     * 服务器端口（public-port → mc.port → server-port 回退）。
     */
    private int port;

    /**
     * API 全局前缀（config.yml api-prefix，默认 /api）。
     */
    private String apiPrefix;

    /**
     * 插件页面前缀（主插件为空串；附属插件 = /plugins/&lt;插件名&gt;）。
     */
    private String pluginsPrefix;

    /**
     * API 完整前缀（apiPrefix + pluginsPrefix）。
     */
    private String apiFullPrefix;

    /**
     * 页面完整前缀（主插件为空串；附属插件 = /web/plugins/&lt;插件名&gt;）。
     */
    private String pageFullPrefix;

    /**
     * jar 内资源路径前缀（/web/plugins/&lt;插件名&gt;/page，非 URL，极简登记用）。
     */
    private String webResourcePrefix;

    /**
     * SPA 回退声明状态（前端据此决定 history/hash 模式；未注入注册表视为未声明）。
     */
    private boolean spaFallback;

    /**
     * vue-router base（主插件 = "/"；附属插件 = pageFullPrefix + "/"）。
     */
    private String pageBase;

    /**
     * 设备指纹双因子开关（auth.yml auto.login.fp.*）。
     */
    private boolean fpEnabled;

    /**
     * 设备指纹严格模式（strict 绑定）。
     */
    private boolean fpStrict;
}
