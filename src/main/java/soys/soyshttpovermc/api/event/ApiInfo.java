package soys.soyshttpovermc.api.event;

import java.io.Serializable;

/**
 * 单个 API 端点的静态信息（注册 / 卸载事件的载体）：
 * HTTP 方法 + 路径 + 端点名 + 所需权限 + 处理器类名 + 注册它的插件名。
 *
 * <p>该对象是只读快照，便于监听方做路由审计、自动文档、权限联动等，
 * 不持有处理器实例引用，避免插件卸载后内存泄漏。</p>
 */
public class ApiInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String httpMethod;
    private final String path;
    private final String apiName;
    private final String permission;
    private final String handlerClass;
    private final String ownerPlugin;

    public ApiInfo(String httpMethod, String path, String apiName,
                   String permission, String handlerClass, String ownerPlugin) {
        this.httpMethod = httpMethod == null ? "" : httpMethod;
        this.path = path == null ? "/" : path;
        this.apiName = apiName == null ? "" : apiName;
        this.permission = permission == null ? "" : permission;
        this.handlerClass = handlerClass == null ? "" : handlerClass;
        this.ownerPlugin = ownerPlugin == null ? "" : ownerPlugin;
    }

    /** HTTP 方法（GET/POST/... 或 * 表示不限定方法） */
    public String getHttpMethod() {
        return httpMethod;
    }

    /** 实际挂载路径（含网关自动添加的 /api 前缀，如 /api/ping） */
    public String getPath() {
        return path;
    }

    /** 端点展示名（@ApiName，缺省为处理器类名） */
    public String getApiName() {
        return apiName;
    }

    /** 所需权限（@ApiPermission，未声明为空字符串） */
    public String getPermission() {
        return permission;
    }

    /** 处理器类的全限定名 */
    public String getHandlerClass() {
        return handlerClass;
    }

    /** 注册该 API 的插件名（由 ApiRegistry 自动标记） */
    public String getOwnerPlugin() {
        return ownerPlugin;
    }

    @Override
    public String toString() {
        return httpMethod + " " + path + " [" + apiName + "] owner=" + ownerPlugin
                + (permission.isEmpty() ? "" : " perm=" + permission);
    }
}
