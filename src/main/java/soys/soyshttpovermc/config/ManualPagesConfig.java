package soys.soyshttpovermc.config;
import lombok.CustomLog;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import soys.soyshttpovermc.web.WebRegistry;
import soys.soyshttpovermc.web.MimeTypes;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 手动网页资源登记（pages.yml）：把用户在 pages.yml 中声明的网页/跳转
 * 经 {@link WebRegistry} <b>强制登记</b> 落入注册通道，从而：
 * <ul>
 *   <li>{@code /soyshttp pages} / {@code /shttp pages} 能列出这些网页（含 description 说明）；</li>
 *   <li>与核心/第三方已注册的同路径页面冲突时<b>强制覆盖</b>。</li>
 * </ul>
 *
 * <p>配置结构分为两种自动注册写法：</p>
 * <pre>
 * pages:
 *   page:            # 显式页面：resource + 可选 nicknames / description
 *     "/":
 *       nicknames: ["主页"]
 *       description: "首页说明"
 *       resource: "web/index.html"
 *   auto:            # 平价自动：键=URL，值=来源（单文件 / 目录 / jar 资源 / 反引号网络跳转）
 *     "/":        "web/"
 *     "/link/x":  "`https://www.mcmod.cn/`"
 * </pre>
 * <ul>
 *   <li><b>page 段</b>：多用于需附带 nickname/description 的单个页面；
 *       {@code resource} 只支持单个文件/资源来源，<b>不支持目录</b>（目录请放 auto 段）；</li>
 *   <li><b>auto 段</b>：平铺键值；
 *       值以反引号包裹（如 {@code "`https://x/`"}）时登记为<b>302 网络跳转</b>；指向文件夹时递归注册该文件夹下
 *       <b>所有 .html</b>（配合键 {@code "/"} 即“写 / 注册文件夹下所有 html”）；否则按单个文件/资源登记。</li>
 * </ul>
 * <p>每次服务器启动与 {@code /soyshttp reload} 重新装配；修改即覆盖（幂等）。</p>
 */
@CustomLog
public final class ManualPagesConfig {

    private ManualPagesConfig() {
    }

    /** 从头登记 pages.yml（文件缺失时先落内置默认）。返回登记成功的网页/跳转数（含目录内逐文件）。 */
    public static int register(JavaPlugin plugin, WebRegistry reg) {
        if (reg == null) return 0;
        YamlConfiguration cfg = loadConfig(plugin);
        if (cfg == null) return 0;
        ConfigurationSection pages = cfg.getConfigurationSection("pages");
        if (pages == null) return 0;

        int total = 0;

        // 加载优先级：先 auto（平铺自动注册），后 page（显式页面），使 page 段可覆盖 auto 段已登记的同路径页面。
        ConfigurationSection autoSec = pages.getConfigurationSection("auto");
        if (autoSec != null) {
            total += applyAutoMap(plugin, reg, autoSec);
        } else if (!pages.contains("page")) {
            // 旧版兼容：pages 下平铺键值（无 page/auto 段）仍按 auto 处理
            total += applyAutoMap(plugin, reg, pages);
        }

        ConfigurationSection pageSec = pages.getConfigurationSection("page");
        if (pageSec != null) {
            for (String key : pageSec.getKeys(false)) {
                String url = normalizeUrl(key);
                try {
                    total += applyPage(plugin, reg, url, readPageObj(pageSec, key));
                } catch (Throwable t) {
                    log.warnT("log.pages.register-fail", "pages.yml 登记失败 {0}: {1}", key, t.getMessage());
                }
            }
        }

        if (total > 0) {
            log.infoT("log.pages.registered", "pages.yml 已登记 {0} 个网页", total);
        }
        return total;
    }

    /**
     * 确保 pages.yml 存在（缺失时落内置默认）并读取。
     * 统一入口：外部模块（web.* 配置、ihomepage 写 web.home）与 {@link #register} 共用同一配置源。
     *
     * @return pages.yml 的配置对象；文件落盘失败返回 null
     */
    public static YamlConfiguration loadConfig(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "pages.yml");
        if (!file.isFile()) {
            if (plugin.getResource("pages.yml") != null) {
                plugin.saveResource("pages.yml", false);
            }
            if (!file.isFile()) return null;
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    /** 解析 page 段单条为 PageItem（resource / nicknames / description）。 */
    private static PageItem readPageObj(ConfigurationSection pageSec, String key) {
        PageItem it = new PageItem();
        Object raw = pageSec.get(key);
        if (raw instanceof ConfigurationSection) {
            ConfigurationSection obj = (ConfigurationSection) raw;
            it.resource = obj.getString("resource");
            it.description = obj.getString("description");
            it.nicknames = obj.getStringList("nicknames");
        } else if (raw != null) {
            it.resource = String.valueOf(raw);
        }
        return it;
    }

    /** page 段：resource 单文件/资源 + nicknames/description；resource 不支持目录。 */
    private static int applyPage(JavaPlugin plugin, WebRegistry reg, String url, PageItem it) {
        if (it == null || it.resource == null || it.resource.trim().isEmpty()) {
            log.warnT("log.pages.source-empty", "pages.yml 条目缺失内容来源: {0}", url);
            return 0;
        }
        String source = it.resource.trim();
        File disk = resolveAsFile(plugin, source);
        if (disk.isDirectory()) {
            log.warnT("log.pages.page-resource-dir",
                    "pages.page 的 resource 不支持目录（如需注册整个 .html 文件夹请改用 pages.auto 段）: {0} -> {1}", url, source);
            return 0;
        }
        byte[] bytes = readSource(plugin, disk, source);
        if (bytes == null || bytes.length == 0) {
            log.warnT("log.pages.source-unreadable", "pages.yml 来源不可读，已跳过: {0}", source);
            return 0;
        }
        String ct = MimeTypes.isHtmlPath(source) ? MimeTypes.forExt("html") : null;
        reg.registerProxyPage(plugin, url, bytes, ct, true, it.description, it.nicknames);
        return 1;
    }

    /** auto 段：遍历平铺键值，逐条 applyAuto。 */
    private static int applyAutoMap(JavaPlugin plugin, WebRegistry reg, ConfigurationSection sec) {
        int total = 0;
        for (String key : sec.getKeys(false)) {
            String url = normalizeUrl(key);
            String source = sec.getString(key);
            if (source == null || source.trim().isEmpty()) {
                log.warnT("log.pages.source-empty", "pages.yml 条目缺失内容来源: {0}", key);
                continue;
            }
            try {
                total += applyAuto(plugin, reg, url, source.trim());
            } catch (Throwable t) {
                log.warnT("log.pages.register-fail", "pages.yml 登记失败 {0}: {1}", key, t.getMessage());
            }
        }
        return total;
    }

    /** auto 段单条：反引号包裹 → 302 网络跳转；目录 → 递归 .html；否则单文件/资源。 */
    private static int applyAuto(JavaPlugin plugin, WebRegistry reg, String url, String source) {
        if (isBacktickUrl(source)) {
            reg.registerProxyRedirect(plugin, url, source.substring(1, source.length() - 1).trim(), 302);
            log.infoT("log.pages.register-redirect", "pages.yml 登记跳转: GET {0} → {1}", url, source.substring(1, source.length() - 1).trim());
            return 1;
        }
        File disk = resolveAsFile(plugin, source);
        if (disk.isDirectory()) {
            return registerDir(plugin, reg, url, disk);
        }
        byte[] bytes = readSource(plugin, disk, source);
        if (bytes == null || bytes.length == 0) {
            log.warnT("log.pages.source-unreadable", "pages.yml 来源不可读，已跳过: {0}", source);
            return 0;
        }
        String ct = MimeTypes.isHtmlPath(source) ? MimeTypes.forExt("html") : null;
        reg.registerProxyPage(plugin, url, bytes, ct, true);
        return 1;
    }

    /** 值是否形如 "`...`"（反引号包裹 = 网络跳转目标）。 */
    private static boolean isBacktickUrl(String s) {
        return s != null && s.length() >= 2 && s.charAt(0) == '`' && s.charAt(s.length() - 1) == '`';
    }

    /** 目录模式：递归收集其下全部 .html，逐文件强制登记（URL = url 前缀 + 目录内相对路径）。 */
    private static int registerDir(JavaPlugin plugin, WebRegistry reg, String url, File dir) {
        List<File> htmls = new ArrayList<>();
        collectHtml(dir, htmls);
        for (File f : htmls) {
            String rel = dir.toPath().relativize(f.toPath()).toString().replace('\\', '/');
            String pageUrl = joinUrl(url, rel);
            byte[] bytes = readFile(f);
            if (bytes == null || bytes.length == 0) continue;
            reg.registerProxyPage(plugin, pageUrl, bytes, MimeTypes.forExt("html"), true);
        }
        return htmls.size();
    }

    private static void collectHtml(File dir, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File c : children) {
            if (c.isDirectory()) {
                collectHtml(c, out);
            } else if (c.isFile() && MimeTypes.isHtmlPath(c.getName())) {
                out.add(c);
            }
        }
    }

    /** 把来源解析为磁盘文件（相对插件 dataFolder 或绝对路径）；目录不存在时返回 {code new File(...)} 便于判空。 */
    private static File resolveAsFile(JavaPlugin plugin, String source) {
        File abs = new File(source);
        return abs.isAbsolute() ? abs : new File(plugin.getDataFolder(), source);
    }

    /** 读取来源字节：优先磁盘文件，否则按 jar 内置资源读取；都读不到返回 null。 */
    private static byte[] readSource(JavaPlugin plugin, File disk, String source) {
        if (disk.isFile()) {
            byte[] b = readFile(disk);
            if (b != null) return b;
        }
        String res = source.startsWith("/") ? source.substring(1) : source;
        try (InputStream in = plugin.getResource(res)) {
            if (in == null) return null;
            return toBytes(in);
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] readFile(File f) {
        try (InputStream in = new FileInputStream(f)) {
            return toBytes(in);
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] toBytes(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }

    private static String normalizeUrl(String key) {
        String k = key.trim();
        String p = k.startsWith("/") ? k : "/" + k;
        return p.replace('\\', '/');
    }

    /** 拼接 URL：root=http://（?）"/"，rel=目录内相对路径（可能含子目录）。 */
    private static String joinUrl(String root, String rel) {
        if ("/".equals(root)) return "/" + rel;
        String r = root.endsWith("/") ? root.substring(0, root.length() - 1) : root;
        String rel2 = rel.startsWith("/") ? rel : "/" + rel;
        return r + rel2;
    }

    /** page 段单条内容。 */
    private static final class PageItem {
        String resource;
        String description;
        List<String> nicknames;
    }
}