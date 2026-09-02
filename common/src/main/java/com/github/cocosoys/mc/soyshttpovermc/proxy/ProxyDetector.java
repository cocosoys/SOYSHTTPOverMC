package com.github.cocosoys.mc.soyshttpovermc.proxy;

import com.github.cocosoys.mc.soyshttpovermc.enums.ProxyPlatform;
import lombok.CustomLog;
import com.github.cocosoys.mc.soyshttpovermc.spi.ConfigSection;
import com.github.cocosoys.mc.soyshttpovermc.spi.Platform;
import com.github.cocosoys.mc.soyshttpovermc.spi.Platforms;

import java.io.File;

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
@CustomLog
public final class ProxyDetector {

    private ProxyDetector() {
    }

    /**
     * 探测当前拓扑。
     *
     * @param plugin 本插件实例（用于定位服务器根目录与读取配置）
     * @return 非 null 的 {@link ProxyPlatform}
     */
    public static ProxyPlatform detect(Platform platform) {
        File spigot = resolveFile(platform, "spigot.yml");
        boolean bungee = spigot != null && readBool(spigot, "settings.bungeecord");
        if (bungee) {
            log.infoT("log.proxy.detect-bungee", "spigot.yml settings.bungeecord=true → BUNGEECORD");
            return ProxyPlatform.BUNGEECORD;
        }
        File paper = resolveFile(platform, "paper.yml");
        if (paper != null && readBool(paper, "settings.velocity-support.enabled")) {
            return ProxyPlatform.VELOCITY;
        }
        if (spigot != null && (readBool(spigot, "settings.velocity-support.enabled")
                || readBool(spigot, "velocity.forwarding.enabled"))) {
            return ProxyPlatform.VELOCITY;
        }
        log.infoT("log.proxy.detect-standalone", "未检测到代理（spigot.yml bungeecord=false）→ STANDALONE");
        return ProxyPlatform.STANDALONE;
    }

    /**
     * 解析 <server>/<fileName> 的绝对路径；定位失败返回 null（仅打一次告警）。
     */
    private static File resolveFile(Platform platform, String fileName) {
        File root = serverRoot(platform);
        if (root == null) return null;
        return new File(root, fileName);
    }

    /**
     * 读取某 YAML 文件的布尔键（文件缺失返回 false，解析异常打印日志）。
     */
    private static boolean readBool(File file, String path) {
        if (!file.isFile()) return false;
        try {
            ConfigSection cfg = Platforms.getOrNull() == null ? null : Platforms.getOrNull().loadYaml(file);
            return cfg != null && cfg.getBoolean(path, false);
        } catch (Exception e) {
            log.warnT("log.proxy.read-fail", "ProxyDetector 读取 {0} 失败: {1}", file.getAbsolutePath(), e.getMessage());
            return false;
        }
    }

    /**
     * 插件数据目录上溯两级定位服务器根目录（<server>/plugins/<plugin> → <server>）。
     */
    private static File serverRoot(Platform platform) {
        File dir = platform.getDataFolder();
        if (dir == null) return null;
        dir = dir.getAbsoluteFile(); // 解析相对路径（getDataFolder 可能为相对路径）
        File parent = dir.getParentFile();
        if (parent == null) return null;
        return parent.getParentFile();
    }
}
