package soys.soyshttpovermc.i18n;
import lombok.CustomLog;

import org.bukkit.plugin.java.JavaPlugin;

import soys.soyshttpovermc.log.LogKit;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    /** jar 内默认语言文件根目录。 */
    private static final String DEFAULT_RESOURCE_DIR = "language/";

    /** 当前语言代码（当前仅中文 zh_cn）。 */
    private static volatile String lang = "zh_cn";
    /** 默认（无前缀）语言包：本插件自身消息。 */
    private static volatile ILanguageBundle current = new YamlLanguageBundle("zh_cn");
    /** 插件作用域：插件名 → I18nScope。 */
    private static volatile Map<String, I18nScope> pluginScopes = new LinkedHashMap<>();
    /** 无头作用域：单独列表（按注册顺序）。 */
    private static volatile List<I18nScope> headlessScopes = new ArrayList<>();
    /** 磁盘语言目录（{@code <dataFolder>/language/}）。 */
    private static volatile File languageDir;

    private I18n() {
    }

    // ==================== 初始化 ====================

    /** 初始化默认语言环境：补齐 language 目录 + 内置中文包，加载进内存。 */
    public static void init(File dataFolder) {
        if (dataFolder == null) return;
        File dir = new File(dataFolder, "language");
        if (!dir.exists() && !dir.mkdirs()) {
            log.warn(I18n.t("log.i18n.dir-create-fail", "[i18n] 创建语言目录失败: {0}", dir.getAbsolutePath()));
            languageDir = dir;
            return;
        }
        languageDir = dir;
        ensureDefaultResource(dir, "zh_cn.yml");
        String code = System.getProperty("soys.i18n.language", "zh_cn");
        load(code);
    }

    /** 加载默认作用域的语言包（磁盘 {@code language/<code>.yml}）。 */
    public static boolean load(String code) {
        if (code == null || code.isEmpty() || languageDir == null) return false;
        File file = new File(languageDir, code.toLowerCase() + ".yml");
        if (!file.isFile()) {
            log.warn(I18n.t("log.i18n.file-not-found", "[i18n] 默认语言文件不存在: {0}，保持当前语言", file.getAbsolutePath()));
            return false;
        }
        YamlLanguageBundle bundle = new YamlLanguageBundle(code);
        if (bundle.load(file)) {
            lang = code.toLowerCase();
            current = bundle;
            log.info(I18n.t("log.i18n.bundle-loaded", "[i18n] 默认语言包已加载: {0} ({1} 条)", lang, bundle.messagesSize()));
            return true;
        }
        return false;
    }

    // ==================== 插件作用域注册 ====================

    /**
     * 插件注册 i18n：提供语言文件夹 + 插件实例。
     * 读取 {@code <folder>/<语言>.yml}，逻辑前缀自动写为 {@code plugins.<插件名称>.language.<语言>}，
     * 翻译时也自动带此前缀。
     *
     * @param plugin   插件实例（以 {@code plugin.getName()} 命名作用域）
     * @param folder   语言文件夹（内含 {@code zh_cn.yml} 等语言文件）
     * @return 该插件的作用域（可立即 {@code .t(key, fallback)} 使用）
     */
    public static I18nScope registerPlugin(JavaPlugin plugin, File folder) {
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
        log.info(I18n.t("log.i18n.plugin-registered", "[i18n] 插件作用域已注册: {0}（前缀 {1}）", name, prefix));
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
        log.info(I18n.t("log.i18n.headless-registered", "[i18n] 无头作用域已登记: {0}（{1} 条，前缀 {2}）", id, entries.size(), prefix));
        return scope;
    }

    // ==================== 作用域访问 ====================

    /** 默认作用域（本插件，无前缀）。 */
    public static ILanguageBundle bundle() {
        return current;
    }

    /** 替换默认作用域实现（第三方可注入）。 */
    public static void setBundle(ILanguageBundle b) {
        if (b != null) current = b;
    }

    /** 取指定插件名的作用域；未注册返回 null。 */
    public static I18nScope plugin(String pluginName) {
        if (pluginName == null) return null;
        return pluginScopes.get(pluginName);
    }

    /** 取指定标识的无头作用域；未登记返回 null。 */
    public static I18nScope headless(String id) {
        if (id == null) return null;
        for (I18nScope s : headlessScopes) {
            if (s.name().equalsIgnoreCase(id)) return s;
        }
        return null;
    }

    /** 全部插件作用域（不可变视图）。 */
    public static Map<String, I18nScope> pluginScopes() {
        return Collections.unmodifiableMap(pluginScopes);
    }

    /** 无头作用域列表（不可变视图）。 */
    public static List<I18nScope> headlessScopes() {
        return Collections.unmodifiableList(headlessScopes);
    }

    /** 当前语言代码。 */
    public static String languageCode() {
        return lang;
    }

    // ==================== 取值 ====================

    /**
     * 取翻译文本（默认作用域 / 本插件无前缀键）。
     * 若 {@code key} 为显式前缀形式（如 {@code plugins.xxx.language.zh_cn.xx}），则转交对应作用域解析。
     */
    public static String t(String key, String defaultText) {
        return resolve(key, defaultText);
    }

    /** 取翻译文本并替换占位符（默认作用域，或显式前缀键）。 */
    public static String t(String key, String defaultText, Object... args) {
        I18nScope scoped = resolveScope(key);
        if (scoped != null) {
            return scoped.t(key, defaultText, args);
        }
        return current.format(key, defaultText, args);
    }

    /** 从显式前缀键中解析目标是哪个作用域；非前缀键返回 null（走默认）。 */
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

    /** 单参数版 resolve：仅默认作用域查无前缀键（不处理占用占位符）。 */
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
            log.warn(I18n.t("log.i18n.copy-resource-fail", "[i18n] 复制内置语言文件失败: {0}", fileName));
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
}
