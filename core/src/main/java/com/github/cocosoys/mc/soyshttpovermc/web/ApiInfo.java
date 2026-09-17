package com.github.cocosoys.mc.soyshttpovermc.web;

import lombok.Getter;

import java.io.Serializable;

/**
 * 单个 API 端点的静态信息（注册 / 卸载事件的载体）：
 * HTTP 方法 + 路径 + 端点名 + 所需权限 + 处理器类名 + 注册它的插件名。
 *
 * <p>该对象是只读快照，便于监听方做路由审计、自动文档、权限联动等，
 * 不持有处理器实例引用，避免插件卸载后内存泄漏。</p>
 */
@Getter
public class ApiInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * HTTP 方法（GET/POST/... 或 * 表示不限定方法）
     */
    private final String httpMethod;
    /**
     * 实际挂载路径（含网关自动添加的 /api 前缀，如 /api/ping）
     */
    private final String path;
    /**
     * 端点展示名（@ApiName，缺省为处理器类名）
     */
    private final String apiName;
    /**
     * 所需权限（@ApiPermission，未声明为空字符串）
     */
    private final String permission;
    /**
     * 处理器类的全限定名
     */
    private final String handlerClass;
    /**
     * 注册该 API 的插件名（由 ApiRegistry 自动标记）
     */
    private final String ownerPlugin;
    /**
     * 是否从 API 清单 / 自动文档中隐藏（@Hidden）
     */
    private final boolean hidden;
    /**
     * 是否已废弃（SOYS @Deprecated，区别于 JDK 注解）
     */
    private final boolean deprecated;

    public ApiInfo(String httpMethod, String path, String apiName,
                   String permission, String handlerClass, String ownerPlugin) {
        this(httpMethod, path, apiName, permission, handlerClass, ownerPlugin, false, null);
    }
    /**
     * 全量构造（含元数据）：hidden/deprecated 由 {@code @Hidden}/{@code @Deprecated} 注解解析而来。
     */
    public ApiInfo(String httpMethod, String path, String apiName,
                   String permission, String handlerClass, String ownerPlugin,
                   boolean hidden, com.github.cocosoys.mc.soyshttpovermc.annotations.Deprecated deprecated) {
        this.httpMethod = httpMethod == null ? "" : httpMethod;
        this.path = path == null ? "/" : path;
        this.apiName = apiName == null ? "" : apiName;
        this.permission = permission == null ? "" : permission;
        this.handlerClass = handlerClass == null ? "" : handlerClass;
        this.ownerPlugin = ownerPlugin == null ? "" : ownerPlugin;
        this.hidden = hidden;
        this.deprecated = deprecated != null;
    }

    @Override
    public String toString() {
        return httpMethod + " " + path + " [" + apiName + "] owner=" + ownerPlugin
                + (permission.isEmpty() ? "" : " perm=" + permission);
    }
}
