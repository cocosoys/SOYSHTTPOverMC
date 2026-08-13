package soys.soyshttpovermc.config;

import org.bukkit.plugin.java.JavaPlugin;

import soys.soyshttpovermc.log.LogKit;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 插件配置文件与资源处理（从 {@code HttpOverMcPlugin} 抽离，职责单一）：
 * <ul>
 *   <li>首次运行时把 jar 内置的默认配置解压到数据目录（gateway/、issuers/ 等）；</li>
 *   <li>解析 {@code web.root}：留空时把 jar 内 {@code /web/*} 解压到数据目录的 web/ 并指向它
 *       （支持磁盘热替换编辑），非空时按相对数据目录/绝对路径处理；</li>
 * </ul>
 */
public final class ConfigManager {

    private ConfigManager() {
    }

    /** 首次运行生成 gateway/ 目录默认配置；返回 gateway 目录。 */
    public static File ensureGatewayFiles(JavaPlugin plugin) {
        File gwDir = new File(plugin.getDataFolder(), "gateway");
        saveDefaultFile(plugin, "gateway/config.yml");
        saveDefaultFile(plugin, "gateway/https.yml");
        saveDefaultFile(plugin, "gateway/policies/tls.yml");
        saveDefaultFile(plugin, "gateway/policies/ip-allowlist.yml");
        saveDefaultFile(plugin, "gateway/policies/auth.yml");
        saveDefaultFile(plugin, "gateway/policies/rate-limit.yml");
        saveDefaultFile(plugin, "gateway/issuers/session-token.yml");
        return gwDir;
    }

    /** 从 jar 资源复制默认配置到数据目录（已存在则不覆盖）。 */
    public static void saveDefaultFile(JavaPlugin plugin, String path) {
        if (plugin.getResource(path) == null) return;
        File target = new File(plugin.getDataFolder(), path);
        if (!target.isFile()) {
            plugin.saveResource(path, false);
        }
    }

    /**
     * 解析 web.root：
     * <ul>
     *   <li>留空（默认）：把 jar 内 {@code /web/*} 解压到数据目录的 web/（即配置文件夹中的 web 文件夹），
     *       并返回该目录；用户可直接在磁盘编辑、刷新即生效；</li>
     *   <li>非空：相对数据目录或绝对路径（原逻辑）。</li>
     * </ul>
     * <p>{@code pluginJar} 由插件自身（同包子类）通过 {@code getFile()} 取得后传入，
     * 因为 {@code JavaPlugin.getFile()} 为 protected，跨包不可直接调用。</p>
     */
    public static File resolveWebRoot(JavaPlugin plugin, File pluginJar, String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            File webDir = new File(plugin.getDataFolder(), "web");
            extractWebResources(plugin, pluginJar, webDir);
            return webDir.getAbsoluteFile();
        }
        File f = new File(raw);
        if (!f.isAbsolute()) {
            f = new File(plugin.getDataFolder(), raw);
        }
        return f.getAbsoluteFile();
    }

    /**
     * 从插件 jar 解压 {@code /web/*} 到磁盘目录：
     * 已存在的文件<b>不覆盖</b>（保留用户修改），仅补回缺失的内置默认资源。
     * 解压失败时仅告警，调用方回退到 jar 内置资源即可。
     */
    public static void extractWebResources(JavaPlugin plugin, File pluginJar, File webDir) {
        webDir.mkdirs();
        if (pluginJar == null || !pluginJar.isFile()) {
            LogKit.warn("[HTTP-Over-MC] 无法定位插件 jar，跳过 web 资源解压（回退 jar 内置）");
            return;
        }
        try (JarFile jf = new JarFile(pluginJar)) {
            Enumeration<JarEntry> entries = jf.entries();
            byte[] buf = new byte[8192];
            int extracted = 0;
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith("web/")) continue;
                if (entry.isDirectory()) continue;
                File out = new File(webDir, name.substring("web/".length()));
                if (out.exists()) continue; // 不覆盖用户修改
                out.getParentFile().mkdirs();
                try (InputStream in = jf.getInputStream(entry);
                     OutputStream os = new FileOutputStream(out)) {
                    int n;
                    while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                }
                extracted++;
            }
            if (extracted > 0) {
                LogKit.info("[HTTP-Over-MC] web 资源已解压 " + extracted + " 个文件到: " + webDir.getAbsolutePath());
            } else {
                LogKit.info("[HTTP-Over-MC] web 目录已就绪（资源来自磁盘或 jar 内置）: " + webDir.getAbsolutePath());
            }
        } catch (Exception e) {
            LogKit.warn("[HTTP-Over-MC] web 资源解压失败（回退 jar 内置）: " + e.getMessage());
        }
    }
}
