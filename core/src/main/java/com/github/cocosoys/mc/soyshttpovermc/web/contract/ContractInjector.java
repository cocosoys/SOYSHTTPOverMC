package com.github.cocosoys.mc.soyshttpovermc.web.contract;

import com.github.cocosoys.mc.soyshttpovermc.HttpOverMcPlugin;
import com.github.cocosoys.mc.soyshttpovermc.util.JsonWriter;
import com.github.cocosoys.mc.soyshttpovermc.web.ApiRegistry;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 前端托管自动适配器（契约注入 + HTML 资源引用改写），主插件内置、对附属插件透明：
 *
 * <p><b>1. 契约注入</b>：约定文件名 {@code __SOYS_CONTEXT__.js}（存放路径不限，按文件名的
 * 最后一段识别）。请求命中该文件时，把内容中的占位符 {@code __SOYS_CONTEXT__} 替换为当前
 * 服务器的真实环境原语（scheme / host / port / apiPrefix / pluginsPrefix / apiFullPrefix /
 * pageFullPrefix / webResourcePrefix），使前端无需关心部署环境（换服务器、改 api-prefix 等均自动适配）。
 *
 * <p><b>2. HTML 资源引用改写</b>：托管在 {@code /web/plugins/<插件名>/} 下的前端页面，若其
 * HTML 内使用绝对路径资源引用（{@code /static/...}、{@code /assets/...}、{@code /favicon.ico}
 * 等），响应时自动补当前页面所属插件的前缀，避免 404。前端可在契约文件中声明排除列表：
 * <pre>{@code
 * window.SOYS_CONTEXT_EXCLUDES = ["/prod-api", "/api"];
 * }</pre>
 * 命中排除前缀（前缀匹配）的绝对路径不改写；未声明时默认排除 {@code /api}（SOYS API 保留前缀），
 * 应用自定义 API 前缀（如 {@code /prod-api}）必须显式声明。
 *
 * <p>两类处理均为<b>响应时替换</b>：每次请求读取内容后处理，重载 config.yml 后
 * host/port/前缀变化天然生效；契约注入对象仅含原语（不含 origin 拼接、不含群组服 serverPrefix）。
 *
 * <pre>
 * window.SOYS_CONTEXT = {
 *   "scheme": "http",                          // TLS 启用 → https
 *   "host": "play.example.com",                // public-host → mc.host → server-ip 回退
 *   "port": 25574,                             // public-port → mc.port → server-port 回退
 *   "apiPrefix": "/api",                       // 网关 api-prefix
 *   "pluginsPrefix": "/plugins/SOYSHTTPOverMC",             // 按登记插件 owner 计算；主插件 ""
 *   "apiFullPrefix": "/api/plugins/SOYSHTTPOverMC",          // apiPrefix + pluginsPrefix 归一
 *   "pageFullPrefix": "/web/plugins/SOYSHTTPOverMC",         // 页面 URL 前缀（主插件 ""）
 *   "webResourcePrefix": "/web/plugins/SOYSHTTPOverMC/page"  // 快捷注册资源默认前缀
 * };
 * </pre>
 */
public class ContractInjector {

    /**
     * 约定契约文件名（大小写敏感，仅匹配路径最后一段）。
     */
    public static final String CONTRACT_FILE_NAME = "__SOYS_CONTEXT__.js";

    /**
     * 内容中的注入占位符，替换为环境原语 JSON 对象字面量。
     */
    private static final String PLACEHOLDER = "__SOYS_CONTEXT__";

    /**
     * exclude 声明锚：契约文件中的 {@code window.SOYS_CONTEXT_EXCLUDES = [...]}。
     */
    private static final Pattern EXCLUDES_PATTERN = Pattern.compile(
            "window\\.SOYS_CONTEXT_EXCLUDES\\s*=\\s*\\[([^\\]]*)\\]");

    /**
     * 未声明排除列表时的默认排除：SOYS API 保留前缀（避免把 API 请求改写成插件资源路径）。
     */
    private static final List<String> DEFAULT_EXCLUDES = Collections.singletonList("/api");

    /** 参与改写白名单的标签与属性：{标签, 属性}（不含 &lt;a href&gt; 导航）。 */
    private static final String[][] HTML_ATTRS = {
            {"script", "src"}, {"img", "src"}, {"link", "href"}, {"source", "src"},
            {"video", "src"}, {"audio", "src"}, {"iframe", "src"}, {"embed", "src"}, {"input", "src"}
    };

    /** 预编译的 HTML 标签属性匹配：组1=标签前缀，组2=引号，组3=绝对路径值。 */
    private static final List<Pattern> TAG_PATTERNS = buildTagPatterns();

    private final HttpOverMcPlugin host;
    private final ApiRegistry apiRegistry;

    /**
     * 各插件声明的排除列表缓存（ownerName -> excludes）；null/未登记 = 默认排除 /api。
     * 登记时由 WebRegistry 填充；契约文件响应时兜底填充（磁盘惰性资源场景）。
     */
    private final ConcurrentHashMap<String, List<String>> excludesByOwner = new ConcurrentHashMap<>();

    public ContractInjector(HttpOverMcPlugin host, ApiRegistry apiRegistry) {
        this.host = host;
        this.apiRegistry = apiRegistry;
    }

    /**
     * 若 path 命中约定契约文件且内容含占位符，则注入后返回新 byte[]；否则原样返回。
     * 顺带解析内容中的 exclude 声明并缓存（响应时兜底，覆盖登记时无法读内容的场景）。
     *
     * @param path      请求命中的资源路径（取最后一段做文件名匹配）
     * @param ownerName 登记该资源的插件名；null/空 → 视为主插件（SOYSHTTPOverMC 自身）
     * @param content   原始内容（可为 null，直接返回）
     */
    public byte[] maybeInject(String path, String ownerName, byte[] content) {
        if (content == null || path == null) {
            return content;
        }
        if (!CONTRACT_FILE_NAME.equals(fileNameOf(path))) {
            return content;
        }
        String owner = resolveOwner(ownerName);
        registerExcludes(owner, content);
        String text = new String(content, StandardCharsets.UTF_8);
        if (!text.contains(PLACEHOLDER)) {
            return content;
        }
        return text.replace(PLACEHOLDER, buildContextJson(owner)).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 若 path 为 HTML 页面内容，则把绝对路径资源引用改写为当前页面所属插件的前缀路径；
     * 否则原样返回。仅当 owner 非主插件（页面托管在 /web/plugins/&lt;名&gt;/ 下）时改写。
     *
     * @param path      请求命中的资源路径
     * @param ownerName 登记该资源的插件名；null/空 → 视为主插件
     * @param content   原始 HTML 内容（可为 null，直接返回）
     */
    public byte[] maybeRewriteHtml(String path, String ownerName, byte[] content) {
        if (content == null || path == null) {
            return content;
        }
        String owner = resolveOwner(ownerName);
        if (owner.equals(host.getName())) {
            return content; // 主插件页面本身在根路径，无需改写
        }
        String prefix = "/web/plugins/" + owner;
        List<String> excludes = excludesOf(owner);
        String text = new String(content, StandardCharsets.UTF_8);
        String rewritten = rewriteHtml(text, prefix, excludes);
        return rewritten.equals(text) ? content : rewritten.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 登记某插件声明的排除列表（由 WebRegistry 在契约文件登记时调用）。
     * 契约文件内容中无 {@code window.SOYS_CONTEXT_EXCLUDES} 声明时不动缓存（保持默认排除）。
     */
    public void registerExcludes(String ownerName, byte[] contractContent) {
        if (ownerName == null || contractContent == null) {
            return;
        }
        List<String> parsed = parseExcludes(new String(contractContent, StandardCharsets.UTF_8));
        if (parsed != null) {
            excludesByOwner.put(ownerName, parsed);
        }
    }

    /**
     * 某插件生效的排除列表：已声明 → 声明值；未声明 → 默认排除 /api。
     */
    public List<String> excludesOf(String ownerName) {
        if (ownerName == null) {
            return DEFAULT_EXCLUDES;
        }
        List<String> ex = excludesByOwner.get(ownerName);
        return ex != null ? ex : DEFAULT_EXCLUDES;
    }

    // ===== 内部实现 =====

    private static List<Pattern> buildTagPatterns() {
        List<Pattern> list = new ArrayList<>(HTML_ATTRS.length);
        for (String[] tagAttr : HTML_ATTRS) {
            list.add(Pattern.compile(
                    "(<" + tagAttr[0] + "\\b[^>]*\\b" + tagAttr[1] + "\\s*=\\s*)([\"'])(/[^\"'\\s>]*)\\2",
                    Pattern.CASE_INSENSITIVE));
        }
        return Collections.unmodifiableList(list);
    }

    private static String rewriteHtml(String html, String prefix, List<String> excludes) {
        for (Pattern p : TAG_PATTERNS) {
            Matcher m = p.matcher(html);
            StringBuffer out = null;
            while (m.find()) {
                String value = m.group(3);
                if (!shouldRewrite(value, prefix, excludes)) {
                    continue;
                }
                if (out == null) {
                    out = new StringBuffer(html.length() + 64);
                }
                m.appendReplacement(out, Matcher.quoteReplacement(
                        m.group(1) + m.group(2) + prefix + value + m.group(2)));
            }
            if (out != null) {
                m.appendTail(out);
                html = out.toString();
            }
        }
        return html;
    }

    private static boolean shouldRewrite(String value, String prefix, List<String> excludes) {
        if (!value.startsWith("/")) {
            return false;                 // 相对路径不改
        }
        if (value.startsWith("//")) {
            return false;                 // 协议相对（//cdn.xxx）不改
        }
        if (value.startsWith(prefix)) {
            return false;                 // 已带本插件前缀
        }
        if (value.startsWith("/web/plugins/")) {
            return false;                 // 已是插件命名空间
        }
        if (excludes != null) {
            for (String ex : excludes) {
                if (ex != null && !ex.isEmpty() && value.startsWith(ex)) {
                    return false;         // 命中排除前缀（如 /api、/prod-api）
                }
            }
        }
        return true;
    }

    /**
     * 解析契约内容中的 {@code window.SOYS_CONTEXT_EXCLUDES = [...]}。
     *
     * @return 声明值（可为空列表 = 显式声明无排除）；未声明返回 null
     */
    private static List<String> parseExcludes(String text) {
        Matcher m = EXCLUDES_PATTERN.matcher(text);
        if (!m.find()) {
            return null;
        }
        List<String> out = new ArrayList<>();
        String body = m.group(1);
        int i = 0;
        while (i < body.length()) {
            char c = body.charAt(i);
            if (c == '\'' || c == '"') {
                int end = body.indexOf(c, i + 1);
                if (end < 0) {
                    break;
                }
                String item = body.substring(i + 1, end).trim();
                if (!item.isEmpty()) {
                    out.add(item);
                }
                i = end + 1;
            } else {
                i++;
            }
        }
        return out;
    }

    private String resolveOwner(String ownerName) {
        return (ownerName == null || ownerName.isEmpty()) ? host.getName() : ownerName;
    }

    private static String fileNameOf(String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private String buildContextJson(String owner) {
        String scheme = (host.getTlsFactory() != null && host.getTlsFactory().getSSLContext() != null)
                ? "https" : "http";
        String hostVal = host.getMcHost();
        if (hostVal == null || hostVal.isEmpty()) {
            hostVal = "localhost";
        }
        int port = host.getMcPort();
        if (port <= 0) {
            port = 25565;
        }
        String apiPrefix = normalizeApiPrefix();
        boolean mainPlugin = owner.equals(host.getName());
        String pluginsPrefix = mainPlugin ? "" : "/plugins/" + owner;
        String apiFullPrefix = joinFull(apiPrefix, pluginsPrefix);
        String pageFullPrefix = mainPlugin ? "" : "/web/plugins/" + owner;
        String webResourcePrefix = "/web/plugins/" + owner + "/page";

        // 契约 JSON 统一经 JsonWriter 输出（键序由 LinkedHashMap 保持，值统一转义）
        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("scheme", scheme);
        contract.put("host", hostVal);
        contract.put("port", port);
        contract.put("apiPrefix", apiPrefix);
        contract.put("pluginsPrefix", pluginsPrefix);
        contract.put("apiFullPrefix", apiFullPrefix);
        contract.put("pageFullPrefix", pageFullPrefix);
        contract.put("webResourcePrefix", webResourcePrefix);
        return JsonWriter.write(contract);
    }

    private String normalizeApiPrefix() {
        if (apiRegistry == null) {
            return "/api";
        }
        String p = apiRegistry.getPathPrefix();
        return (p == null || p.trim().isEmpty()) ? "/api" : p.trim();
    }

    /**
     * 与 ApiToolkitImpl.apiFullPrefix 同语义：apiPrefix + pluginsPrefix 归一拼接。
     */
    private static String joinFull(String apiPrefix, String pluginsPrefix) {
        if (pluginsPrefix.isEmpty()) {
            return apiPrefix;
        }
        if (apiPrefix.isEmpty() || apiPrefix.equals("/")) {
            return pluginsPrefix;
        }
        String b = apiPrefix.endsWith("/") ? apiPrefix.substring(0, apiPrefix.length() - 1) : apiPrefix;
        String p = pluginsPrefix.startsWith("/") ? pluginsPrefix : "/" + pluginsPrefix;
        return b + p;
    }
}
