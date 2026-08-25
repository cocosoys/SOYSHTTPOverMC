package com.github.cocosoys.mc.soyshttpovermc.i18n;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * YAML 语言包实现：把 {@code language/<code>.yml} 整体加载进内存 Map，运行期纯内存查表。
 * <p>加载时使用 {@link YamlConfiguration#getKeys(boolean)} 平铺嵌套键（点路径），
 * 例如 YAML 中的 {@code gift.claim.success} 直接成为键名。</p>
 */
public class YamlLanguageBundle implements ILanguageBundle {

    private final String code;
    private volatile Map<String, String> messages = Collections.emptyMap();

    public YamlLanguageBundle(String code) {
        this.code = code == null || code.isEmpty() ? "unknown" : code.toLowerCase();
    }

    /** 直接以已有键值构造语言包（合并/覆盖场景用）。 */
    public YamlLanguageBundle(String code, Map<String, String> entries) {
        this(code);
        this.messages = new HashMap<>(entries == null ? Collections.emptyMap() : entries);
    }

    /** 返回当前内存键值（副本）。 */
    public Map<String, String> entries() {
        return new HashMap<>(messages);
    }

    /**
     * 从文件加载语言包到内存。
     *
     * @param file 语言文件（如 {@code <dataFolder>/language/zh_cn.yml}）
     * @return 加载是否成功（文件不存在返回 false，保持空包）
     */
    public boolean load(File file) {
        if (file == null || !file.isFile()) {
            return false;
        }
        try {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            Map<String, String> map = new HashMap<>();
            for (String key : cfg.getKeys(true)) {
                Object v = cfg.get(key);
                if (v != null) {
                    map.put(key, String.valueOf(v));
                }
            }
            this.messages = map;
            return true;
        } catch (Exception e) {
            this.messages = Collections.emptyMap();
            return false;
        }
    }

    /**
     * 从 YAML 配置直接加载（资源文件内嵌时用）。{@code sourcePath} 指向 jar 内资源。
     * <p>内部先读入 {@code YamlConfiguration}（Bukkit 的 {@link YamlConfiguration#loadConfiguration(File)}
     * 支持从文件加载；资源流请调用本类扩展方法或先落盘为临时文件）。</p>
     *
     * @param source 已解析好的 YamlConfiguration
     */
    public void load(YamlConfiguration source) {
        if (source == null) {
            this.messages = Collections.emptyMap();
            return;
        }
        Map<String, String> map = new HashMap<>();
        for (String key : source.getKeys(true)) {
            Object v = source.get(key);
            if (v != null) {
                map.put(key, String.valueOf(v));
            }
        }
        this.messages = map;
    }

    @Override
    public String languageCode() {
        return code;
    }

    @Override
    public boolean isLoaded() {
        return !messages.isEmpty();
    }

    /** 当前内存中缓存的翻译文本条数。 */
    public int messagesSize() {
        return messages.size();
    }

    @Override
    public String get(String key, String defaultText) {
        if (key == null) {
            return defaultText;
        }
        String v = messages.get(key);
        return v != null ? v : defaultText;
    }

    @Override
    public String format(String key, String defaultText, Object... args) {
        String template = get(key, defaultText);
        if (args == null || args.length == 0) {
            return template;
        }
        for (int i = 0; i < args.length; i++) {
            if (args[i] == null) {
                continue;
            }
            template = template.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return template;
    }
}