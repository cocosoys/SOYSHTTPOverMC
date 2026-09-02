package com.github.cocosoys.mc.soyshttpovermc.spi;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 平台无关的 YAML 配置节抽象（替代 Bukkit 的 YamlConfiguration / ConfigurationSection）。
 *
 * <p>由各平台模块实现并注入：Bukkit 端 {@code core} 包装其 YamlConfiguration，其他版本模块可覆写。
 * 取值方法与 Bukkit ConfigurationSection 语义对齐（点路径、null 容忍）。</p>
 */
public interface ConfigSection {

    String getString(String path);

    String getString(String path, String def);

    int getInt(String path);

    int getInt(String path, int def);

    long getLong(String path);

    long getLong(String path, long def);

    double getDouble(String path);

    double getDouble(String path, double def);

    boolean getBoolean(String path);

    boolean getBoolean(String path, boolean def);

    List<String> getStringList(String path);

    /**
     * 读取原始值；不存在或节点类型不匹配返回 null。
     */
    Object get(String path);

    void set(String path, Object value);

    /**
     * 取子节；path 不存在或非节返回 null。
     */
    ConfigSection getSection(String path);

    /**
     * path 处是否为节（等价 Bukkit isConfigurationSection）。
     */
    boolean isSection(String path);

    /**
     * 创建/获取子节。
     */
    ConfigSection createSection(String path);

    /**
     * 键集合（深层/浅层）。
     */
    Set<String> getKeys(boolean deep);

    /**
     * 全部键值（深层/浅层）。
     */
    Map<String, Object> getValues(boolean deep);
}