package soys.ihomepages.homepage;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

/**
 * 首页选择（当前首页名）的持久化助手：读写 {@code ihomepages/config.yml} 的
 * {@code homepage.current} 字段，服务器重启后自动恢复当前首页。
 */
public final class HomepageState {

    private final JavaPlugin host;

    public HomepageState(JavaPlugin host) {
        this.host = host;
    }

    private File configFile() {
        return new File(host.getDataFolder(), "ihomepages/config.yml");
    }

    /** 读取持久化的当前首页名；未设置/文件不存在返回 null。 */
    public String readCurrent() {
        File f = configFile();
        if (!f.isFile()) return null;
        return YamlConfiguration.loadConfiguration(f).getString("homepage.current", null);
    }

    /** 加载 ihomepage config.yml 全量配置（文件不存在返回空配置，不抛异常）。 */
    public YamlConfiguration loadConfig() {
        File f = configFile();
        return f.isFile() ? YamlConfiguration.loadConfiguration(f) : new YamlConfiguration();
    }

    /** 写入持久化的当前首页名（覆盖）。 */
    public void saveCurrent(String name) {
        try {
            File f = configFile();
            File parent = f.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) return;
            YamlConfiguration cfg = f.isFile() ? YamlConfiguration.loadConfiguration(f) : new YamlConfiguration();
            cfg.set("homepage.current", name);
            cfg.save(f);
        } catch (IOException ignored) {
            // 持久化失败不影响运行时切换
        }
    }
}