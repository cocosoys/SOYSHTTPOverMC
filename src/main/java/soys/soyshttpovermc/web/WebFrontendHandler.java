package soys.soyshttpovermc.web;

import soys.soyshttpovermc.util.AjaxResult;
import soys.soyshttpovermc.util.HttpFrames;
import soys.soyshttpovermc.ApiRegistry;
import soys.soyshttpovermc.gateway.policy.auth.bridge.AuthLoginBridge;
import soys.soyshttpovermc.log.LogKit;
import soys.soyshttpovermc.proto.FrameProto;
import com.google.protobuf.ByteString;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务端 HTTP 处理器：把一次经 Bot 隧道送达的 HTTP 请求，路由为注解式 API 或静态资源。
 *
 * 路由优先级：
 *  1) 网页登录流程（/auth/login、/auth/issue）→ 登录桥处理；
 *  2) 注解式 API（@GetMapping 注册，如 /api/status、/api/ping）→ dispatch；
 *  3) /favicon.ico → 优先本地插件配置目录 web/favicon.ico（磁盘，可热替换），
 *     再 jar 内置 /dist/favicon.ico，仍缺失才 204 无内容；
 *  4) 插件登记网页（WebRegistry：第三方插件注册，默认 /plugins/&lt;插件名&gt; 前缀；
 *     支持强制代理、302/301 跳转与 .html 后缀智能匹配）；
 *  5) 静态资源：web.root 磁盘目录（含 .. 穿越防护）→ jar 内置 /dist/ → 404。
 *     无扩展名路径同时支持带/不带 .html（/login 与 /login.html 等价）。
 */
public class WebFrontendHandler {

    private final File webRoot;     // null 表示未配置磁盘 webroot
    private final String webRootCanonical;
    private final ApiRegistry apiRegistry; // 注解式 API 注册表（可为 null）
    private final WebRegistry webRegistry; // 插件登记网页（可为 null）
    private final NavRegistry navRegistry; // 门户导航登记处（可为 null）
    private volatile AuthLoginBridge authBridge; // AuthMe 网页登录桥（null=未启用；/soyshttp reload 后热替换）
    /** Web 内容存活缓存（常驻 pinned + LRU + 大文件加载器），null=禁用缓存 */
    private final WebContentCache webContent;
    /** 大文件安全上限（超过直接 413；防单文件把内存打爆，默认 128MB） */
    private final long largeFileMaxBytes;
    /** CORS 声明注册中心（null=禁用） */
    private final CorsRegistry corsRegistry;
    /** 请求级拦截器注册中心（null=禁用） */
    private final WebInterceptorRegistry interceptorRegistry;

    public WebFrontendHandler(String webRootPath, ApiRegistry apiRegistry, WebRegistry webRegistry,
                              NavRegistry navRegistry, AuthLoginBridge authBridge,
                              WebContentCache webContent, long largeFileMaxBytes,
                              CorsRegistry corsRegistry, WebInterceptorRegistry interceptorRegistry) {
        this.apiRegistry = apiRegistry;
        this.webRegistry = webRegistry;
        this.navRegistry = navRegistry;
        this.authBridge = authBridge;
        this.webContent = webContent;
        this.largeFileMaxBytes = Math.max(0, largeFileMaxBytes);
        this.corsRegistry = corsRegistry;
        this.interceptorRegistry = interceptorRegistry;
        File root = null;
        String canonical = null;
        if (webRootPath != null && !webRootPath.trim().isEmpty()) {
            try {
                root = new File(webRootPath).getAbsoluteFile();
                canonical = root.getCanonicalPath();
                if (!root.isDirectory()) {
                    root.mkdirs();
                }
            } catch (Exception e) {
                root = null;
                canonical = null;
            }
        }
        this.webRoot = root;
        this.webRootCanonical = canonical;
    }

    /** 热替换 AuthMe 登录桥（/soyshttp reload 重建网关后调用，保持与最新 session-token 颁发器一致）。 */
    public void setAuthBridge(AuthLoginBridge bridge) {
        this.authBridge = bridge;
    }

    /**
     * 处理一次请求，返回完整（单分片）HttpResponseFrame。
     * 调用方负责按 32000 字节上限分片发回客户端。
     * <p>外层包装：CORS 预检/附加 + 请求级拦截器（可改写 path/headers 或短路）；
     * 实际路由在 {@link #handleInner}。</p>
     */
    public FrameProto.HttpResponseFrame handle(String method, String path, Map<String, String> headers, byte[] body) {
        String m = method == null ? "" : method.toUpperCase();
        String rawPath = decode(path);

        // CORS：预检请求短路（204 + CORS 头），普通命中请求响应附加 CORS 头
        CorsRegistry.CorsEntry cors = corsRegistry == null ? null : corsRegistry.match(stripQuery(rawPath));
        if (cors != null && "OPTIONS".equals(m)) {
            return corsRegistry.preflight(cors);
        }

        // 请求级拦截器（网关之后、业务之前）：可改写 path/headers，或按自定义规则短路返回
        if (interceptorRegistry != null && !interceptorRegistry.isEmpty()) {
            WebInterceptor.WebInterceptContext ctx =
                    new WebInterceptor.WebInterceptContext(m, rawPath, headers, body, null, false);
            for (WebInterceptor in : interceptorRegistry.all()) {
                try {
                    WebInterceptor.Outcome oc = in.intercept(ctx);
                    if (oc != null && oc.isStop()) {
                        FrameProto.HttpResponseFrame stop = FrameProto.HttpResponseFrame.newBuilder()
                                .setStatusCode(oc.status())
                                .putHeaders("Content-Type",
                                        oc.contentType() == null ? "text/plain; charset=utf-8" : oc.contentType())
                                .putAllHeaders(oc.headers())
                                .setBody(ByteString.copyFrom(oc.body() == null ? new byte[0] : oc.body()))
                                .setFragmentIndex(0)
                                .setTotalFragments(1)
                                .build();
                        return cors == null ? stop : corsRegistry.attach(cors, stop);
                    }
                } catch (Throwable t) {
                    LogKit.warn("[HTTP-Over-MC] 拦截器 " + in.name() + " 异常，放行继续: " + t);
                }
            }
            m = ctx.method();
            rawPath = ctx.path();
            headers = ctx.headers();
        }

        FrameProto.HttpResponseFrame resp = handleInner(m, rawPath, headers, body);
        return cors == null ? resp : corsRegistry.attach(cors, resp);
    }

    /** 实际路由：登录流程 → 注解式 API → favicon → 插件登记网页 → 静态资源。 */
    private FrameProto.HttpResponseFrame handleInner(String m, String rawPath, Map<String, String> headers, byte[] body) {

        // 0) AuthMe 网页登录流程（/auth/login 重定向 / /auth/issue 校验发 Cookie / /auth/mode 模式探测），先于 API 路由，且本身免鉴权
        if (authBridge != null) {
            String ap = stripQuery(rawPath);
            if (ap.equals("/auth/login") || ap.equals("/auth/issue") || ap.equals("/auth/mode")) {
                return handleAuth(m, rawPath, headers, body);
            }
        }

        // 0.5) 门户导航 JSON（供前端渲染插件面板，替代后端 injectNav 注入 DOM）
        if (navRegistry != null && "/api/nav".equals(stripQuery(rawPath))) {
            List<Map<String, Object>> data = new ArrayList<>();
            for (NavRegistry.NavItem it : navRegistry.snapshot()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("owner", it.owner);
                item.put("label", it.label);
                item.put("path", it.path);
                item.put("icon", it.icon);
                item.put("permission", it.permission);
                item.put("order", it.order);
                data.add(item);
            }
            return HttpFrames.json(200, AjaxResult.success(data));
        }

        // 1) 注解式 API 优先（@GetMapping 等注册的路由）
        if (apiRegistry != null) {
            Object apiResult = apiRegistry.dispatch(m, rawPath, headers, body);
            if (apiResult != null) {
                AjaxResult ar = apiResult instanceof AjaxResult
                        ? (AjaxResult) apiResult : AjaxResult.success(apiResult);
                // 离线 cookie 自动升级：响应附加 Set-Cookie(新在线令牌) + X-Soys-New-Token
                Map<String, String> extra = apiRegistry.drainResponseHeaders();
                return jsonResponse(200, ar.toJson(), extra);
            }
        }

        String cleanPath = stripQuery(rawPath);
        if (cleanPath.isEmpty() || cleanPath.equals("/")) cleanPath = "/";

        // favicon：优先本地插件配置目录 web/favicon.ico（磁盘，可热替换），再 jar 内置 /dist/favicon.ico，仍缺失才 204 无内容
        if (cleanPath.equals("/favicon.ico")) {
            byte[] ico = resolveFavicon();
            if (ico != null) {
                return FrameProto.HttpResponseFrame.newBuilder()
                        .setStatusCode(200)
                        .putHeaders("Content-Type", "image/x-icon")
                        .setBody(ByteString.copyFrom(ico))
                        .setFragmentIndex(0)
                        .setTotalFragments(1)
                        .build();
            }
            return FrameProto.HttpResponseFrame.newBuilder()
                    .setStatusCode(204)
                    .putHeaders("Content-Type", "image/x-icon")
                    .setBody(ByteString.EMPTY)
                    .setFragmentIndex(0)
                    .setTotalFragments(1)
                    .build();
        }

        // 插件登记网页（第三方插件注册；默认前缀 /plugins/<插件名>，强制代理无前缀；
        // 支持跳转（302 Location）与 .html 后缀智能匹配：/login ↔ /login.html 均可命中）
        if (webRegistry != null) {
            WebRegistry.Entry page = webRegistry.resolve(m, cleanPath);
            if (page != null) {
                if (page.redirectTo != null) {
                    return HttpFrames.redirect(page.redirectCode > 0 ? page.redirectCode : 302, page.redirectTo);
                }
                return FrameProto.HttpResponseFrame.newBuilder()
                        .setStatusCode(200)
                        .putHeaders("Content-Type", page.effectiveContentType())
                        .setBody(ByteString.copyFrom(
                                loadBytes(page.path, page.getDiskFile(), page::resolveBytes)))
                        .setFragmentIndex(0)
                        .setTotalFragments(1)
                        .build();
            }
        }

        // 静态资源
        // 根/目录回退 index.html；无扩展名路径同时支持 "/login" 与 "/login.html" 两种访问形式
        // （先试 login.html，再试 login/index.html 目录语义）；Content-Type 按实际命中文件名判定，
        // 否则浏览器会下载而非渲染
        Hit hit = resolveResource(cleanPath);
        if (hit == null) {
            return notFound(cleanPath);
        }
        return FrameProto.HttpResponseFrame.newBuilder()
                .setStatusCode(200)
                .putHeaders("Content-Type", MimeTypes.forPath(hit.name))
                .setBody(ByteString.copyFrom(hit.bytes))
                .setFragmentIndex(0)
                .setTotalFragments(1)
                .build();
    }

    // ===== AuthMe 网页登录流程路由 =====
    private FrameProto.HttpResponseFrame handleAuth(String method, String rawPath,
                                                    Map<String, String> headers, byte[] body) {
        String cleanPath = stripQuery(rawPath);
        if (cleanPath.equals("/auth/issue") && "POST".equals(method)) {
            Map<String, String> form = parseForm(body);
            // 有登录插件=票据+密码校验；无登录插件=免密码，仅凭用户名直登
            if (authBridge.loginRequiresPassword()) {
                return authBridge.issue(form.get("ticket"), form.get("password"));
            }
            return authBridge.issueByUsername(form.get("username"));
        }
        if (cleanPath.equals("/auth/login")) {
            return authBridge.serveLoginPage(queryParam(rawPath, "ticket"));
        }
        if (cleanPath.equals("/auth/mode")) {
            // 登录模式信息：前端 /login.html 据此切换「票据+密码」/「免密用户名」表单
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("requiresPassword", authBridge.loginRequiresPassword());
            d.put("cookieName", authBridge.getCookieName());
            d.put("ttlSeconds", authBridge.getTtlSeconds());
            return HttpFrames.json(200, AjaxResult.success(d));
        }
        // 其它 /auth/* 方法不匹配 → 404
        return notFound(rawPath);
    }

    /** 解析 application/x-www-form-urlencoded 请求体（POST /auth/issue）。 */
    private static Map<String, String> parseForm(byte[] body) {
        Map<String, String> map = new java.util.HashMap<>();
        if (body == null || body.length == 0) return map;
        String s = new String(body, StandardCharsets.UTF_8);
        for (String pair : s.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            String k = pair.substring(0, eq);
            String v = pair.substring(eq + 1);
            try {
                map.put(java.net.URLDecoder.decode(k, "UTF-8"), java.net.URLDecoder.decode(v, "UTF-8"));
            } catch (Exception ignored) {
                map.put(k, v);
            }
        }
        return map;
    }

    /** 从原始路径（含 ?query）提取单个查询参数值。 */
    private static String queryParam(String rawPath, String name) {
        int q = rawPath.indexOf('?');
        if (q < 0 || q + 1 >= rawPath.length()) return null;
        for (String pair : rawPath.substring(q + 1).split("&")) {
            int eq = pair.indexOf('=');
            String k = eq >= 0 ? pair.substring(0, eq) : pair;
            if (k.equals(name)) {
                String v = eq >= 0 ? pair.substring(eq + 1) : "";
                try {
                    return java.net.URLDecoder.decode(v, "UTF-8");
                } catch (Exception e) {
                    return v;
                }
            }
        }
        return null;
    }

    /** 404 响应帧（路径不存在）：优先伺服 dist/404.html 静态页 → webRegistry 自定义错误页（字节，非拼串）→ JSON 错误体。 */
    private FrameProto.HttpResponseFrame notFound(String cleanPath) {
        byte[] page404 = readResource("/dist/404.html");
        if (page404 != null) {
            return FrameProto.HttpResponseFrame.newBuilder()
                    .setStatusCode(404)
                    .putHeaders("Content-Type", "text/html; charset=utf-8")
                    .setBody(ByteString.copyFrom(page404))
                    .setFragmentIndex(0)
                    .setTotalFragments(1)
                    .build();
        }
        byte[] custom = webRegistry == null ? null : webRegistry.errorPage(404);
        if (custom != null) {
            return FrameProto.HttpResponseFrame.newBuilder()
                    .setStatusCode(404)
                    .putHeaders("Content-Type", "text/html; charset=utf-8")
                    .setBody(ByteString.copyFrom(custom))
                    .setFragmentIndex(0)
                    .setTotalFragments(1)
                    .build();
        }
        return HttpFrames.jsonError(404, "资源不存在: " + cleanPath);
    }

    /** 500 响应帧（支持自定义错误页 registerErrorPage(500)）。 */
    private FrameProto.HttpResponseFrame internalError(String path) {
        byte[] custom = webRegistry == null ? null : webRegistry.errorPage(500);
        if (custom != null) {
            return FrameProto.HttpResponseFrame.newBuilder()
                    .setStatusCode(500)
                    .putHeaders("Content-Type", "text/html; charset=utf-8")
                    .setBody(ByteString.copyFrom(custom))
                    .setFragmentIndex(0)
                    .setTotalFragments(1)
                    .build();
        }
        return FrameProto.HttpResponseFrame.newBuilder()
                .setStatusCode(500)
                .putHeaders("Content-Type", "text/plain; charset=utf-8")
                .setBody(ByteString.copyFrom("Internal Server Error".getBytes(StandardCharsets.UTF_8)))
                .setFragmentIndex(0)
                .setTotalFragments(1)
                .build();
    }

    // ===== 资源解析 =====
    /** favicon 解析顺序：1) 本地插件配置目录 web/favicon.ico（磁盘，可热替换）→ 2) jar 内置 /dist/favicon.ico */
    private byte[] resolveFavicon() {
        if (webRoot != null) {
            File f = new File(webRoot, "favicon.ico");
            try {
                if (f.getCanonicalPath().startsWith(webRootCanonical)
                        && f.isFile() && f.length() <= 16L * 1024 * 1024) {
                    return readFile(f);
                }
            } catch (Exception ignored) {
            }
        }
        return readResource("/dist/favicon.ico");
    }

    /**
     * 静态资源解析（磁盘 webroot 优先，jar 内置 /dist/ 兜底）。
     * 路径无扩展名时支持两种形式：先试 {@code path.html}（/login → login.html），
     * 再试 {@code path/index.html}（目录语义）；返回实际命中文件名（Content-Type 判定用）。
     */
    private Hit resolveResource(String cleanPath) {
        String relative = cleanPath.startsWith("/") ? cleanPath.substring(1) : cleanPath;
        if (relative.isEmpty()) relative = "index.html";
        String[] candidates;
        if (hasExtension(relative) || relative.equals("index.html")) {
            candidates = new String[]{relative};
        } else {
            String base = relative.endsWith("/") ? relative.substring(0, relative.length() - 1) : relative;
            candidates = new String[]{base + ".html", (base.isEmpty() ? "" : base + "/") + "index.html"};
        }
        for (String c : candidates) {
            // 1) 磁盘 webroot（经 Web 内容缓存：pinned 常驻 / LRU 存活 / 大文件走加载器）
            if (webRoot != null) {
                File f = new File(webRoot, c);
                try {
                    if (f.getCanonicalPath().startsWith(webRootCanonical) && f.isFile()) {
                        if (largeFileMaxBytes > 0 && f.length() > largeFileMaxBytes) {
                            LogKit.warn("[HTTP-Over-MC] 静态资源超过大文件上限，拒绝加载: /" + c
                                    + " size=" + f.length() + " limit=" + largeFileMaxBytes);
                            continue;
                        }
                        byte[] body = loadBytes("/" + c, f, () -> {
                            try {
                                return readFile(f);
                            } catch (IOException e) {
                                return null;
                            }
                        });
                        if (body != null) {
                            return new Hit(c, body);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            // 2) jar 内置 /dist/（经缓存；null 表示未命中）
            byte[] rb = loadBytes("/dist/" + c, () -> readResource("/dist/" + c));
            if (rb != null) {
                return new Hit(c, rb);
            }
        }
        return null;
    }

    /** 经 Web 内容缓存取字节（无磁盘文件来源：注册页 / jar 资源）。 */
    private byte[] loadBytes(String path, java.util.function.Supplier<byte[]> loader) {
        return webContent == null ? safeGet(loader) : webContent.bytes(path, loader);
    }

    /** 经 Web 内容缓存取字节（磁盘静态资源：支持 pinned / 大文件加载器 / LRU / 热替换失效）。 */
    private byte[] loadBytes(String path, File file, java.util.function.Supplier<byte[]> loader) {
        return webContent == null ? safeGet(loader) : webContent.bytes(path, file, loader);
    }

    private static byte[] safeGet(java.util.function.Supplier<byte[]> loader) {
        try {
            return loader == null ? null : loader.get();
        } catch (Throwable t) {
            return null;
        }
    }

    /** 命中的静态资源（文件名 + 字节内容）。 */
    private static final class Hit {
        final String name;
        final byte[] bytes;

        Hit(String name, byte[] bytes) {
            this.name = name;
            this.bytes = bytes;
        }
    }

    /** 路径最后一段是否含扩展名（无扩展名视为目录 → 找 index.html） */
    private static boolean hasExtension(String p) {
        String last = p;
        int slash = p.lastIndexOf('/');
        if (slash >= 0) last = p.substring(slash + 1);
        return last.indexOf('.') >= 0;
    }

    private byte[] readResource(String resource) {
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            if (in == null) return null;
            return toBytes(in);
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] readFile(File f) throws IOException {
        try (FileInputStream in = new FileInputStream(f)) {
            return toBytes(in);
        }
    }

    private static byte[] toBytes(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }

    // ===== JSON 响应（注解式 API 序列化） =====
    private static FrameProto.HttpResponseFrame jsonResponse(int code, String json) {
        return jsonResponse(code, json, null);
    }

    /** 带附加响应头（如离线 cookie 升级的 Set-Cookie / X-Soys-New-Token）。 */
    private static FrameProto.HttpResponseFrame jsonResponse(int code, String json, Map<String, String> extra) {
        FrameProto.HttpResponseFrame.Builder b = FrameProto.HttpResponseFrame.newBuilder()
                .setStatusCode(code)
                .putHeaders("Content-Type", "application/json; charset=utf-8")
                .setBody(ByteString.copyFrom(json.getBytes(StandardCharsets.UTF_8)))
                .setFragmentIndex(0)
                .setTotalFragments(1);
        if (extra != null) {
            for (Map.Entry<String, String> e : extra.entrySet()) {
                b.putHeaders(e.getKey(), e.getValue());
            }
        }
        return b.build();
    }

    private static String stripQuery(String p) {
        int q = p.indexOf('?');
        return q >= 0 ? p.substring(0, q) : p;
    }

    private static String decode(String p) {
        if (p == null) return "/";
        try {
            return java.net.URLDecoder.decode(p, "UTF-8");
        } catch (Exception e) {
            return p;
        }
    }
}
