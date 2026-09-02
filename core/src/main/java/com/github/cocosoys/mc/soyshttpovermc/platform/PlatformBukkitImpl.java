package com.github.cocosoys.mc.soyshttpovermc.platform;

import com.github.cocosoys.mc.soyshttpovermc.spi.ConfigSection;
import com.github.cocosoys.mc.soyshttpovermc.spi.Platform;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * Bukkit 平台的 {@link Platform} 默认实现（core 兜底）。
 *
 * <p><b>版本模块部分覆盖</b>：其他版本模块可继承本类并覆写需要差异化的方法（如配置读取 / 调度），
 * 经 ServiceLoader（META-INF/services）注册后在运行期优先生效；未覆写的方法沿用本默认实现（core 兜底）。</p>
 *
 * <p><b>构造约定</b>：{@link #PlatformBukkitImpl()} 无参构造供 ServiceLoader 实例化版本模块实现时使用，
 * 通过静态 {@link #currentPlugin} 获取插件实例——core 在 onEnable 时写入。</p>
 */
public class PlatformBukkitImpl implements Platform {

    /**
     * 供版本模块 ServiceLoader 无参构造使用的当前插件实例（core onEnable 时写入）。
     */
    protected static volatile JavaPlugin currentPlugin;

    protected final JavaPlugin plugin;

    /**
     * 供版本模块继承的无参构造（ServiceLoader 实例化用）；未初始化时插件为 null。
     */
    protected PlatformBukkitImpl() {
        this(currentPlugin);
    }

    public PlatformBukkitImpl(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 记录当前插件实例（core onEnable 时调用，供无参构造使用）。
     */
    public static void setCurrentPlugin(JavaPlugin plugin) {
        currentPlugin = plugin;
    }

    @Override
    public String getName() {
        return plugin.getName();
    }

    @Override
    public File getDataFolder() {
        return plugin.getDataFolder();
    }

    @Override
    public Logger getLogger() {
        return plugin.getLogger();
    }

    @Override
    public void saveResource(String path, boolean replace) {
        plugin.saveResource(path, replace);
    }

    @Override
    public ConfigSection getConfig() {
        return new BukkitConfigSection(plugin.getConfig());
    }

    @Override
    public ConfigSection loadYaml(File file) {
        return new BukkitConfigSection(YamlConfiguration.loadConfiguration(file));
    }

    @Override
    public ConfigSection loadYaml(String content) {
        YamlConfiguration c = new YamlConfiguration();
        try {
            c.loadFromString(content == null ? "" : content);
        } catch (Exception e) {
            throw new IllegalArgumentException("YAML 解析失败", e);
        }
        return new BukkitConfigSection(c);
    }

    @Override
    public ConfigSection createYaml() {
        return new BukkitConfigSection(new YamlConfiguration());
    }

    @Override
    public void saveYaml(ConfigSection cfg, File file) throws IOException {
        if (!(cfg instanceof BukkitConfigSection)) {
            throw new IOException("非 Bukkit 配置实现，无法保存: " + (cfg == null ? "null" : cfg.getClass().getName()));
        }
        org.bukkit.configuration.ConfigurationSection d = ((BukkitConfigSection) cfg).delegate();
        if (d instanceof org.bukkit.configuration.file.FileConfiguration) {
            ((org.bukkit.configuration.file.FileConfiguration) d).save(file);
        } else {
            throw new IOException("底层配置非 FileConfiguration，无法保存: " + d.getClass().getName());
        }
    }

    @Override
    public ScheduledTask runTaskAsync(Runnable task) {
        BukkitTask t = plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
        return new BukkitScheduledTask(t);
    }

    @Override
    public ScheduledTask runTaskTimerAsync(Runnable task, long delayTicks, long periodTicks) {
        BukkitTask t = plugin.getServer().getScheduler()
                .runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks);
        return new BukkitScheduledTask(t);
    }

    @Override
    public void cancelTask(ScheduledTask task) {
        if (task instanceof BukkitScheduledTask) {
            ((BukkitScheduledTask) task).task.cancel();
        }
    }

    /**
     * BukkitTask 的调度句柄。
     */
    private static final class BukkitScheduledTask implements ScheduledTask {
        private final BukkitTask task;

        BukkitScheduledTask(BukkitTask task) {
            this.task = task;
        }
    }
}
