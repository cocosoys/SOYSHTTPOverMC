package com.github.cocosoys.mc.soyshttpovermc.i18n;

import com.github.cocosoys.mc.soyshttpovermc.enums.LanguagePolicy;
import com.github.cocosoys.mc.soyshttpovermc.spi.ConfigSection;
import com.github.cocosoys.mc.soyshttpovermc.spi.Platform;
import com.github.cocosoys.mc.soyshttpovermc.spi.Platforms;
import lombok.CustomLog;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * 国际化门面：全局多作用域翻译环境，从磁盘 {@code language/} 目录加载语言包到内存。
 *
 * <p><b>目录约定</b>：插件自带语言包存放在 {@code language/}（本插件的在
 * {@code <dataFolder>/language/}），文件名即语言代码（{@code zh_cn.yml}）。</p>
 *
 * <p><b>作用域体系</b>：</p>
 * <ul>
 *   <li><b>默认作用域</b>：本插件自身（无前缀，{@code I18n.t("gift.claim.success", ...)}）；</li>
 *   <li><b>插件作用域</b>：{@link #registerPlugin} 注册，开发提供 {@code 语言文件夹 + 插件实例}，
 *       逻辑前缀自动为 {@code plugins.<插件名称>.language.<语言>}；</li>
 *   <li><b>无头作用域</b>：{@link #registerHeadless} 注册，只填键值对（无需文件），
 *       单独存入 {@code headlessScopes} 列表，前缀 {@code headless.<标识>.language.<语言>}。</li>
 * </ul>
 *
 * <p><b>用法</b>：宿主 {@code I18n.init(dataFolder)}；注册插件用 {@code I18n.plugin(你) .t("menu.title", ...)}；
 * 无头用 {@code I18n.headless("xxx").t(...)}；默认作用域沿用 {@code I18n.t(...)}。</p>
 */
@CustomLog
public final class I18n {

    /**
     * jar 内默认语言文件根目录。
     */
    private static final String DEFAULT_RESOURCE_DIR = "language/";

    /**
     * 当前语言代码（当前仅中文 zh_cn）。
     */
    private static volatile String lang = "zh_cn";
    /**
     * 默认（无前缀）语言包：本插件自身消息。
     */
    private static volatile ILanguageBundle current = new YamlLanguageBundle(lang);
    /**
     * 插件作用域：插件名 → I18nScope。
     */
    private static volatile Map<String, I18nScope> pluginScopes = new LinkedHashMap<>();
    /**
     * 无头作用域：单独列表（按注册顺序）。
     */
    private static volatile List<I18nScope> headlessScopes = new ArrayList<>();
    /**
     * 磁盘语言目录（{@code <dataFolder>/language/}）。
     */
    private static volatile File languageDir;
    /**
     * 当前语言加载策略（默认国际化为基底 en_us + 叠加目标语言）。
     */
    private static volatile LanguagePolicy languagePolicy = LanguagePolicy.INTERNATIONALIZATION;
    /**
     * 额外语言源（其他插件/配置注册）。
     */
    private static volatile List<LanguageSource> languageSources = new ArrayList<>();

    private I18n() {
    }

    // ==================== 初始化 ====================

    /**
     * 初始化默认语言环境：补齐 language 目录 + 内置中文包，加载进内存（语言取系统属性 soys.i18n.language，默认 zh_cn）。
     */
    public static void init(File dataFolder) {
        init(dataFolder, System.getProperty("soys.i18n.language", ""));
    }

    /**
     * 初始化默认语言环境并加载指定语言包（code 为空则回退系统属性 / zh_cn）。
     */
    public static void init(File dataFolder, String code) {
        if (dataFolder == null) return;
        File dir = new File(dataFolder, "language");
        if (!dir.exists() && !dir.mkdirs()) {
            log.warnT("log.i18n.dir-create-fail", "[i18n] 创建语言目录失败: {0}", dir.getAbsolutePath());
            languageDir = dir;
            return;
        }
        languageDir = dir;
        ensureDefaultResource(dir, "zh_cn.yml");
        ensureDefaultResource(dir, "en_us.yml");
        String c = (code == null || code.isEmpty()) ? System.getProperty("soys.i18n.language", "zh_cn") : code;
        load(c);
        // 异步预加载网络源到本地缓存，避免后续加载时阻塞主线程
        asyncPreloadNetworkSources(c);
    }

    /**
     * 异步预加载所有网络源到本地缓存（后台线程，不阻塞主线程）。
     * 预加载成功后，后续 load() 优先走本地缓存，离线可用且不阻塞。
     */
    private static void asyncPreloadNetworkSources(String code) {
        List<LanguageSource> sources = languageSources;
        if (sources == null || sources.isEmpty()) return;
        boolean hasUrl = false;
        for (LanguageSource s : sources) {
            if (s.isUrl() && s.enabled() && s.appliesToLanguage(code)) {
                hasUrl = true;
                break;
            }
        }
        if (!hasUrl) return;
        Thread t = new Thread(() -> {
            try {
                for (int i = 0; i < sources.size(); i++) {
                    LanguageSource s = sources.get(i);
                    if (s.isUrl() && s.enabled() && s.appliesToLanguage(code)) {
                        File cache = networkLangFile(s.name(), code);
                        if (cache != null && !cache.isFile()) {
                            String text = s.fetchText(code);
                            if (text != null && !text.isEmpty()) {
                                cache.getParentFile().mkdirs();
                                java.nio.file.Files.write(cache.toPath(),
                                        text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                                log.infoT("log.i18n.network-preloaded",
                                        "[i18n] 网络语言源已异步预加载到本地缓存: {0} ({1} 字节)",
                                        s.name(), text.length());
                            }
                        }
                    }
                }
            } catch (Throwable e) {
                log.warnT("log.i18n.network-preload-fail",
                        "[i18n] 网络语言源异步预加载异常: {0}", e.getMessage());
            }
        }, "I18n-NetworkPreloader");
        t.setDaemon(true);
        t.start();
    }

    /**
     * 加载默认作用域的语言包（磁盘 {@code language/<code>.yml} + 已注册的额外语言源，按当前策略合并）。
     */
    public static boolean load(String code) {
        if (code == null || code.isEmpty() || languageDir == null) return false;
        String c = code.toLowerCase();
        File file = new File(languageDir, c + ".yml");

        // 起步表因策略而异：
        //   OVERLAY            —— 保留既有内存键值（不清空，覆盖同键）；
        //   INTERNATIONALIZATION —— 以 en_us 为基底（先加载一次 en_us，再加目标语言保底）；
        //   CLEAR              —— 从空表起步（清空重载）。
        Map<String, String> compiled;
        switch (languagePolicy) {
            case OVERLAY:
                compiled = currentEntries();
                break;
            case INTERNATIONALIZATION:
                compiled = loadFileEntries("en_us");
                break;
            default:
                compiled = new HashMap<>();
        }

        boolean anyLoaded = false;
        if (file.isFile()) {
            Platform p = Platforms.getOrNull();
            if (p != null) {
                ConfigSection cfg = p.loadYaml(file);
                putAll(compiled, cfg);
                anyLoaded = true;
            }
        }
        for (LanguageSource src : languageSources) {
            if (!src.enabled() || !src.appliesToLanguage(c)) continue;
            Map<String, String> m;
            // 网络源优先从本地缓存加载（离线可用、避免重复网络请求）
            if (src.isUrl()) {
                File cache = networkLangFile(src.name(), c);
                m = (cache != null && cache.isFile()) ? src.loadLocal(cache) : src.load(c);
            } else {
                m = src.load(c);
            }
            if (m != null) {
                compiled.putAll(m);
                anyLoaded = true;
            }
        }
        if (!anyLoaded) {
            log.warnT("log.i18n.file-not-found", "[i18n] 默认语言文件不存在: {0}，保持当前语言", file.getAbsolutePath());
            return false;
        }
        lang = c;
        current = new YamlLanguageBundle(c, compiled);
        log.infoT("log.i18n.bundle-loaded", "[i18n] 默认语言包已加载: {0} ({1} 条)", lang, compiled.size());
        return true;
    }

    /**
     * 读取磁盘 {code}.yml 到键值表（供国际化策略取 en_us 基底；文件不存在返回空表）。
     */
    private static Map<String, String> loadFileEntries(String code) {
        Map<String, String> m = new HashMap<>();
        File f = new File(languageDir, code + ".yml");
        if (f.isFile()) {
            Platform p = Platforms.getOrNull();
            if (p != null) {
                putAll(m, p.loadYaml(f));
            }
        }
        return m;
    }

    // ==================== 语言源注册 / 加载策略 ====================

    /**
     * 设置语言加载策略（{@link LanguagePolicy} 对应配置字符串；null/未知回退默认国际化）。
     */
    public static void setLanguageRule(String rule) {
        languagePolicy = LanguagePolicy.from(rule);
    }

    /**
     * 设置语言加载策略（枚举直接指定）。
     */
    public static void setLanguagePolicy(LanguagePolicy policy) {
        languagePolicy = policy == null ? LanguagePolicy.INTERNATIONALIZATION : policy;
    }

    /**
     * 当前语言加载策略（枚举值）。
     */
    public static LanguagePolicy languagePolicy() {
        return languagePolicy;
    }

    /**
     * 当前语言加载策略（配置字符串形式）。
     */
    public static String languageRule() {
        return languagePolicy.configName();
    }

    /**
     * 注册一个额外语言源（其他插件/配置提供）。每个来源必须能唯一识别它所翻译的语言，二者取其一：
     * <ul>
     *   <li><b>指定语言</b>：{@code language} 非空 → 来源仅绑定该语言，{@code source} 不做任何占位符处理；</li>
     *   <li><b>语言模板</b>：{@code language} 为空 → {@code source} 必须含 {@code {0}} 占位符，加载时替换为当前语言代码。</li>
     * </ul>
     * 若 {@code language} 为空且 {@code source} 不含 {@code {0}}，则无法识别翻译语言，属书写错误，注册会被拒绝并返回 {@code false}。
     *
     * <p>{@code source} 可为相对/绝对文件或文件夹、网络文件；网络 URL 可用反引号包裹（此处自动去除）。
     * 相对路径以 {@code owner} 数据目录为基准。注册后立即按当前策略重载当前语言，使新源生效。</p>
     *
     * @param owner       注册插件（解析相对路径的基准目录；可为 null，此时以工作目录为基准）
     * @param name        来源名称（配置/HUD 展示用；可为 null）
     * @param description 来源描述（可为 null）
     * @param language    绑定的语言代码（如 {@code zh_cn}）；空串 = 语言模板源（source 须含 {@code {0}}）
     * @param source      来源路径/URL 描述
     * @return 是否注册成功（书写错误/路径为空返回 false）
     */
    public static boolean registerLanguageSource(Platform owner, String name, String description, String language, String source) {
        String srcTrim = source == null ? "" : source.trim();
        if (srcTrim.isEmpty()) return false;
        String langTrim = language == null ? "" : language.trim();
        // 语言不可识别即书写错误：language 为空且 source 不含语言占位符
        if (langTrim.isEmpty() && !srcTrim.contains(LanguageSource.PLACEHOLDER)) {
            log.warnT("log.i18n.source-bad-language",
                    "[i18n] 语言源书写错误，已拒绝: {0} —— language 为空时 source 必须包含语言占位符 {1}",
                    name == null ? srcTrim : name, LanguageSource.PLACEHOLDER);
            return false;
        }
        // 去除反引号包裹（配置里 URL 常反引号包裹以免歧义）
        String src = stripBackticks(srcTrim);
        File base = (owner != null && owner.getDataFolder() != null) ? owner.getDataFolder() : null;
        LanguageSource ns = new LanguageSource(name, description, langTrim, src, base);
        List<LanguageSource> list = new ArrayList<>(languageSources);
        list.add(ns);
        languageSources = list;
        log.infoT("log.i18n.source-registered",
                "[i18n] 已注册语言源: {0}（language={1}，source={2}）", ns.name(), ns.language(), ns.raw());
        // 语言目录已就绪时立即重载，使新源生效
        if (languageDir != null) load(lang);
        return true;
    }

    /**
     * 去除首尾反引号（仅当整串被一对反引号包裹时）。
     */
    private static String stripBackticks(String s) {
        if (s.length() >= 2 && s.charAt(0) == '`' && s.charAt(s.length() - 1) == '`') {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    /**
     * 清空全部已注册的语言源（配置重载/重新初始化时调用）。
     */
    public static void clearLanguageSources() {
        languageSources = new ArrayList<>();
    }

    /**
     * 当前已注册的语言源数量（调试用）。
     */
    public static int languageSourceCount() {
        return languageSources.size();
    }

    /**
     * 列出当前全部已注册语言源的信息（索引 / 原始路径 / 是否启用 / 当前语言下提供的条数）。
     * 供 {@code /soyshttp lang sources} 展示；索引固定为注册顺序（停用不改变索引，可据此再次启用）。
     */
    public static List<LanguageSourceInfo> languageSourcesInfo() {
        List<LanguageSourceInfo> out = new ArrayList<>(languageSources.size());
        int i = 0;
        for (LanguageSource s : languageSources) {
            Map<String, String> m = s.enabled() ? s.load(lang) : null;
            out.add(new LanguageSourceInfo(i, s.name(), s.description(), s.language(), s.raw(), s.enabled(), m == null ? 0 : m.size()));
            i++;
        }
        return out;
    }

    /**
     * 启用 / 停用指定索引的语言源（索引即注册顺序，见 {@link #languageSourcesInfo}）。
     * 变更后立即重载当前语言使生效。索引越界返回 false。
     */
    public static boolean setLanguageSourceEnabled(int index, boolean enabled) {
        List<LanguageSource> list = languageSources;
        if (index < 0 || index >= list.size()) return false;
        LanguageSource s = list.get(index);
        if (s.enabled() == enabled) return true;
        s.setEnabled(enabled);
        if (languageDir != null) load(lang);
        return true;
    }

    /**
     * 停用指定索引的语言源（便捷方法，见 {@link #setLanguageSourceEnabled}）。
     */
    public static boolean disableLanguageSource(int index) {
        return setLanguageSourceEnabled(index, false);
    }

    /**
     * 启用指定索引的语言源（便捷方法，见 {@link #setLanguageSourceEnabled}）。
     */
    public static boolean enableLanguageSource(int index) {
        return setLanguageSourceEnabled(index, true);
    }

    // ==================== 网络源本地化管理 ====================

    /**
     * 网络翻译本地缓存根目录：<dataFolder>/lang/network/。
     */
    private static File networkRoot() {
        if (languageDir == null || languageDir.getParentFile() == null) return null;
        return new File(languageDir.getParentFile(), "lang" + File.separator + "network");
    }

    /**
     * 指定网络源的本地缓存目录：<dataFolder>/lang/network/<safeName>/。
     */
    private static File networkSourceDir(String name) {
        String safe = (name == null || name.isEmpty()) ? "unnamed" : name.replaceAll("[^\\w.-]", "_");
        return new File(networkRoot(), safe);
    }

    /**
     * 指定网络源指定语言的本地翻译文件：<dir>/lang/<code>.yml。
     */
    private static File networkLangFile(String name, String code) {
        File root = networkSourceDir(name);
        if (root == null) return null;
        return new File(new File(root, "lang"), code + ".yml");
    }

    /**
     * 指定网络源的本地 config.yml（记录名称/介绍/语言）。
     */
    private static File networkConfigFile(String name) {
        return new File(networkSourceDir(name), "config.yml");
    }

    /**
     * 取出指定索引的语言源（越界返回 null）。
     */
    private static LanguageSource getLanguageSource(int index) {
        List<LanguageSource> list = languageSources;
        if (index < 0 || index >= list.size()) return null;
        return list.get(index);
    }

    /**
     * 将网络来源的翻译下载到本地缓存（{@code lang/network/<name>/lang/<当前语言>.yml}），
     * 并在 {@code lang/network/<name>/config.yml} 中记录名称/介绍/语言。
     * 下载后自动重载使本地翻译生效（后续加载优先走本地缓存，离线可用）。
     *
     * @return 操作结果消息（成功含条数，失败含原因）
     */
    public static String downloadNetworkSource(int index) {
        LanguageSource s = getLanguageSource(index);
        if (s == null) return "§c索引越界（当前共 " + languageSourceCount() + " 个来源）";
        if (!s.isUrl()) return "§c来源 #" + index + "（" + s.name() + "）不是网络来源，无法下载";
        String code = lang;
        if (!s.appliesToLanguage(code)) return "§c来源 #" + index + " 不适用于当前语言 " + code;
        String text = s.fetchText(code);
        if (text == null || text.isEmpty()) return "§c网络获取失败: " + s.resolvedSource(code);

        // 保存翻译文件
        File langFile = networkLangFile(s.name(), code);
        if (langFile == null) return "§c缓存目录未就绪";
        langFile.getParentFile().mkdirs();
        try {
            java.nio.file.Files.write(langFile.toPath(), text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            return "§c保存翻译文件失败: " + e.getMessage();
        }

        // 保存 config.yml
        File configFile = networkConfigFile(s.name());
        Platform p = Platforms.get();
        ConfigSection cfg = p.createYaml();
        cfg.set("name", s.name());
        cfg.set("description", s.description());
        cfg.set("language", s.language().isEmpty() ? code : s.language());
        cfg.set("source", s.raw());
        try {
            p.saveYaml(cfg, configFile);
        } catch (IOException e) {
            log.warnT("log.i18n.network-config-save-fail", "[i18n] 保存网络源 config.yml 失败: {0}", e.getMessage());
        }

        // 重载使本地翻译生效
        load(lang);

        // 统计条数
        int count = 0;
        Map<String, String> m = s.loadLocal(langFile);
        if (m != null) count = m.size();
        log.infoT("log.i18n.network-downloaded",
                "[i18n] 网络源 #{0}({1}) 翻译已下载到本地: {2} ({3} 条)",
                index, s.name(), langFile.getAbsolutePath(), count);
        return "已下载来源 #" + index + "（" + s.name() + "）的 " + code + " 翻译到本地（" + count + " 条）";
    }

    /**
     * 更新本地网络翻译（重新从网络拉取覆盖本地文件）。
     * 若本地缓存不存在则自动执行下载。
     *
     * @return 操作结果消息
     */
    public static String updateNetworkSource(int index) {
        LanguageSource s = getLanguageSource(index);
        if (s == null) return "§c索引越界（当前共 " + languageSourceCount() + " 个来源）";
        if (!s.isUrl()) return "§c来源 #" + index + "（" + s.name() + "）不是网络来源，无法更新";
        String code = lang;
        if (!s.appliesToLanguage(code)) return "§c来源 #" + index + " 不适用于当前语言 " + code;
        File langFile = networkLangFile(s.name(), code);
        if (langFile == null || !langFile.isFile()) {
            return downloadNetworkSource(index);
        }
        return downloadNetworkSource(index);
    }

    /**
     * 删除本地网络翻译缓存并从内存中卸载（停用该源 + 重载）。
     * 仅删除本地缓存文件，不影响 language.yml 中注册的源配置（下次 reload 会重新注册并恢复启用）。
     *
     * @return 操作结果消息
     */
    public static String removeNetworkSourceLocal(int index) {
        LanguageSource s = getLanguageSource(index);
        if (s == null) return "§c索引越界（当前共 " + languageSourceCount() + " 个来源）";
        String code = lang;
        File langFile = networkLangFile(s.name(), code);
        boolean deleted = false;
        if (langFile != null && langFile.isFile()) {
            deleted = langFile.delete();
        }
        // 停用该源使其从内存中卸载
        s.setEnabled(false);
        // 重载使变更生效
        load(lang);
        log.infoT("log.i18n.network-removed",
                "[i18n] 网络源 #{0}({1}) 本地翻译已删除并从内存卸载",
                index, s.name());
        return deleted
                ? "已删除来源 #" + index + "（" + s.name() + "）的本地 " + code + " 翻译并从内存卸载"
                : "来源 #" + index + "（" + s.name() + "）无本地缓存，已从内存卸载";
    }

    /**
     * 查看指定来源的详细信息（名称/介绍/语言/原始 source/是否网络源/是否启用/本地缓存状态）。
     *
     * @return 多行信息字符串；索引越界返回 null
     */
    public static String networkSourceInfo(int index) {
        LanguageSource s = getLanguageSource(index);
        if (s == null) return null;
        StringBuilder sb = new StringBuilder();
        sb.append("§a== ").append(s.name()).append(" (").append(s.description()).append(") ==");
        sb.append("\n§7索引: §f").append(index);
        sb.append("\n§7名称: §f").append(s.name());
        sb.append("\n§7介绍: §f").append(s.description());
        sb.append("\n§7语言: §f").append(s.language().isEmpty() ? "模板源（适配任意语言）" : s.language());
        sb.append("\n§7来源: §f").append(s.raw());
        sb.append("\n§7类型: §f").append(s.isUrl() ? "网络来源" : "本地来源");
        sb.append("\n§7状态: §f").append(s.enabled() ? "启用" : "停用");
        if (s.isUrl()) {
            File cache = networkLangFile(s.name(), lang);
            if (cache != null && cache.isFile()) {
                Map<String, String> m = s.loadLocal(cache);
                sb.append("\n§7本地缓存: §a存在§7 (").append(m == null ? 0 : m.size()).append(" 条)");
                sb.append("\n§7缓存路径: §f").append(cache.getAbsolutePath());
            } else {
                sb.append("\n§7本地缓存: §e无（加载时从网络获取）");
            }
            sb.append("\n§7解析URL: §f").append(s.resolvedSource(lang));
        }
        return sb.toString();
    }

    /**
     * 取出当前默认语言包的全部键值（供覆盖策略起步）。
     */
    private static Map<String, String> currentEntries() {
        if (current instanceof YamlLanguageBundle) {
            return ((YamlLanguageBundle) current).entries();
        }
        return new HashMap<>();
    }

    /**
     * 把 YAML 配置平铺进目标 Map（叶子键写入，忽略中间节点）。
     */
    private static void putAll(Map<String, String> target, ConfigSection cfg) {
        for (String key : cfg.getKeys(true)) {
            Object v = cfg.get(key);
            if (v != null && !(v instanceof ConfigSection)) {
                target.put(key, String.valueOf(v));
            }
        }
    }

    // ==================== 插件作用域注册 ====================

    /**
     * 插件注册 i18n：提供语言文件夹 + 插件实例。
     * 读取 {@code <folder>/<语言>.yml}，逻辑前缀自动写为 {@code plugins.<插件名称>.language.<语言>}，
     * 翻译时也自动带此前缀。
     *
     * @param plugin 插件实例（以 {@code plugin.getName()} 命名作用域）
     * @param folder 语言文件夹（内含 {@code zh_cn.yml} 等语言文件）
     * @return 该插件的作用域（可立即 {@code .t(key, fallback)} 使用）
     */
    public static I18nScope registerPlugin(Platform plugin, File folder) {
        if (plugin == null) return null;
        String name = plugin.getName();
        if (name == null || name.isEmpty()) return null;
        String prefix = "plugins." + name.toLowerCase() + ".language." + lang;

        I18nScope scope = new I18nScope(name, prefix);
        if (folder != null) {
            File file = new File(folder, lang + ".yml");
            if (!file.isFile() && folder.getName().equalsIgnoreCase("language") == false) {
                file = new File(new File(folder, "language"), lang + ".yml");
            }
            scope.load(file);
        }
        Map<String, I18nScope> map = new LinkedHashMap<>(pluginScopes);
        map.put(name, scope);
        pluginScopes = map;
        log.infoT("log.i18n.plugin-registered", "[i18n] 插件作用域已注册: {0}（前缀 {1}）", name, prefix);
        return scope;
    }

    // ==================== 无头作用域注册 ====================

    /**
     * 无头登记：仅需键值对，无需语言文件。单独存入无头列表（按注册顺序）。
     * 逻辑前缀：{@code headless.<标识>.language.<语言>}。
     *
     * @param id      标识（同一标识覆盖）
     * @param entries 键值对（去前缀的原始键）
     * @return 该无头作用域
     */
    public static I18nScope registerHeadless(String id, Map<String, String> entries) {
        if (id == null || id.isEmpty()) return null;
        String prefix = "headless." + id.toLowerCase() + ".language." + lang;
        I18nScope scope = new I18nScope(id, prefix);
        if (entries != null) scope.putAll(entries);

        List<I18nScope> list = new ArrayList<>(headlessScopes);
        // 同标识覆盖
        list.removeIf(s -> s.name().equalsIgnoreCase(id));
        list.add(scope);
        headlessScopes = list;
        log.infoT("log.i18n.headless-registered", "[i18n] 无头作用域已登记: {0}（{1} 条，前缀 {2}）", id, entries.size(), prefix);
        return scope;
    }

    // ==================== 作用域访问 ====================

    /**
     * 默认作用域（本插件，无前缀）。
     */
    public static ILanguageBundle bundle() {
        return current;
    }

    /**
     * 替换默认作用域实现（第三方可注入）。
     */
    public static void setBundle(ILanguageBundle b) {
        if (b != null) current = b;
    }

    /**
     * 取指定插件名的作用域；未注册返回 null。
     */
    public static I18nScope plugin(String pluginName) {
        if (pluginName == null) return null;
        return pluginScopes.get(pluginName);
    }

    /**
     * 取指定标识的无头作用域；未登记返回 null。
     */
    public static I18nScope headless(String id) {
        if (id == null) return null;
        for (I18nScope s : headlessScopes) {
            if (s.name().equalsIgnoreCase(id)) return s;
        }
        return null;
    }

    /**
     * 全部插件作用域（不可变视图）。
     */
    public static Map<String, I18nScope> pluginScopes() {
        return Collections.unmodifiableMap(pluginScopes);
    }

    /**
     * 无头作用域列表（不可变视图）。
     */
    public static List<I18nScope> headlessScopes() {
        return Collections.unmodifiableList(headlessScopes);
    }

    /**
     * 当前语言代码。
     */
    public static String languageCode() {
        return lang;
    }

    /**
     * 列出磁盘语言目录中可用的语言代码（*.yml 去扩展名，按名称排序）；目录未就绪时仅含内置中文。
     */
    public static List<String> availableLanguages() {
        if (languageDir == null || !languageDir.isDirectory()) {
            return Collections.singletonList("zh_cn");
        }
        File[] files = languageDir.listFiles((d, n) -> n.toLowerCase().endsWith(".yml"));
        if (files == null || files.length == 0) {
            return Collections.emptyList();
        }
        List<String> codes = new ArrayList<>(files.length);
        for (File f : files) {
            String n = f.getName();
            codes.add(n.substring(0, n.length() - 4));
        }
        Collections.sort(codes);
        return codes;
    }

    // ==================== 统一转译决策（底层函数入口） ====================

    /**
     * 统一转译决策，供日志 / 异常 / AjaxResult 等底层函数复用，业务调用点无需再手动拼 {@code I18n.t}。
     *
     * <p>规则：</p>
     * <ul>
     *   <li>{@code key} 非空 → 以 {@code key} 查语言表翻译 {@code fallback}，命中用表文本，未命中回退 {@code fallback}，最后填 {@code {i}} 占位符；</li>
     *   <li>{@code key} 为 {@code null} → 不查表，仅对 {@code fallback} 做 {@code {i}} 占位符替换（作为纯模板）。</li>
     * </ul>
     */
    public static String resolve(String key, String fallback, Object... args) {
        if (key == null) {
            return replace(fallback, args);
        }
        return t(key, fallback, args);
    }

    /**
     * 纯占位符 {@code {0} {1}...} 替换（不查表）；{@code null} 模板返回 {@code null}。
     */
    public static String replace(String template, Object... args) {
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

    // ==================== 取值 ====================

    /**
     * 取翻译文本（默认作用域 / 本插件无前缀键）。
     * 若 {@code key} 为显式前缀形式（如 {@code plugins.xxx.language.zh_cn.xx}），则转交对应作用域解析。
     */
    public static String t(String key, String defaultText) {
        return resolve(key, defaultText);
    }

    /**
     * 取翻译文本并替换占位符（默认作用域，或显式前缀键）。
     */
    public static String t(String key, String defaultText, Object... args) {
        I18nScope scoped = resolveScope(key);
        if (scoped != null) {
            return scoped.t(key, defaultText, args);
        }
        return current.format(key, defaultText, args);
    }

    /**
     * 从显式前缀键中解析目标是哪个作用域；非前缀键返回 null（走默认）。
     */
    private static I18nScope resolveScope(String key) {
        if (key == null) return null;
        if (key.startsWith("plugins.")) {
            int dot = key.indexOf('.', "plugins.".length());
            if (dot > 0) {
                String name = key.substring("plugins.".length(), dot);
                return pluginScopes.get(name);
            }
        } else if (key.startsWith("headless.")) {
            int dot = key.indexOf('.', "headless.".length());
            if (dot > 0) {
                String id = key.substring("headless.".length(), dot);
                return headless(id);
            }
        }
        return null;
    }

    /**
     * 单参数版 resolve：仅默认作用域查无前缀键（不处理占用占位符）。
     */
    private static String resolve(String key, String def) {
        if (key == null) return def;
        I18nScope scoped = resolveScope(key);
        if (scoped != null) {
            return scoped.t(key, def);
        }
        return current.get(key, def);
    }

    // ==================== 内部 ====================

    private static void ensureDefaultResource(File dir, String fileName) {
        File target = new File(dir, fileName);
        if (target.isFile()) return;
        if (!writeResource(dir, fileName)) {
            log.warnT("log.i18n.copy-resource-fail", "[i18n] 复制内置语言文件失败: {0}", fileName);
        }
    }

    private static boolean writeResource(File dir, String fileName) {
        String resPath = DEFAULT_RESOURCE_DIR + fileName;
        try (InputStream in = I18n.class.getClassLoader().getResourceAsStream(resPath)) {
            if (in == null) return false;
            File out = new File(dir, fileName);
            java.nio.file.Files.copy(in, out.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    // ==================== 语言源信息 ====================

    /**
     * 已注册语言源的快照信息（供 {@code /soyshttp lang sources} 展示）。
     */
    public static final class LanguageSourceInfo {
        private final int index;
        private final String name;
        private final String description;
        private final String language;
        private final String raw;
        private final boolean enabled;
        private final int count;

        LanguageSourceInfo(int index, String name, String description, String language, String raw, boolean enabled, int count) {
            this.index = index;
            this.name = name == null ? "" : name;
            this.description = description == null ? "" : description;
            this.language = language == null ? "" : language;
            this.raw = raw;
            this.enabled = enabled;
            this.count = count;
        }

        /**
         * 注册顺序索引（供 on/off 指定目标）。
         */
        public int index() {
            return index;
        }

        /**
         * 来源名称。
         */
        public String name() {
            return name;
        }

        /**
         * 来源描述。
         */
        public String description() {
            return description;
        }

        /**
         * 绑定的语言代码；空串 = 模板源（任意语言）。
         */
        public String language() {
            return language;
        }

        /**
         * 原始路径/URL 描述。
         */
        public String raw() {
            return raw;
        }

        /**
         * 是否启用。
         */
        public boolean enabled() {
            return enabled;
        }

        /**
         * 当前语言下该来源提供的翻译条数。
         */
        public int count() {
            return count;
        }
    }
}
