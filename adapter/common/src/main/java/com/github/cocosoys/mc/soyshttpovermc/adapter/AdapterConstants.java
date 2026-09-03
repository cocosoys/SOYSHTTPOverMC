package com.github.cocosoys.mc.soyshttpovermc.adapter;

/**
 * 版本兼容模块统一常量。
 *
 * <p>所有版本模块（v1_6x / v1_7x…）与公共底座共用的约定值，避免散落魔法字符串。</p>
 */
public final class AdapterConstants {

    private AdapterConstants() {
    }

    /** 插件名（与 plugin.yml / 主工程一致）。 */
    public static final String PLUGIN_NAME = "SOYSHTTPOverMC";

    /** 兼容模块包根（统一规范，见 README.md）。 */
    public static final String PACKAGE_ROOT = "com.github.cocosoys.mc.soyshttpovermc.adapter";

    /** 注解式 API 全局前缀（与主工程 gateway/config.yml 的 api-prefix 保持一致）。 */
    public static final String API_PREFIX = "/api";

    /** 同端口嗅探器在父 Channel pipeline 上的处理器名（与主工程 SocketSniffer 约定一致）。 */
    public static final String SNIFFER_PARENT_HANDLER = "http-over-mc-parent";

    /** 嗅探器在子连接 pipeline 上的处理器名。 */
    public static final String SNIFFER_CHILD_HANDLER = "http-over-mc-sniffer";

    /**
     * 按统一产物命名规则生成 jar 名：{@code SOYSHTTPOverMC-<版本段>-<revision>.jar}。
     *
     * @param versionSegment 版本段，如 {@code 1_6} / {@code 1_7}
     * @param revision       Maven revision，如 {@code 1.2.0}
     * @return 如 {@code SOYSHTTPOverMC-1_6-1.2.0.jar}
     */
    public static String artifactName(String versionSegment, String revision) {
        return PLUGIN_NAME + "-" + versionSegment + "-" + revision + ".jar";
    }
}
