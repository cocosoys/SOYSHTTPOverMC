package soys.soyshttpovermc;

import soys.soyshttpovermc.log.LogKit;

import soys.soyshttpovermc.annotations.*;
import soys.soyshttpovermc.api.event.ApiInfo;
import soys.soyshttpovermc.api.event.ApiRegisteredEvent;
import soys.soyshttpovermc.api.event.ApiUnregisteredEvent;
import soys.soyshttpovermc.util.AjaxResult;
import soys.soyshttpovermc.gateway.policy.auth.AuthUtils;
import soys.soyshttpovermc.gateway.policy.auth.issuer.CredentialPresentation;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 注解式 API 注册表与分发器（仿 Spring MVC / RuoYi）：
 * 开发者把带 {@link GetMapping} 等映射注解的处理器实例注册进来，网关即按 path+method 反射分发。
 *
 * <pre>
 *   public class MyApi {
 *       &#64;ApiName("打招呼")
 *       &#64;ApiPermission("demo:hello")
 *       &#64;GetMapping("/hello")
 *       public AjaxResult hello(&#64;RequestParam(name = "name", required = false) String name) {
 *           return AjaxResult.success("hello " + name);
 *       }
 *   }
 *   HttpOverMcPlugin.getInstance().getApiRegistry().register(new MyApi());
 * </pre>
 *
 * <h3>插件归属与生命周期（自动）</h3>
 * 注册时网关会<b>自动标记注册该 API 的插件名</b>（按处理器实例的 ClassLoader 归属，无需调用方手动传入）。
 * 监听 {@link ApiRegisteredEvent} 可获取本批端点清单（方法 / 路径 / 端点名 / 权限 / 处理器类 / 所属插件）。
 * 插件卸载时（{@code PluginDisableEvent}）网关会<b>自动卸载其名下全部 API</b>并触发
 * {@link ApiUnregisteredEvent}；亦可调用 {@link #unregister(Object)} / {@link #unregisterPlugin(String)} 显式卸载。
 *
 * <h3>路由约定</h3>
 * <ul>
 *   <li>映射注解：{@link GetMapping} / {@link PostMapping} / {@link PutMapping} /
 *       {@link DeleteMapping} / {@link PatchMapping} / {@link RequestMapping}(method 可多值/空=任意方法)；</li>
 *   <li><b>全局前缀</b>：注解式 API 始终挂载在网关配置的 {@code api-prefix}（默认 /api）之下，
 *       注解内无需写前缀、已写前缀不重复；无论 auth 是否启用，地址恒定（如 /api/ping），
 *       避免「未开启 auth 时 API 地址变化」的问题。该前缀由网关自动添加；</li>
 *   <li><b>类级前缀</b>：在控制器类上写 {@code @RequestMapping("/admin")}，其下所有方法自动获得
 *       {@code /admin} 段（位于全局前缀之后），无需每个方法重复写。最终路径 = 全局前缀 + 类前缀 + 方法路径
 *       （如 /api/admin/users）。仅取类级注解的 value/path，不约束 HTTP 方法（方法由方法级注解决定）；</li>
 *   <li><b>插件命名空间</b>：非主插件（SOYSHTTPOverMC 本体以外）注册时自动补充 {@code /plugins/&lt;插件名&gt;}
 *       前缀（位于全局前缀之后、类/方法路径之前），例如插件 Foo 的 {@code /users} 实际地址为
 *       {@code /api/plugins/Foo/users}；主插件自身不加此前缀。调用 {@link #registerProxy(Object)} 可强制以
 *       主插件代理注册（不加 /plugins 前缀，路径同主插件直接注册），ownerPlugin 仍标记为真实插件以便卸载；</li>
 *   <li>方法返回 {@link AjaxResult} 原样序列化；返回其他对象自动包 {@link AjaxResult#success(Object)}；</li>
 *   <li>{@link ApiPermission} 由 {@link PermissionService} 判定，未注册服务时注解不阻断；</li>
 *   <li>参数支持 {@link RequestParam}（query 绑定 + 类型转换）与 {@link RequestBody}（String body）。</li>
 * </ul>
 */
public class ApiRegistry {

    private static final String ANY_METHOD = "*";

    private final Logger log;
    /** 宿主插件（SOYSHTTPOverMC 本体）：注册时若无法归属到其它插件则归为本插件 */
    private final Plugin hostPlugin;
    private final Map<String, EndpointMeta> routes = new ConcurrentHashMap<>();
    private volatile PermissionService permissionService;
    /** 全局路径前缀（网关配置 api-prefix，默认 /api；始终生效，与 auth 是否启用解耦） */
    private volatile String pathPrefix = "/api";

    public ApiRegistry(Plugin hostPlugin, Logger log) {
        this.hostPlugin = hostPlugin;
        this.log = log;
    }

    /** 注册 PermissionService（登录插件接入点）：非空后 @ApiPermission 生效。 */
    public void setPermissionService(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    public PermissionService getPermissionService() {
        return permissionService;
    }

    /**
     * 设置全局路径前缀（如 "/api"）：后续 register 的 API 自动拼接该前缀。
     * 由插件在启动时从 gateway/config.yml 读取（始终生效，与 auth 启用与否无关）。
     */
    public void setPathPrefix(String prefix) {
        this.pathPrefix = prefix == null ? "" : prefix.trim();
    }

    public String getPathPrefix() {
        return pathPrefix;
    }

    /**
     * 注册一个带映射注解的处理器实例（自动标记其所属插件 = 处理器实例的 ClassLoader 归属插件）。
     * 非主插件注册时自动补充 {@code /plugins/<插件名>} 命名空间前缀（如 /api/plugins/Foo/users）。
     */
    public void register(Object instance) {
        register(pluginOfInstance(instance), instance, false);
    }

    /**
     * 注册一个带映射注解的处理器实例，并显式指定所属插件（用于跨插件代理注册等场景）。
     * 非主插件且非强制代理时自动补充 {@code /plugins/<插件名>} 前缀。
     */
    public void register(Plugin owner, Object instance) {
        register(owner, instance, false);
    }

    /**
     * 强制以主插件（SOYSHTTPOverMC）代理注册：不加 {@code /plugins/<插件名>} 前缀，
     * 路由路径同主插件直接注册（如 /api/users）。ownerPlugin 仍标记为真实插件，
     * 故该插件被禁用时其代理注册的 API 仍会被一并卸载。
     */
    public void registerProxy(Object instance) {
        register(pluginOfInstance(instance), instance, true);
    }

    /** 强制代理注册并显式指定所属插件（见 {@link #registerProxy(Object)}）。 */
    public void registerProxy(Plugin owner, Object instance) {
        register(owner, instance, true);
    }

    /**
     * 注册核心实现。
     * @param proxy true=强制以主插件代理（无 /plugins 前缀）；false=非主插件自动加 /plugins/&lt;插件名&gt;。
     */
    private void register(Plugin owner, Object instance, boolean proxy) {
        if (instance == null) return;
        Class<?> cls = instance.getClass();
        String ownerName = owner == null ? null : owner.getName();
        if (ownerName == null) ownerName = pluginNameOfInstance(instance);
        String clsName = classApiName(cls);
        String clsPermission = classApiPermission(cls);
        // 类级 @RequestMapping 路径前缀（位于全局 api-prefix 之后），为该类下所有方法统一加前缀
        String classPrefix = classMappingPrefix(cls);
        // 插件命名空间前缀：非主插件且非强制代理 → /plugins/<插件名>
        String pluginsPrefix = "";
        if (!proxy && ownerName != null && !ownerName.equals(hostPlugin.getName())) {
            pluginsPrefix = "/plugins/" + ownerName;
        }
        int n = 0;
        List<ApiInfo> registered = new ArrayList<>();
        for (Method m : cls.getDeclaredMethods()) {
            List<String[]> plans = resolveMapping(m); // [method|*, path]
            if (plans.isEmpty()) continue;
            m.setAccessible(true);
            String apiName = firstNonEmpty(m.getAnnotation(ApiName.class) == null ? null : m.getAnnotation(ApiName.class).value(), clsName);
            String permission = firstNonEmpty(m.getAnnotation(ApiPermission.class) == null ? null : m.getAnnotation(ApiPermission.class).value(), clsPermission);
            List<ParamBinding> params = analyzeParams(m);
            for (String[] plan : plans) {
                String method = plan[0];
                // 路径 = 全局前缀 + 插件前缀 + 类级前缀 + 方法路径
                // 例：api-prefix=/api + /plugins/Foo + /admin + /users → /api/plugins/Foo/admin/users
                String subPath = joinPath(classPrefix, plan[1]);
                String path = subPath;
                if (!pluginsPrefix.isEmpty()) path = pluginsPrefix + path;
                path = applyPrefix(path);
                String key = method + " " + path;
                EndpointMeta meta = new EndpointMeta(instance, m, apiName, permission, params, path, method, ownerName, cls.getName());
                EndpointMeta old = routes.put(key, meta);
                if (old != null) {
                    LogKit.warn("[HTTP-Over-MC] API 路由重复注册被覆盖: " + key + "（新=" + cls.getName()
                            + "，旧=" + old.method.getDeclaringClass().getName() + "）");
                }
                n++;
                registered.add(new ApiInfo(method, path, apiName, permission, cls.getName(), ownerName));
                LogKit.info("[HTTP-Over-MC] 注册 API: " + key + " 名称=" + apiName
                        + " 插件=" + ownerName + (proxy ? " (代理无前缀)" : "")
                        + (permission.isEmpty() ? "" : " 权限=" + permission));
            }
        }
        if (n == 0) {
            LogKit.warn("[HTTP-Over-MC] register(" + cls.getName() + ") 未发现映射注解方法（@GetMapping 等）");
            return;
        }
        // 发射注册事件（同步事件，确保在主线程触发；监听器异常不影响注册）
        fireApiEvent(new ApiRegisteredEvent(ownerName, registered));
    }

    /** 卸载某处理器实例注册的全部端点（插件可显式调用；亦会在 PluginDisable 时自动调用）。 */
    public List<ApiInfo> unregister(Object instance) {
        List<ApiInfo> removed = new ArrayList<>();
        if (instance == null) return removed;
        Iterator<Map.Entry<String, EndpointMeta>> it = routes.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, EndpointMeta> en = it.next();
            EndpointMeta m = en.getValue();
            if (m.instance == instance) {
                removed.add(toInfo(m));
                it.remove();
            }
        }
        if (!removed.isEmpty()) {
            LogKit.info("[HTTP-Over-MC] 卸载 API（实例 " + instance.getClass().getName() + "）：共 " + removed.size() + " 个");
            fireApiEvent(new ApiUnregisteredEvent(removed.get(0).getOwnerPlugin(), removed));
        }
        return removed;
    }

    /** 卸载指定插件名注册的全部 API（监听 PluginDisableEvent 时调用）。 */
    public List<ApiInfo> unregisterPlugin(String pluginName) {
        List<ApiInfo> removed = new ArrayList<>();
        if (pluginName == null || pluginName.isEmpty()) return removed;
        Iterator<Map.Entry<String, EndpointMeta>> it = routes.entrySet().iterator();
        while (it.hasNext()) {
            EndpointMeta m = it.next().getValue();
            if (pluginName.equals(m.ownerPlugin)) {
                removed.add(toInfo(m));
                it.remove();
            }
        }
        if (!removed.isEmpty()) {
            LogKit.info("[HTTP-Over-MC] 卸载 API（插件 " + pluginName + "）：共 " + removed.size() + " 个");
            fireApiEvent(new ApiUnregisteredEvent(pluginName, removed));
        }
        return removed;
    }

    /**
     * 分发一次请求。命中路由则执行处理器并返回结果对象（通常为 AjaxResult）；
     * 未命中返回 null（调用方继续走内置/静态资源路由）。
     */
    public Object dispatch(String httpMethod, String rawPath, Map<String, String> headers, byte[] body) {
        String path = stripQuery(rawPath);
        String method = httpMethod == null ? "" : httpMethod.toUpperCase();
        EndpointMeta meta = routes.get(method + " " + path);
        if (meta == null) meta = routes.get(ANY_METHOD + " " + path); // @RequestMapping 不限定方法
        if (meta == null) return null;

        // 权限判定（未注册 PermissionService 时注解不阻断）
        PermissionService ps = permissionService;
        if (ps != null && !meta.permission.isEmpty()) {
            CredentialPresentation credential = AuthUtils.extractPresentation(headers, "X-API-Key",
                    true, true, true, true);
            try {
                if (!ps.hasPermission(credential, meta.permission)) {
                    return AjaxResult.forbidden("无权限访问: " + meta.apiName + "（需要 " + meta.permission + "）");
                }
            } catch (Exception e) {
                LogKit.warn("[HTTP-Over-MC] PermissionService 异常，按拒绝处理: " + e, e);
                return AjaxResult.forbidden("权限服务异常");
            }
        }

        // 参数绑定 + 调用
        Map<String, String> query = parseQuery(rawPath);
        Object[] args = new Object[meta.params.size()];
        for (int i = 0; i < meta.params.size(); i++) {
            ParamBinding pb = meta.params.get(i);
            if (pb.requestBody) {
                args[i] = body == null ? "" : new String(body, java.nio.charset.StandardCharsets.UTF_8);
                continue;
            }
            String value = query.get(pb.name);
            if (value == null) {
                if (pb.required) {
                    return AjaxResult.error(400, "缺少必填参数: " + pb.name);
                }
                value = pb.defaultValue;
            }
            try {
                args[i] = convert(pb.type, value);
            } catch (Exception e) {
                return AjaxResult.error(400, "参数 " + pb.name + " 类型不合法: " + value);
            }
        }

        try {
            Object ret = meta.method.invoke(meta.instance, args);
            if (ret instanceof AjaxResult) return ret;
            return AjaxResult.success(ret);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            LogKit.warn("[HTTP-Over-MC] API 处理异常 " + meta.method.getName() + ": " + cause, cause);
            return AjaxResult.error("服务器内部错误: " + cause.getMessage());
        } catch (Exception e) {
            LogKit.warn("[HTTP-Over-MC] API 调用异常 " + meta.method.getName() + ": " + e, e);
            return AjaxResult.error("服务器内部错误: " + e.getMessage());
        }
    }

    public Map<String, EndpointMeta> getRoutes() {
        return routes;
    }

    /** 当前已注册的全部端点快照（路径/方法/端点名/权限/处理器类/所属插件），供门面 getRegisteredApis 复用。 */
    public List<ApiInfo> listEndpoints() {
        List<ApiInfo> list = new ArrayList<>();
        for (EndpointMeta m : routes.values()) {
            list.add(toInfo(m));
        }
        return list;
    }

    // ===== 插件归属推断 =====

    /** 按处理器实例的 ClassLoader 归属插件；找不到（如宿主自身）则归为宿主插件。 */
    private Plugin pluginOfInstance(Object instance) {
        String name = pluginNameOfInstance(instance);
        if (name != null) {
            Plugin p = Bukkit.getPluginManager().getPlugin(name);
            if (p != null) return p;
        }
        return hostPlugin;
    }

    private String pluginNameOfInstance(Object instance) {
        if (instance == null) return null;
        ClassLoader cl = instance.getClass().getClassLoader();
        for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
            if (p.getClass().getClassLoader() == cl) return p.getName();
        }
        return null;
    }

    /** 主线程安全触发 API 注册/卸载事件（同步事件，必在主线程触发，规避 1.12.2 异步事件限制）。 */
    private void fireApiEvent(Event e) {
        try {
            if (Bukkit.isPrimaryThread()) {
                Bukkit.getPluginManager().callEvent(e);
            } else {
                final Plugin host = hostPlugin;
                Bukkit.getScheduler().runTask(host, () -> {
                    try {
                        Bukkit.getPluginManager().callEvent(e);
                    } catch (Throwable ignored) {
                    }
                });
            }
        } catch (Throwable ignored) {
        }
    }

    private static ApiInfo toInfo(EndpointMeta m) {
        return new ApiInfo(m.httpMethod, m.path, m.apiName, m.permission, m.handlerClass, m.ownerPlugin);
    }

    // ===== 元数据 =====

    public static final class EndpointMeta {
        public final Object instance;
        public final Method method;
        public final String apiName;
        public final String permission;
        public final List<ParamBinding> params;
        /** 实际挂载路径（含前缀，如 /api/ping） */
        public final String path;
        /** HTTP 方法（GET/POST/... 或 *） */
        public final String httpMethod;
        /** 注册该 API 的插件名（网关自动标记） */
        public final String ownerPlugin;
        /** 处理器类全限定名 */
        public final String handlerClass;

        EndpointMeta(Object instance, Method method, String apiName, String permission,
                     List<ParamBinding> params, String path, String httpMethod, String ownerPlugin, String handlerClass) {
            this.instance = instance;
            this.method = method;
            this.apiName = apiName;
            this.permission = permission;
            this.params = params;
            this.path = path;
            this.httpMethod = httpMethod;
            this.ownerPlugin = ownerPlugin == null ? "" : ownerPlugin;
            this.handlerClass = handlerClass;
        }
    }

    public static final class ParamBinding {
        final String name;
        final boolean required;
        final String defaultValue;
        final Class<?> type;
        final boolean requestBody;

        ParamBinding(String name, boolean required, String defaultValue, Class<?> type, boolean requestBody) {
            this.name = name;
            this.required = required;
            this.defaultValue = defaultValue;
            this.type = type;
            this.requestBody = requestBody;
        }
    }

    private static List<ParamBinding> analyzeParams(Method m) {
        List<ParamBinding> list = new ArrayList<>();
        Annotation[][] anns = m.getParameterAnnotations();
        Class<?>[] types = m.getParameterTypes();
        for (int i = 0; i < types.length; i++) {
            RequestBody rb = find(anns[i], RequestBody.class);
            if (rb != null) {
                list.add(new ParamBinding(null, false, null, types[i], true));
                continue;
            }
            RequestParam rp = find(anns[i], RequestParam.class);
            if (rp != null) {
                list.add(new ParamBinding(rp.name(), rp.required(), rp.defaultValue(), types[i], false));
            } else {
                list.add(new ParamBinding("arg" + i, false, "", types[i], false));
            }
        }
        return list;
    }

    private static <T extends Annotation> T find(Annotation[] anns, Class<T> type) {
        if (anns == null) return null;
        for (Annotation a : anns) {
            if (a.annotationType() == type) return type.cast(a);
        }
        return null;
    }

    private static Object convert(Class<?> type, String value) {
        if (value == null) return null;
        if (type == String.class) return value;
        if (type == int.class || type == Integer.class) return Integer.parseInt(value.trim());
        if (type == long.class || type == Long.class) return Long.parseLong(value.trim());
        if (type == double.class || type == Double.class) return Double.parseDouble(value.trim());
        if (type == boolean.class || type == Boolean.class) return Boolean.parseBoolean(value.trim());
        return value; // 其他类型按字符串
    }

    private static Map<String, String> parseQuery(String rawPath) {
        Map<String, String> map = new HashMap<>();
        int q = rawPath == null ? -1 : rawPath.indexOf('?');
        if (q < 0 || q + 1 >= rawPath.length()) return map;
        for (String pair : rawPath.substring(q + 1).split("&")) {
            int eq = pair.indexOf('=');
            String k = eq >= 0 ? pair.substring(0, eq) : pair;
            String v = eq >= 0 ? pair.substring(eq + 1) : "";
            map.put(decode(k), decode(v));
        }
        return map;
    }

    private static String stripQuery(String rawPath) {
        if (rawPath == null) return "/";
        int q = rawPath.indexOf('?');
        return q >= 0 ? rawPath.substring(0, q) : rawPath;
    }

    private static String decode(String s) {
        try {
            return URLDecoder.decode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    private String applyPrefix(String path) {
        String prefix = pathPrefix;
        if (prefix.isEmpty() || prefix.equals("/")) return path;
        if (path.startsWith(prefix)) return path; // 已写前缀不重复
        return prefix + path;
    }

    private static String normalizePath(String p) {
        if (p == null || p.isEmpty()) return "/";
        return p.startsWith("/") ? p : "/" + p;
    }

    private static String classApiName(Class<?> cls) {
        ApiName an = cls.getAnnotation(ApiName.class);
        return an == null ? cls.getSimpleName() : an.value();
    }

    private static String classApiPermission(Class<?> cls) {
        ApiPermission ap = cls.getAnnotation(ApiPermission.class);
        return ap == null ? "" : ap.value();
    }

    /** 类级 @RequestMapping 路径前缀（为空字符串表示无前缀）。仅取 value/path，不约束方法。 */
    private static String classMappingPrefix(Class<?> cls) {
        RequestMapping rm = cls.getAnnotation(RequestMapping.class);
        if (rm == null) return "";
        String p = firstNonEmpty(rm.path(), rm.value());
        return p == null ? "" : p.trim();
    }

    /** 拼接类级前缀与方法路径（均先归一化为 / 开头，两者直接拼接即可得到 /admin/users） */
    private static String joinPath(String prefix, String sub) {
        if (prefix.isEmpty()) return normalizePath(sub);
        if (sub == null || sub.isEmpty()) return normalizePath(prefix);
        String p = normalizePath(prefix);                  // 例如 /admin（以 / 开头）
        String s = normalizePath(sub);                     // 例如 /users（以 / 开头）
        return p + s;                                      // → /admin/users
    }

    private static String firstNonEmpty(String... vals) {
        for (String v : vals) {
            if (v != null && !v.isEmpty()) return v;
        }
        return "";
    }

    /** 解析方法上的映射注解 → [method|*, path] 列表（一个方法可注册多个方法路由） */
    private static List<String[]> resolveMapping(Method m) {
        List<String[]> list = new ArrayList<>();
        GetMapping g = m.getAnnotation(GetMapping.class);
        if (g != null) list.add(new String[]{"GET", firstNonEmpty(g.path(), g.value())});
        PostMapping po = m.getAnnotation(PostMapping.class);
        if (po != null) list.add(new String[]{"POST", firstNonEmpty(po.path(), po.value())});
        PutMapping pu = m.getAnnotation(PutMapping.class);
        if (pu != null) list.add(new String[]{"PUT", firstNonEmpty(pu.path(), pu.value())});
        DeleteMapping d = m.getAnnotation(DeleteMapping.class);
        if (d != null) list.add(new String[]{"DELETE", firstNonEmpty(d.path(), d.value())});
        PatchMapping pa = m.getAnnotation(PatchMapping.class);
        if (pa != null) list.add(new String[]{"PATCH", firstNonEmpty(pa.path(), pa.value())});
        RequestMapping rm = m.getAnnotation(RequestMapping.class);
        if (rm != null) {
            String p = firstNonEmpty(rm.path(), rm.value());
            RequestMethod[] methods = rm.method();
            if (methods.length == 0) {
                list.add(new String[]{ANY_METHOD, p});
            } else {
                for (RequestMethod rmethod : methods) {
                    list.add(new String[]{rmethod.name(), p});
                }
            }
        }
        return list;
    }
}
