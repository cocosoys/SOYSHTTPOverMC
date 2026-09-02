package com.github.cocosoys.mc.soyshttpovermc.i18n;

import com.github.cocosoys.mc.soyshttpovermc.spi.ConfigSection;
import com.github.cocosoys.mc.soyshttpovermc.spi.Platform;
import com.github.cocosoys.mc.soyshttpovermc.spi.Platforms;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 命名空间语言作用域：一个独立的翻译键空间，含一个逻辑前缀。
 * <p>注册的插件作用域默认前缀形如 {@code plugins.<插件名称>.language.<语言>}，
 * 无头作用域前缀形如 {@code headless.<标识>.language.<语言>}。消息以<b>去前缀的原始键</b>
 * 存于内存 {@link Map}，{@link #t} 翻译时自动补全前缀 → 「载入写入前缀 / 翻译带前缀」同步一致。</p>
 */
public class I18nScope {

    private final String name;
    private final String prefix;
    private volatile Map<String, String> messages = Collections.emptyMap();

    I18nScope(String name, String prefix) {
        this.name = name;
        this.prefix = prefix == null ? "" : prefix;
    }

    /**
     * 从语言文件读取内容（键保持去前缀）。
     */
    void load(File file) {
        if (file == null || !file.isFile()) {
            this.messages = Collections.emptyMap();
            return;
        }
        try {
            Platform p = Platforms.getOrNull();
            if (p == null) {
                this.messages = Collections.emptyMap();
                return;
            }
            ConfigSection cfg = p.loadYaml(file);
            Map<String, String> map = new HashMap<>();
            for (String key : cfg.getKeys(true)) {
                Object v = cfg.get(key);
                if (v != null) {
                    map.put(key, String.valueOf(v));
                }
            }
            this.messages = map;
        } catch (Exception e) {
            this.messages = Collections.emptyMap();
        }
    }

    /**
     * 直接注入键值对（无头登记用）。
     */
    void putAll(Map<String, String> entries) {
        Map<String, String> map = new HashMap<>(entries);
        this.messages = map;
    }

    /**
     * 作用域标识（插件名 / 无头标识）。
     */
    public String name() {
        return name;
    }

    /**
     * 逻辑前缀（如 plugins.myplugin.language.zh_cn）。
     */
    public String prefix() {
        return prefix;
    }

    /**
     * 是否已存在该去前缀键。
     */
    public boolean has(String key) {
        return messages.containsKey(key);
    }

    /**
     * 翻译：用去前缀的 {@code key} 查表，命中即返回；未命中回退 {@code fallback}。
     * 若 {@code key} 已带本作用域前缀，也兼容去除前缀后再查。
     */
    public String t(String key, String fallback, Object... args) {
        if (key == null) {
            return fallback;
        }
        String lookup = key;
        // 兼容传入已带前缀的完整键
        if (!prefix.isEmpty() && key.startsWith(prefix + ".")) {
            lookup = key.substring(prefix.length() + 1);
        }
        String v = messages.get(lookup);
        String template = v != null ? v : fallback;
        return format(template, args);
    }

    /**
     * 文本占位符 {@code {0} {1}...} 替换。
     */
    private static String format(String template, Object[] args) {
        if (template == null) {
            return null;
        }
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
