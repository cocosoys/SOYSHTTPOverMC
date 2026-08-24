package soys.soyshttpovermc.proxy;
import soys.soyshttpovermc.enums.ProxyPlatform;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.logging.Level;

/**
 * 群组服探测骨架（运行期）。
 *
 * <p>通过读取后端配置文件（spigot.yml / paper.yml / velocity 转发配置）判断当前后端
 * 是否位于 BungeeCord / Waterfall / Velocity 之后。<b>纯反射 + Bukkit 配置 API，
 * 不引用任何代理类</b>，因此即使代理 API 不在 classpath（后端独立运行）也完全安全。</p>
 *
 * <p>注意：Bukkit 的 {@code getDataFolder()} 在某些启动方式下返回<b>相对路径</b>，
 * 故 {@link #serverRoot} 必须先 {@code getAbsoluteFile()} 再上溯两级定位服务器根目录。</p>
 */
public final class ProxyDetector {

    private ProxyDetector() {
    }

    /**
     * 探测当前拓扑。
     *
     * @param plugin 本插件实例（用于定位服务器根目录与读取配置）
     * @return 非 null 的 {@link ProxyPlatform}
     */
    public static ProxyPlatform detect(JavaPlugin plugin) {
        File spigot = resolveFile(plugin, "spigot.yml");
        boolean bungee = spigot != null && readBool(plugin, spigot, "settings.bungeecord");
        if (bungee) {
            plugin.getLogger().info("[HTTP-Over-MC] ProxyDetector: spigot.yml settings.bungeecord=true → BUNGEECORD");
            return ProxyPlatform.BUNGEECORD;
        }
        File paper = resolveFile(plugin, "paper.yml");
        if (paper != null && readBool(plugin, paper, "settings.velocity-support.enabled")) {
            return ProxyPlatform.VELOCITY;
        }
        if (spigot != null && (readBool(plugin, spigot, "settings.velocity-support.enabled")
                || readBool(plugin, spigot, "velocity.forwarding.enabled"))) {
            return ProxyPlatform.VELOCITY;
        }
        plugin.getLogger().info("[HTTP-Over-MC] ProxyDetector: 未检测到代理（spigot.yml bungeecord=false）→ STANDALONE");
        return ProxyPlatform.STANDALONE;
    }

    /** 解析 <server>/<fileName> 的绝对路径；定位失败返回 null（仅打一次告警）。 */
    private static File resolveFile(JavaPlugin plugin, String fileName) {
        File root = serverRoot(plugin);
        if (root == null) return null;
        return new File(root, fileName);
    }

    /** 读取某 YAML 文件的布尔键（文件缺失返回 false，解析异常打印日志）。 */
    private static boolean readBool(JavaPlugin plugin, File file, String path) {
        if (!file.isFile()) return false;
        try {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            return cfg.getBoolean(path, false);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[HTTP-Over-MC] ProxyDetector 读取 " + file.getAbsolutePath() + " 失败", e);
            return false;
        }
    }

    /** 插件数据目录上溯两级定位服务器根目录（<server>/plugins/<plugin> → <server>）。 */
    private static File serverRoot(JavaPlugin plugin) {
        File dir = plugin.getDataFolder();
        if (dir == null) return null;
        dir = dir.getAbsoluteFile(); // 解析相对路径（getDataFolder 可能为相对路径）
        File parent = dir.getParentFile();
        if (parent == null) return null;
        return parent.getParentFile();
    }
}
