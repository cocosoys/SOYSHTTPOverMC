package soys.soyshttpovermc.config;

import org.bukkit.plugin.java.JavaPlugin;

import soys.soyshttpovermc.log.LogKit;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.Properties;
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
     * 解析 web.root 并保证目标目录包含缺省 web 资源：
     * <ul>
     *   <li>留空（默认）：目标 = 数据目录的 web/（即配置文件夹中的 web 文件夹）；</li>
     *   <li>非空：相对数据目录或绝对路径（原逻辑）；</li>
     * </ul>
     * <b>两种情况都会调用 {@link #extractWebResources} 自动补缺省资源</b>：
     * 目标目录不存在或为空时，把 jar 内置 {@code /web/*} 解压进去（已存在文件不覆盖，保留用户修改）；
     * 非空目录中的缺失文件也会补回。修复"用户填写了 web.root 且目录为空/不存在时不会自动解压"的问题。
     *
     * <p>{@code pluginJar} 由插件自身（同包子类）通过 {@code getFile()} 取得后传入，
     * 因为 {@code JavaPlugin.getFile()} 为 protected，跨包不可直接调用。</p>
     */
    public static File resolveWebRoot(JavaPlugin plugin, File pluginJar, String raw) {
        File dir;
        if (raw == null || raw.trim().isEmpty()) {
            dir = new File(plugin.getDataFolder(), "web");
        } else {
            File f = new File(raw);
            dir = f.isAbsolute() ? f : new File(plugin.getDataFolder(), raw);
        }
        // 目标目录不存在/为空/缺文件时自动补 jar 内置缺省 web 资源（已存在不覆盖）
        extractWebResources(plugin, pluginJar, dir);
        return dir.getAbsoluteFile();
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

    /**
     * 解析本服对外地址的 host 部分：
     * 若 config.yml 的 {@code mc.host} 未填写（空），则从服务器根目录的
     * {@code server.properties} 读取 {@code server-ip}；若其仍为空，再回退到
     * Bukkit 运行期 {@code getServer().getIp()}，最终回退 {@code 127.0.0.1}。
     *
     * @param plugin   本插件实例（用于定位服务器根目录与运行期端口）
     * @param configured 已从 config.yml 读到的 host（可能为空）
     * @return 非空 host
     */
    public static String resolveMcHost(JavaPlugin plugin, String configured) {
        if (configured != null && !configured.trim().isEmpty()) {
            return configured.trim();
        }
        String ip = readServerProperty(plugin, "server-ip");
        if (ip != null && !ip.trim().isEmpty()) {
            return ip.trim();
        }
        String apiIp = plugin.getServer().getIp();
        if (apiIp != null && !apiIp.trim().isEmpty()) {
            return apiIp.trim();
        }
        return "127.0.0.1";
    }

    /**
     * 解析本服对外地址的 port 部分：
     * 若 config.yml 的 {@code mc.port} 未填写（<=0），则从服务器根目录的
     * {@code server.properties} 读取 {@code server-port}；解析失败再回退到
     * Bukkit 运行期 {@code getServer().getPort()}（即 Spigot 实际监听端口）。
     *
     * @param plugin   本插件实例
     * @param configured 已从 config.yml 读到的 port（可能为 0/负数表示未填）
     * @return 正整端口
     */
    public static int resolveMcPort(JavaPlugin plugin, int configured) {
        if (configured > 0) {
            return configured;
        }
        String p = readServerProperty(plugin, "server-port");
        if (p != null && !p.trim().isEmpty()) {
            try {
                int v = Integer.parseInt(p.trim());
                if (v > 0) return v;
            } catch (NumberFormatException ignored) {
                // 回退运行期端口
            }
        }
        return plugin.getServer().getPort();
    }

    /** 从服务器根目录的 server.properties 读取单个键（找不到返回 null）。 */
    private static String readServerProperty(JavaPlugin plugin, String key) {
        // 插件数据目录为 <server>/plugins/<pluginName>，上溯两级即 <server> 根目录
        File root = plugin.getDataFolder().getParentFile();
        if (root != null) root = root.getParentFile();
        if (root == null) return null;
        File props = new File(root, "server.properties");
        if (!props.isFile()) return null;
        try (InputStream in = new FileInputStream(props)) {
            Properties properties = new Properties();
            properties.load(in);
            return properties.getProperty(key);
        } catch (Exception e) {
            LogKit.warn("[HTTP-Over-MC] 读取 server.properties 失败（键=" + key + "）: " + e.getMessage());
            return null;
        }
    }
}
