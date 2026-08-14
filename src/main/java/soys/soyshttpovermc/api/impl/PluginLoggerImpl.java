package soys.soyshttpovermc.api.impl;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import soys.soyshttpovermc.api.PluginLoggerApi;
import soys.soyshttpovermc.log.LogKit;

/**
 * 能力组 5：日志（包装 LogKit，自动识别调用插件并加 [插件名] 前缀）。
 * 由 {@link SoysHttpOverMcApiImpl} 组合并对外暴露；实现 {@link PluginLoggerApi}。
 */
public class PluginLoggerImpl implements PluginLoggerApi {

    private final Plugin hostPlugin;

    public PluginLoggerImpl(Plugin hostPlugin) {
        this.hostPlugin = hostPlugin;
    }

    @Override public void logInfo(String msg) { LogKit.info("[" + detectPluginName() + "] " + msg); }

    @Override public void logWarn(String msg) { LogKit.warn("[" + detectPluginName() + "] " + msg); }

    @Override public void logError(String msg) { LogKit.error("[" + detectPluginName() + "] " + msg); }

    @Override public void logDebug(String msg) { LogKit.debug("[" + detectPluginName() + "] " + msg); }

    @Override public void logTrace(String msg) { LogKit.trace("[" + detectPluginName() + "] " + msg); }

    @Override public boolean isDebugEnabled() { return LogKit.isDebugEnabled(); }

    /** 沿调用栈找到首个属于某个插件的类，返回其插件名；找不到则回落为主插件名。 */
    String detectPluginName() {
        StackTraceElement[] stack = new Throwable().getStackTrace();
        for (int i = 1; i < stack.length; i++) {
            String cn = stack[i].getClassName();
            if (cn.startsWith("soys.soyshttpovermc")) continue; // 跳过本插件内部类
            try {
                Class<?> c = Class.forName(cn, false, getClass().getClassLoader());
                ClassLoader cl = c.getClassLoader();
                for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
                    if (p.getClass().getClassLoader() == cl) return p.getName();
                }
            } catch (Throwable ignored) {
            }
        }
        return hostPlugin.getName();
    }
}
