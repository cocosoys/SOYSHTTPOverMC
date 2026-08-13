package soys.soyshttpovermc.api;

import soys.soyshttpovermc.api.annotations.*;
import soys.soyshttpovermc.api.util.AjaxResult;
import soys.soyshttpovermc.gateway.policy.auth.AuthUtils;
import soys.soyshttpovermc.gateway.policy.auth.issuer.CredentialPresentation;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
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
 * 约定：
 * <ul>
 *   <li>映射注解：{@link GetMapping} / {@link PostMapping} / {@link PutMapping} /
 *       {@link DeleteMapping} / {@link PatchMapping} / {@link RequestMapping}(method 可多值/空=任意方法)；</li>
 *   <li><b>全局前缀</b>：auth 策略启用时注册的 API 自动加 {@code /api} 前缀（由插件
 *       {@link #setPathPrefix} 注入），注解内无需写前缀；已写前缀则不再重复；</li>
 *   <li>方法返回 {@link AjaxResult} 原样序列化；返回其他对象自动包 {@link AjaxResult#success(Object)}；</li>
 *   <li>{@link ApiPermission} 由 {@link PermissionService} 判定，未注册服务时注解不阻断；</li>
 *   <li>参数支持 {@link RequestParam}（query 绑定 + 类型转换）与 {@link RequestBody}（String body）。</li>
 * </ul>
 */
public class ApiRegistry {

    private static final String ANY_METHOD = "*";

    private final Logger log;
    private final Map<String, EndpointMeta> routes = new ConcurrentHashMap<>();
    private volatile PermissionService permissionService;
    /** 全局路径前缀（auth 开启时为 /api，否则空）；注册时写入路由 */
    private volatile String pathPrefix = "";

    public ApiRegistry(Logger log) {
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
     * 由插件在 auth 策略启用时调用（auth 关闭时传空串表示不加前缀）。
     */
    public void setPathPrefix(String prefix) {
        this.pathPrefix = prefix == null ? "" : prefix.trim();
    }

    public String getPathPrefix() {
        return pathPrefix;
    }

    /** 注册一个带映射注解的处理器实例（扫描其全部方法）。 */
    public void register(Object instance) {
        if (instance == null) return;
        Class<?> cls = instance.getClass();
        String clsName = classApiName(cls);
        String clsPermission = classApiPermission(cls);
        int n = 0;
        for (Method m : cls.getDeclaredMethods()) {
            List<String[]> plans = resolveMapping(m); // [method|*, path]
            if (plans.isEmpty()) continue;
            m.setAccessible(true);
            String apiName = firstNonEmpty(m.getAnnotation(ApiName.class) == null ? null : m.getAnnotation(ApiName.class).value(), clsName);
            String permission = firstNonEmpty(m.getAnnotation(ApiPermission.class) == null ? null : m.getAnnotation(ApiPermission.class).value(), clsPermission);
            List<ParamBinding> params = analyzeParams(m);
            for (String[] plan : plans) {
                String method = plan[0];
                String path = applyPrefix(normalizePath(plan[1]));
                String key = method + " " + path;
                EndpointMeta meta = new EndpointMeta(instance, m, apiName, permission, params);
                EndpointMeta old = routes.put(key, meta);
                if (old != null) {
                    log.warning("[HTTP-Over-MC] API 路由重复注册被覆盖: " + key + "（新=" + cls.getName() + "，旧=" + old.method.getDeclaringClass().getName() + "）");
                }
                n++;
                log.info("[HTTP-Over-MC] 注册 API: " + key + " 名称=" + apiName + (permission.isEmpty() ? "" : " 权限=" + permission));
            }
        }
        if (n == 0) {
            log.warning("[HTTP-Over-MC] register(" + cls.getName() + ") 未发现映射注解方法（@GetMapping 等）");
        }
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

    private String applyPrefix(String path) {
        String prefix = pathPrefix;
        if (prefix.isEmpty() || prefix.equals("/")) return path;
        if (path.startsWith(prefix)) return path; // 已写前缀不重复
        return prefix + path;
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
                log.log(Level.WARNING, "[HTTP-Over-MC] PermissionService 异常，按拒绝处理: " + e, e);
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
            log.log(Level.WARNING, "[HTTP-Over-MC] API 处理异常 " + meta.method.getName() + ": " + cause, cause);
            return AjaxResult.error("服务器内部错误: " + cause.getMessage());
        } catch (Exception e) {
            log.log(Level.WARNING, "[HTTP-Over-MC] API 调用异常 " + meta.method.getName() + ": " + e, e);
            return AjaxResult.error("服务器内部错误: " + e.getMessage());
        }
    }

    public Map<String, EndpointMeta> getRoutes() {
        return routes;
    }

    // ===== 元数据 =====

    public static final class EndpointMeta {
        public final Object instance;
        public final Method method;
        public final String apiName;
        public final String permission;
        public final List<ParamBinding> params;

        EndpointMeta(Object instance, Method method, String apiName, String permission, List<ParamBinding> params) {
            this.instance = instance;
            this.method = method;
            this.apiName = apiName;
            this.permission = permission;
            this.params = params;
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

    private static String firstNonEmpty(String... vals) {
        for (String v : vals) {
            if (v != null && !v.isEmpty()) return v;
        }
        return "";
    }
}
