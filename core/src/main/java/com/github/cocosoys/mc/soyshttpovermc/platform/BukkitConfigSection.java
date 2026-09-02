package com.github.cocosoys.mc.soyshttpovermc.platform;

import com.github.cocosoys.mc.soyshttpovermc.spi.ConfigSection;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bukkit {@link ConfigurationSection} 的 {@link ConfigSection} 适配包装。
 * 所有方法直接委托给 Bukkit 实现，保持取值语义（点路径 / null 容忍）一致。
 */
public class BukkitConfigSection implements ConfigSection {

    private final ConfigurationSection delegate;

    public BukkitConfigSection(ConfigurationSection delegate) {
        this.delegate = delegate;
    }

    /**
     * 暴露底层 Bukkit 节点（saveYaml 等平台实现需要）。
     */
    public ConfigurationSection delegate() {
        return delegate;
    }

    @Override
    public String getString(String path) {
        return delegate.getString(path);
    }

    @Override
    public String getString(String path, String def) {
        return delegate.getString(path, def);
    }

    @Override
    public int getInt(String path) {
        return delegate.getInt(path);
    }

    @Override
    public int getInt(String path, int def) {
        return delegate.getInt(path, def);
    }

    @Override
    public long getLong(String path) {
        return delegate.getLong(path);
    }

    @Override
    public long getLong(String path, long def) {
        return delegate.getLong(path, def);
    }

    @Override
    public double getDouble(String path) {
        return delegate.getDouble(path);
    }

    @Override
    public double getDouble(String path, double def) {
        return delegate.getDouble(path, def);
    }

    @Override
    public boolean getBoolean(String path) {
        return delegate.getBoolean(path);
    }

    @Override
    public boolean getBoolean(String path, boolean def) {
        return delegate.getBoolean(path, def);
    }

    @Override
    public List<String> getStringList(String path) {
        return delegate.getStringList(path);
    }

    @Override
    public Object get(String path) {
        return delegate.get(path);
    }

    @Override
    public void set(String path, Object value) {
        delegate.set(path, value);
    }

    @Override
    public ConfigSection getSection(String path) {
        ConfigurationSection s = delegate.getConfigurationSection(path);
        return s == null ? null : new BukkitConfigSection(s);
    }

    @Override
    public boolean isSection(String path) {
        return delegate.isConfigurationSection(path);
    }

    @Override
    public ConfigSection createSection(String path) {
        return new BukkitConfigSection(delegate.createSection(path));
    }

    @Override
    public Set<String> getKeys(boolean deep) {
        return delegate.getKeys(deep);
    }

    @Override
    public Map<String, Object> getValues(boolean deep) {
        return delegate.getValues(deep);
    }
}
