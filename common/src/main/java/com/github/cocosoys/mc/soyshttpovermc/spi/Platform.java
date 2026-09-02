package com.github.cocosoys.mc.soyshttpovermc.spi;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * 平台抽象（替代对 Bukkit JavaPlugin 的直接依赖），覆盖插件所需的 5 类宿主能力：
 * 配置 / 数据目录 / 日志 / 异步调度 / 资源。
 *
 * <p>{@code core} 提供默认实现 {@code PlatformBukkitImpl} 作为兜底；其他版本模块可继承并覆写个别方法，
 * 通过 ServiceLoader（META-INF/services）注册以在运行时优先生效。</p>
 */
public interface Platform {

    /**
     * 插件名（等价 JavaPlugin#getName）。
     */
    String getName();

    /**
     * 插件数据目录（等价 JavaPlugin#getDataFolder）。
     */
    File getDataFolder();

    /**
     * 日志记录器（等价 JavaPlugin#getLogger）。
     */
    Logger getLogger();

    /**
     * 从 jar 释放资源到数据目录（等价 JavaPlugin#saveResource）。
     */
    void saveResource(String path, boolean replace);

    /**
     * 主配置文件根节（等价 JavaPlugin#getConfig 的根 ConfigurationSection）。
     */
    ConfigSection getConfig();

    /**
     * 从文件加载 YAML（等价 YamlConfiguration#loadConfiguration(File)）。
     */
    ConfigSection loadYaml(File file);

    /**
     * 从字符串加载 YAML（等价 YamlConfiguration#loadConfiguration(Reader)）。
     */
    ConfigSection loadYaml(String content);

    /**
     * 新建空 YAML 配置（等价 new YamlConfiguration()）。
     */
    ConfigSection createYaml();

    /**
     * 将配置保存到文件（等价 YamlConfiguration#save(File)）。
     */
    void saveYaml(ConfigSection cfg, File file) throws IOException;

    /**
     * 异步执行一次性任务（等价 runTaskAsynchronously）。
     */
    ScheduledTask runTaskAsync(Runnable task);

    /**
     * 异步定时循环任务（等价 runTaskTimerAsynchronously）。
     *
     * @param delayTicks  首执行延迟（tick）
     * @param periodTicks 周期（tick）
     */
    ScheduledTask runTaskTimerAsync(Runnable task, long delayTicks, long periodTicks);

    /**
     * 取消已调度的任务。
     */
    void cancelTask(ScheduledTask task);

    /**
     * 调度句柄（不透明；供 cancelTask 使用）。
     */
    interface ScheduledTask {
    }
}