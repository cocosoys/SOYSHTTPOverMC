package com.github.cocosoys.mc.soyshttpovermc.web.swagger;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.github.cocosoys.mc.soyshttpovermc.util.JsonWriter;
import com.github.cocosoys.mc.soyshttpovermc.web.ApiRegistry;
import com.github.cocosoys.mc.soyshttpovermc.web.ApiRegistry.EndpointMeta;
import com.github.cocosoys.mc.soyshttpovermc.web.ApiRegistry.ParamBinding;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * OpenAPI 3.0 自文档生成器（Swagger UI 数据源）。
 *
 * <p>遍历 {@link ApiRegistry} 路由表，为每个端点生成 Operation（路径 / 方法 / 参数 /
 * 请求体 / 响应信封 / 权限与匿名标注 / 限流与防重复标记），并对 {@code @RequestBody}
 * 实体与嵌套字段做字段级 schema 反射（消费 jackson-annotations：
 * {@link JsonProperty} 改名、{@link JsonIgnore} 排除、{@link JsonFormat} 日期格式、
 * {@link JsonValue} 枚举输出值；集合泛型元素类型、嵌套 POJO、继承字段均会展开）。
 *
 * <p>纯逻辑、无 Bukkit 依赖，可被 SwaggerApiDocsPage 动态调用。
 */
public final class SwaggerDocument {

    private SwaggerDocument() {
    }

    /**
     * 生成 OpenAPI 3.0 JSON 文本（每次调用实时反射当前路由表快照）。
     *
     * @param registry 注解式 API 注册表（通常为主插件的 ApiRegistry）
     * @param version  文档版本（建议传插件版本号；null 时显示 unknown）
     */
    public static String build(ApiRegistry registry, String version) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("openapi", "3.0.3");
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("title", "SOYSHTTPOverMC API");
        info.put("version", version == null || version.isEmpty() ? "unknown" : version);
        info.put("description", "SOYSHTTPOverMC 注解式 Web API 自文档。"
                + "端点注册于 ApiRegistry（method + path 精确匹配），认证与权限由网关策略层统一裁决；"
                + "本页仅面向已登录的服务器 OP。");
        root.put("info", info);

        Map<String, Object> paths = new TreeMap<>();
        Map<String, Object> schemas = new TreeMap<>();
        SchemaReflector reflector = new SchemaReflector(schemas);

        Map<String, EndpointMeta> routes = registry == null
                ? Collections.<String, EndpointMeta>emptyMap() : registry.getRoutes();
        for (EndpointMeta meta : routes.values()) {
            if (meta.hidden) {
                continue; // @Hidden：从文档隐藏
            }
            String m = (meta.httpMethod == null || meta.httpMethod.isEmpty()) ? "get" : meta.httpMethod.toLowerCase();
            if ("*".equals(m)) {
                m = "get"; // OpenAPI 无法表达“任意方法”，按 get 兜底
            }
            Map<String, Object> op = new LinkedHashMap<>();
            op.put("operationId", operationId(m, meta.path));
            if (meta.apiName != null && !meta.apiName.isEmpty()) {
                op.put("summary", meta.apiName);
            }
            op.put("tags", Collections.singletonList(
                    meta.ownerPlugin == null || meta.ownerPlugin.isEmpty() ? "SOYS" : meta.ownerPlugin));
            if (meta.deprecated != null) {
                op.put("deprecated", Boolean.TRUE);
            }
            String desc = describe(meta, registry);
            if (desc != null) {
                op.put("description", desc);
            }

            List<Object> parameters = new ArrayList<>();
            Map<String, Object> requestBody = null;
            if (meta.params != null) {
                for (ParamBinding p : meta.params) {
                    if (p.injectContext || p.injectCredential) {
                        continue; // 网关注入项不出现在文档中
                    }
                    if (p.requestBody) {
                        if (p.type != null && !isTrivialBody(p.type)) {
                            Map<String, Object> rb = new LinkedHashMap<>();
                            rb.put("required", Boolean.TRUE);
                            Map<String, Object> content = new LinkedHashMap<>();
                            Map<String, Object> mt = new LinkedHashMap<>();
                            mt.put("schema", reflector.schemaRef(p.type));
                            content.put("application/json", mt);
                            rb.put("content", content);
                            requestBody = rb;
                        }
                        continue;
                    }
                    if (p.name == null) {
                        continue;
                    }
                    Map<String, Object> prm = new LinkedHashMap<>();
                    prm.put("name", p.name);
                    prm.put("in", p.pathVariable ? "path" : "query");
                    prm.put("required", p.required ? Boolean.TRUE : Boolean.FALSE);
                    prm.put("schema", reflector.schemaRef(p.type == null ? String.class : p.type));
                    if (p.defaultSet && p.defaultValue != null) {
                        Map<String, Object> schema = new LinkedHashMap<>();
                        schema.put("type", "string");
                        schema.put("default", p.defaultValue);
                        prm.put("schema", schema);
                    }
                    parameters.add(prm);
                }
            }
            if (!parameters.isEmpty()) {
                op.put("parameters", parameters);
            }
            if (requestBody != null) {
                op.put("requestBody", requestBody);
            }
            op.put("responses", responses());

            @SuppressWarnings("unchecked")
            Map<String, Object> pathItem = (Map<String, Object>) paths.get(meta.path);
            if (pathItem == null) {
                pathItem = new LinkedHashMap<>();
                paths.put(meta.path, pathItem);
            }
            pathItem.put(m, op);
        }
        root.put("paths", paths);
        if (!schemas.isEmpty()) {
            Map<String, Object> comp = new LinkedHashMap<>();
            comp.put("schemas", schemas);
            root.put("components", comp);
        }
        return JsonWriter.write(root);
    }

    private static boolean isTrivialBody(Class<?> type) {
        return type == byte[].class || type == Byte[].class
                || type.isPrimitive() || type == String.class
                || type == Boolean.class || Number.class.isAssignableFrom(type);
    }

    private static String describe(EndpointMeta meta, ApiRegistry registry) {
        List<String> parts = new ArrayList<>();
        if (meta.permission != null && !meta.permission.isEmpty()) {
            parts.add("所需权限: " + meta.permission);
        }
        if (registry != null) {
            try {
                if (registry.isAnonymous(meta.httpMethod, meta.path)) {
                    parts.add("匿名可访问（@Anonymous）");
                }
            } catch (Throwable ignored) {
                // 判定失败不影响文档生成
            }
        }
        if (meta.rateLimit != null) {
            parts.add("端点限流（@RateLimiter）");
        }
        if (meta.repeatSubmit != null) {
            parts.add("防重复提交（@RepeatSubmit）");
        }
        return parts.isEmpty() ? null : String.join("；", parts);
    }

    private static Map<String, Object> responses() {
        Map<String, Object> r = new LinkedHashMap<>();
        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("description", "成功（SOYS 统一信封 {code, msg, data}）");
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("code", obj("type", "integer"));
        props.put("msg", obj("type", "string"));
        props.put("data", obj("type", "object"));
        schema.put("properties", props);
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("application/json", obj("schema", schema));
        ok.put("content", content);
        r.put("200", ok);
        r.put("401", resp("未登录 / 凭证无效"));
        r.put("403", resp("无权限（网关权限判定拒绝）"));
        r.put("404", resp("路径不存在"));
        r.put("429", resp("触发限流 / 防重复提交"));
        r.put("500", resp("服务器内部错误"));
        return r;
    }

    private static Map<String, Object> resp(String desc) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("description", desc);
        return m;
    }

    private static String operationId(String method, String path) {
        return (method + "_" + path).replaceAll("[^A-Za-z0-9]", "_").replaceAll("_+", "_");
    }

    private static Map<String, Object> obj(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    /**
     * 字段级 schema 反射器：消费 jackson-annotations，产出 components/schemas。
     */
    static final class SchemaReflector {

        private final Map<String, Object> schemas;
        private final Map<Class<?>, String> schemaNames = new HashMap<>();
        private final Set<Class<?>> processing = new HashSet<>();

        SchemaReflector(Map<String, Object> schemas) {
            this.schemas = schemas;
        }

        /**
         * 返回 schema 对象：基础类型直接展开，POJO 注册后返回 $ref。
         */
        Map<String, Object> schemaRef(Class<?> type) {
            Map<String, Object> s = simple(type);
            if (s != null) {
                return s;
            }
            Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("$ref", "#/components/schemas/" + register(type));
            return ref;
        }

        private Map<String, Object> simple(Class<?> t) {
            if (t == null) {
                return obj("type", "string");
            }
            if (t == String.class || t == Character.class || t == char.class) {
                return obj("type", "string");
            }
            if (t == Integer.class || t == int.class) {
                return obj("type", "integer", "format", "int32");
            }
            if (t == Long.class || t == long.class) {
                return obj("type", "integer", "format", "int64");
            }
            if (t == Double.class || t == double.class) {
                return obj("type", "number", "format", "double");
            }
            if (t == Float.class || t == float.class) {
                return obj("type", "number", "format", "float");
            }
            if (t == Boolean.class || t == boolean.class) {
                return obj("type", "boolean");
            }
            if (t == byte[].class || t == Byte[].class) {
                return obj("type", "string", "format", "byte");
            }
            if (Date.class.isAssignableFrom(t)) {
                return obj("type", "string", "format", "date-time");
            }
            if (t.isEnum()) {
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("type", "string");
                List<String> vals = new ArrayList<>();
                Method jsonValue = jsonValueMethod(t);
                try {
                    for (Object c : t.getEnumConstants()) {
                        vals.add(jsonValue != null
                                ? String.valueOf(jsonValue.invoke(c)) : ((Enum<?>) c).name());
                    }
                } catch (Exception ignored) {
                    // 反射失败时保留空枚举值
                }
                e.put("enum", vals);
                return e;
            }
            if (Collection.class.isAssignableFrom(t)) {
                return obj("type", "array", "items", obj("type", "string"));
            }
            if (Map.class.isAssignableFrom(t)) {
                return obj("type", "object", "additionalProperties", obj("type", "string"));
            }
            return null;
        }

        private static Method jsonValueMethod(Class<?> type) {
            for (Method m : type.getDeclaredMethods()) {
                if (m.getAnnotation(JsonValue.class) != null && m.getParameterCount() == 0) {
                    m.setAccessible(true);
                    return m;
                }
            }
            return null;
        }

        /**
         * 注册 POJO 为 components/schemas 条目并展开字段，返回 schema 名。
         */
        private String register(Class<?> type) {
            String name = type.getSimpleName();
            String clashOwner = null;
            for (Map.Entry<Class<?>, String> e : schemaNames.entrySet()) {
                if (e.getValue().equals(name) && !e.getKey().equals(type)) {
                    clashOwner = e.getKey().getName();
                    break;
                }
            }
            if (clashOwner != null) {
                name = type.getName().replace('.', '_'); // 同名冲突 → 全限定名
            }
            schemaNames.put(type, name);
            if (processing.contains(type)) {
                // 环：schema 条目已占位，直接返回引用名
                return name;
            }
            Map<String, Object> obj = new LinkedHashMap<>();
            obj.put("type", "object");
            schemas.put(name, obj);
            processing.add(type);
            Map<String, Object> props = new LinkedHashMap<>();
            for (Field f : fieldsOf(type)) {
                String fname = f.getName();
                JsonProperty jp = f.getAnnotation(JsonProperty.class);
                if (jp != null && jp.value() != null && !jp.value().isEmpty()) {
                    fname = jp.value();
                }
                Map<String, Object> fs = fieldSchema(f);
                JsonFormat fmt = f.getAnnotation(JsonFormat.class);
                if (fmt != null && fmt.pattern() != null && !fmt.pattern().isEmpty()
                        && Date.class.isAssignableFrom(f.getType())) {
                    Map<String, Object> dated = new LinkedHashMap<>(fs);
                    dated.put("type", "string");
                    dated.put("format", "date-time");
                    dated.put("description", "格式: " + fmt.pattern());
                    fs = dated;
                }
                props.put(fname, fs);
            }
            obj.put("properties", props);
            processing.remove(type);
            return name;
        }

        private static List<Field> fieldsOf(Class<?> type) {
            List<Field> list = new ArrayList<>();
            for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers()) || Modifier.isTransient(f.getModifiers())
                            || f.isSynthetic()) {
                        continue;
                    }
                    if (f.getAnnotation(JsonIgnore.class) != null) {
                        continue;
                    }
                    list.add(f);
                }
            }
            return list;
        }

        private Map<String, Object> fieldSchema(Field f) {
            Type g = f.getGenericType();
            if (g instanceof ParameterizedType) {
                ParameterizedType pt = (ParameterizedType) g;
                Type raw = pt.getRawType();
                if (raw instanceof Class && Collection.class.isAssignableFrom((Class<?>) raw)) {
                    Map<String, Object> a = new LinkedHashMap<>();
                    a.put("type", "array");
                    Type[] args = pt.getActualTypeArguments();
                    a.put("items", args.length == 1 ? typeRef(plain(args[0])) : obj("type", "string"));
                    return a;
                }
                if (raw instanceof Class && Map.class.isAssignableFrom((Class<?>) raw)) {
                    Map<String, Object> o = new LinkedHashMap<>();
                    o.put("type", "object");
                    o.put("additionalProperties", obj("type", "string"));
                    return o;
                }
                if (raw instanceof Class) {
                    return typeRef((Class<?>) raw);
                }
            }
            return typeRef(f.getType());
        }

        private Map<String, Object> typeRef(Class<?> t) {
            Map<String, Object> s = simple(t);
            if (s != null) {
                return s;
            }
            return schemaRef(t);
        }

        private static Class<?> plain(Type t) {
            if (t instanceof Class) {
                return (Class<?>) t;
            }
            if (t instanceof ParameterizedType) {
                Type raw = ((ParameterizedType) t).getRawType();
                if (raw instanceof Class) {
                    return (Class<?>) raw;
                }
            }
            if (t instanceof WildcardType) {
                Type[] upper = ((WildcardType) t).getUpperBounds();
                if (upper.length > 0) {
                    return plain(upper[0]);
                }
            }
            return String.class;
        }
    }
}
