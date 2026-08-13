package soys.soyshttpovermc.gateway;

import java.util.Collections;
import java.util.Map;

/**
 * 单次请求的网关上下文：策略链各环节共享的只读信息。
 * 携带方法/路径/请求头、socket 源 IP、是否 TLS 连接，供策略判定使用。
 */
public class GatewayContext {

    private final String method;
    private final String path;
    private final Map<String, String> headers;
    private final String socketIp;
    private final boolean tls;
    private final Credential credential; // 经网关鉴权解析出的已认证主体（null = 未携带有效凭证）

    public GatewayContext(String method, String path, Map<String, String> headers, String socketIp, boolean tls) {
        this(method, path, headers, socketIp, tls, null);
    }

    public GatewayContext(String method, String path, Map<String, String> headers,
                          String socketIp, boolean tls, Credential credential) {
        this.method = method == null ? "" : method;
        this.path = path == null ? "/" : path;
        this.headers = headers == null ? Collections.<String, String>emptyMap() : headers;
        this.socketIp = socketIp == null ? "0.0.0.0" : socketIp;
        this.tls = tls;
        this.credential = credential;
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    /** TCP socket 上的源 IP（代理未配置时即客户端真实 IP） */
    public String getSocketIp() {
        return socketIp;
    }

    /** 连接是否已通过 TLS 解密（25564 就地升级后为 true） */
    public boolean isTls() {
        return tls;
    }

    /** 请求是否携带有效凭证（已通过鉴权）：TLS 策略据此决定是否旁路 HTTPS 强制升级。 */
    public boolean isAuthenticated() {
        return credential != null;
    }

    /** 已认证主体（null = 未携带有效凭证）。 */
    public Credential getCredential() {
        return credential;
    }

    /** 当前主体是否拥有某权限（预留权限控制抽象；当前有效凭证恒为 true）。 */
    public boolean hasPermission(String permission) {
        return credential != null && credential.hasPermission(permission);
    }

    /** 大小写不敏感读取请求头 */
    public String getHeader(String name) {
        if (name == null || headers.isEmpty()) return null;
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) return e.getValue();
        }
        return null;
    }
}
