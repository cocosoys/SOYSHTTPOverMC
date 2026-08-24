package soys.soyshttpovermc.i18n;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.StringReader;
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
 */
final class LanguageSource {

    private final String name;
    private final String description;
    private final String language;   // 指定的语言（非空 = 绑定语言，不做占位符处理；空 = 必须含 {0} 模板）
    private final String raw;        // source 原始路径/URL 描述
    private final File baseDir;      // 相对路径的基准目录（注册者数据目录；null=以当前工作目录为基准）
    private final boolean url;       // 是否是网络来源
    private final boolean template;  // 是否含 {0} 占位符
    private volatile boolean enabled = true;   // 是否参与加载（可经 /soyshttp lang sources on/off 运行时停用/启用）

    /** 语言占位符（language 为空时 source 必须含它）。 */
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

    /** 来源名称（配置/展示用）。 */
    String name() {
        return name;
    }

    /** 来源描述。 */
    String description() {
        return description;
    }

    /** 绑定语言代码；空串 = 语言模板源（适用于任意语言）。 */
    String language() {
        return language;
    }

    /** 原始 source 描述（日志/展示用）。 */
    String raw() {
        return raw;
    }

    /** 该来源当前是否启用。 */
    boolean enabled() {
        return enabled;
    }

    /** 设置启用状态（停用后该来源不再参与语言合并）。 */
    void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** 是否为网络来源（http/https URL）。 */
    boolean isUrl() {
        return url;
    }

    /** 解析后的 source（{0} 占位符已替换为指定语言代码）。 */
    String resolvedSource(String code) {
        return resolved(code);
    }

    /** 仅网络源：拉取翻译文本（UTF-8）；非网络源或失败返回 null。 */
    String fetchText(String code) {
        if (!url) return null;
        return fetch(resolved(code));
    }

    /** 从本地文件加载翻译键值（供网络源本地缓存读取）。 */
    Map<String, String> loadLocal(File file) {
        if (file == null || !file.isFile()) return null;
        try {
            return parse(YamlConfiguration.loadConfiguration(file));
        } catch (Throwable t) {
            return null;
        }
    }

    /** 该来源是否适用于要加载的语言代码（指定语言源仅匹配自身语言；模板源对任意语言生效）。 */
    boolean appliesToLanguage(String code) {
        if (code == null) return false;
        return language.isEmpty() || language.equalsIgnoreCase(code);
    }

    /**
     * 为指定语言代码解析出键值对；来源不可用/加载失败返回 {@code null}（调用方跳过）。
     * 调用方需先经 {@link #appliesToLanguage} 确认语言匹配。
     */
    Map<String, String> load(String code) {
        try {
            if (url) {
                String u = resolved(code);
                String text = fetch(u);
                if (text == null || text.isEmpty()) return null;
                return parse(text);
            }
            File target = resolveLocal(code);
            if (target == null || !target.isFile()) return null;
            return parse(YamlConfiguration.loadConfiguration(target));
        } catch (Throwable t) {
            return null;
        }
    }

    /** 把 {@code {0}} 替换为当前语言代码（无占位符则原样使用）。 */
    private String resolved(String code) {
        return template ? raw.replace(PLACEHOLDER, code) : raw;
    }

    /** 解析本地路径到具体语言文件。 */
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

    /** GET 拉取网络文件文本（UTF-8）；失败返回 {@code null}。 */
    private String fetch(String urlString) {
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
            return null;
        } catch (Throwable t) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** 从 YAML 文本解析键值（平铺点路径）。 */
    private Map<String, String> parse(String yamlText) {
        YamlConfiguration cfg = loadFromText(yamlText);
        return cfg == null ? null : flatten(cfg);
    }

    private Map<String, String> parse(YamlConfiguration cfg) {
        return cfg == null ? null : flatten(cfg);
    }

    private static YamlConfiguration loadFromText(String yamlText) {
        try {
            return YamlConfiguration.loadConfiguration(new StringReader(yamlText));
        } catch (Throwable t) {
            return null;
        }
    }

    private static Map<String, String> flatten(YamlConfiguration cfg) {
        Map<String, String> m = new HashMap<>();
        for (String key : cfg.getKeys(true)) {
            Object v = cfg.get(key);
            if (v != null && !(v instanceof ConfigurationSection)) {
                m.put(key, String.valueOf(v));
            }
        }
        return m;
    }
}