package com.github.cocosoys.mc.soyshttpovermc.i18n;

import com.github.cocosoys.mc.soyshttpovermc.spi.ConfigSection;
import com.github.cocosoys.mc.soyshttpovermc.spi.Platform;
import com.github.cocosoys.mc.soyshttpovermc.spi.Platforms;
import lombok.CustomLog;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 可注册的语言源：某插件/管理员额外提供的语言包来源，加载进全局（默认）语言作用域。
 *
 * <p>每个来源必须能<b>唯一识别它所翻译的语言</b>，二者取其一：</p>
 * <ul>
 *   <li><b>指定语言</b>：{@code language} 非空 → 该来源仅绑定到该语言，{@code source} 不做任何占位符处理
 *       （文件/文件夹/网络文件：文件夹按 {@code <文件夹>/<language>.yml} 取）；</li>
 *   <li><b>语言模板</b>：{@code language} 为空 → {@code source} 必须含 {@code {0}} 占位符，
 *       加载时把 {@code {0}} 替换为当前语言代码（如 {@code test/{0}.yml}、{@code https://...?language={0}.yml}）；
 *       若 language 为空且 source 不含 {@code {0}}，则无法识别语言，属书写错误，注册时会被拒绝。</li>
 * </ul>
 *
 * <p>{@code source} 可为相对/绝对文件或文件夹、网络文件；网络 URL 可用反引号包裹（配置读取时会去掉）。
 * 经 {@link I18n#registerLanguageSource} 注册后，在语言加载（clear 清空 / overlay 覆盖 / 国际化）时参与合并。</p>
 *
 * <p><b>失败告警</b>：网络拉取失败、本地文件解析失败等情形<b>不再静默返回 null</b>，
 * 而是经 {@code @CustomLog} 打印 warn 级别日志（含来源名称/URL/原因），便于运维定位。
 * 失败后仍返回 null/空集合以保持降级语义（不阻塞其他来源加载）。</p>
 */
@CustomLog
final class LanguageSource {

    private final String name;
    private final String description;
    private final String language;   // 指定的语言（非空 = 绑定语言，不做占位符处理；空 = 必须含 {0} 模板）
    private final String raw;        // source 原始路径/URL 描述
    private final File baseDir;      // 相对路径的基准目录（注册者数据目录；null=以当前工作目录为基准）
    private final boolean url;       // 是否是网络来源
    private final boolean template;  // 是否含 {0} 占位符
    private volatile boolean enabled = true;   // 是否参与加载（可经 /soyshttp lang sources on/off 运行时停用/启用）

    /**
     * 语言占位符（language 为空时 source 必须含它）。
     */
    static final String PLACEHOLDER = "{0}";

    LanguageSource(String name, String description, String language, String source, File baseDir) {
        this.name = name == null ? "" : name;
        this.description = description == null ? "" : description;
        this.language = language == null ? "" : language.trim();
        this.raw = source == null ? "" : source.trim();
        this.baseDir = baseDir;
        this.url = this.raw.toLowerCase().startsWith("http://") || this.raw.toLowerCase().startsWith("https://");
        this.template = this.raw.contains(PLACEHOLDER);
    }

    /**
     * 来源名称（配置/展示用）。
     */
    String name() {
        return name;
    }

    /**
     * 来源描述。
     */
    String description() {
        return description;
    }

    /**
     * 绑定语言代码；空串 = 语言模板源（适用于任意语言）。
     */
    String language() {
        return language;
    }

    /**
     * 原始 source 描述（日志/展示用）。
     */
    String raw() {
        return raw;
    }

    /**
     * 该来源当前是否启用。
     */
    boolean enabled() {
        return enabled;
    }

    /**
     * 设置启用状态（停用后该来源不再参与语言合并）。
     */
    void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 是否为网络来源（http/https URL）。
     */
    boolean isUrl() {
        return url;
    }

    /**
     * 解析后的 source（{0} 占位符已替换为指定语言代码）。
     */
    String resolvedSource(String code) {
        return resolved(code);
    }

    /**
     * 仅网络源：拉取翻译文本（UTF-8）；非网络源或失败返回 null。
     */
    String fetchText(String code) {
        if (!url) return null;
        return fetch(resolved(code));
    }

    /**
     * 从本地文件加载翻译键值（供网络源本地缓存读取）。
     */
    Map<String, String> loadLocal(File file) {
        if (file == null || !file.isFile()) return null;
        try {
            Platform p = Platforms.getOrNull();
            return p == null ? null : parse(p.loadYaml(file));
        } catch (Throwable t) {
            log.warnT("log.i18n.local-load-fail",
                    "[i18n] 语言源 {0} 本地缓存解析失败: {1} - {2}",
                    name, file.getAbsolutePath(), t.getMessage());
            return null;
        }
    }

    /**
     * 该来源是否适用于要加载的语言代码（指定语言源仅匹配自身语言；模板源对任意语言生效）。
     */
    boolean appliesToLanguage(String code) {
        if (code == null) return false;
        return language.isEmpty() || language.equalsIgnoreCase(code);
    }

    /**
     * 为指定语言代码解析出键值对；来源不可用/加载失败返回 {@code null}（调用方跳过）。
     * 调用方需先经 {@link #appliesToLanguage} 确认语言匹配。
     *
     * <p>失败时（网络异常、HTTP 非 2xx、本地文件缺失、YAML 解析失败）<b>不再静默</b>，
     * 经 {@code @CustomLog} 打印 warn 日志（含来源名称/语言代码/原因），便于运维定位。</p>
     */
    Map<String, String> load(String code) {
        try {
            if (url) {
                String u = resolved(code);
                String text = fetch(u);
                if (text == null || text.isEmpty()) {
                    // fetch() 内部已打印告警，此处避免重复日志
                    return null;
                }
                Map<String, String> m = parse(text);
                if (m == null) {
                    log.warnT("log.i18n.fetch-parse-fail",
                            "[i18n] 语言源 {0} 网络内容 YAML 解析失败: language={1} url={2}",
                            name, code, u);
                }
                return m;
            }
            File target = resolveLocal(code);
            if (target == null || !target.isFile()) {
                log.warnT("log.i18n.local-file-missing",
                        "[i18n] 语言源 {0} 本地文件不存在: language={1} path={2}",
                        name, code, target == null ? "(null)" : target.getAbsolutePath());
                return null;
            }
            Platform p = Platforms.getOrNull();
            Map<String, String> m = p == null ? null : parse(p.loadYaml(target));
            if (m == null) {
                log.warnT("log.i18n.local-parse-fail",
                        "[i18n] 语言源 {0} 本地文件 YAML 解析失败: language={1} path={2}",
                        name, code, target.getAbsolutePath());
            }
            return m;
        } catch (Throwable t) {
            log.warnT("log.i18n.load-error",
                    "[i18n] 语言源 {0} 加载异常: language={1} - {2}",
                    name, code, t.getMessage());
            return null;
        }
    }

    /**
     * 把 {@code {0}} 替换为当前语言代码（无占位符则原样使用）。
     */
    private String resolved(String code) {
        return template ? raw.replace(PLACEHOLDER, code) : raw;
    }

    /**
     * 解析本地路径到具体语言文件。
     */
    private File resolveLocal(String code) {
        String p = resolved(code);
        File f = new File(p);
        if (!f.isAbsolute() && baseDir != null) {
            f = new File(baseDir, p);
        }
        if (f.isDirectory()) {
            // 文件夹：按 <文件夹>/<语言>.yml 约定取对应语言文件
            return new File(f, code + ".yml");
        }
        return f;
    }

    /**
     * GET 拉取网络文件文本（UTF-8）；失败时<b>告警</b>并返回 {@code null}（保持降级语义）。
     * 告警含来源名称/HTTP 状态/异常原因/URL，便于运维定位网络问题或配置错误。
     * 支持重试：最多重试 MAX_FETCH_RETRIES 次，每次间隔 RETRY_INTERVAL_MS 毫秒。
     */
    private static final int MAX_FETCH_RETRIES = 2;
    private static final long RETRY_INTERVAL_MS = 1000L;

    private String fetch(String urlString) {
        for (int attempt = 0; attempt <= MAX_FETCH_RETRIES; attempt++) {
            if (attempt > 0) {
                try {
                    Thread.sleep(RETRY_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
            String result = fetchOnce(urlString);
            if (result != null) {
                return result;
            }
            if (attempt < MAX_FETCH_RETRIES) {
                log.infoT("log.i18n.fetch-retry",
                        "[i18n] 语言源 {0} 网络拉取失败，正在重试 ({1}/{2}): {3}",
                        name, attempt + 1, MAX_FETCH_RETRIES, urlString);
            }
        }
        return null;
    }

    /**
     * 单次网络拉取（无重试）。
     */
    private String fetchOnce(String urlString) {
        HttpURLConnection conn = null;
        try {
            URL u = new URL(urlString);
            conn = (HttpURLConnection) u.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                try (InputStream in = conn.getInputStream()) {
                    byte[] buf = new byte[8192];
                    java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                    int n;
                    while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                    return new String(bos.toByteArray(), StandardCharsets.UTF_8);
                }
            }
            log.warnT("log.i18n.fetch-http-error",
                    "[i18n] 语言源 {0} 网络拉取失败: HTTP {1} - {2}",
                    name, code, urlString);
            return null;
        } catch (Throwable t) {
            log.warnT("log.i18n.fetch-exception",
                    "[i18n] 语言源 {0} 网络拉取异常: {1} - {2}",
                    name, t.getClass().getSimpleName() + ": " + t.getMessage(), urlString);
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * 从 YAML 文本解析键值（平铺点路径）。
     */
    private Map<String, String> parse(String yamlText) {
        ConfigSection cfg = loadFromText(yamlText);
        return cfg == null ? null : flatten(cfg);
    }

    private Map<String, String> parse(ConfigSection cfg) {
        return cfg == null ? null : flatten(cfg);
    }

    private static ConfigSection loadFromText(String yamlText) {
        try {
            Platform p = Platforms.getOrNull();
            return p == null ? null : p.loadYaml(yamlText);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Map<String, String> flatten(ConfigSection cfg) {
        Map<String, String> m = new HashMap<>();
        for (String key : cfg.getKeys(true)) {
            Object v = cfg.get(key);
            if (v != null && !(v instanceof ConfigSection)) {
                m.put(key, String.valueOf(v));
            }
        }
        return m;
    }
}
