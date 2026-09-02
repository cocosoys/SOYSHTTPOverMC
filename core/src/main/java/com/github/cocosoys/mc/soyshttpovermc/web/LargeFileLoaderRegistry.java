package com.github.cocosoys.mc.soyshttpovermc.web;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 大文件加载器注册中心：
 * <ul>
 *   <li>内置默认加载器 {@link DefaultLargeFileLoader}（流式分块，默认接管所有超过阈值的大文件）；</li>
 *   <li>开发者可 {@link #register} 注册自定义加载器（实现 {@link LargeFileLoader}）；</li>
 *   <li>按路径前缀 {@link #setPathLoader} 强制指定某资源的加载方式（开发者强行切换）；</li>
 *   <li>{@link #setDefault} 切换全局默认加载器（"默认为所有大文件状态"，可整体替换）。</li>
 * </ul>
 * 解析优先级：按路径前缀指定 > 全局默认。
 */
public class LargeFileLoaderRegistry {

    private volatile LargeFileLoader defaultLoader;
    private final Map<String, LargeFileLoader> loaders = new ConcurrentHashMap<>();
    /**
     * pathPrefix -> loader 名（最长前缀优先匹配）
     */
    private final Map<String, String> pathOverrides = new ConcurrentHashMap<>();

    public LargeFileLoaderRegistry(long thresholdBytes) {
        this.defaultLoader = new DefaultLargeFileLoader(thresholdBytes);
        this.loaders.put(DefaultLargeFileLoader.NAME, defaultLoader);
    }

    /**
     * 注册一个自定义加载器（同名覆盖）。
     */
    public void register(LargeFileLoader loader) {
        if (loader == null || loader.name() == null || loader.name().isEmpty()) return;
        loaders.put(loader.name(), loader);
    }

    /**
     * 按名称取加载器（不存在返回 null）。
     */
    public LargeFileLoader get(String name) {
        return name == null ? null : loaders.get(name);
    }

    /**
     * 切换全局默认加载器（按名称；未知名称忽略并告警）。
     */
    public boolean setDefault(String loaderName) {
        LargeFileLoader l = loaderName == null ? null : loaders.get(loaderName);
        if (l == null) return false;
        defaultLoader = l;
        return true;
    }

    /**
     * 为某路径前缀强制指定加载方式（开发者强行切换；最长前缀优先）。
     */
    public void setPathLoader(String pathPrefix, String loaderName) {
        if (pathPrefix == null || pathPrefix.isEmpty() || loaderName == null) return;
        if (loaders.containsKey(loaderName)) {
            pathOverrides.put(pathPrefix, loaderName);
        }
    }

    /**
     * 解析某资源应使用的加载器（按路径前缀指定 > 全局默认）；无加载器命中返回 null。
     */
    public LargeFileLoader resolve(String path, File file, long sizeBytes, String contentType) {
        LargeFileLoader byPath = resolvePathOverride(path, file, sizeBytes, contentType);
        if (byPath != null) return byPath;
        LargeFileLoader def = defaultLoader;
        if (def != null && def.supports(path, file, sizeBytes, contentType)) return def;
        return null;
    }

    private LargeFileLoader resolvePathOverride(String path, File file, long sizeBytes, String contentType) {
        if (path == null || pathOverrides.isEmpty()) return null;
        String best = null;
        int bestLen = -1;
        for (Map.Entry<String, String> e : pathOverrides.entrySet()) {
            String prefix = e.getKey();
            if (prefix != null && path.startsWith(prefix) && prefix.length() > bestLen) {
                best = e.getValue();
                bestLen = prefix.length();
            }
        }
        if (best == null) return null;
        LargeFileLoader l = loaders.get(best);
        if (l == null) return null;
        // 路径强制指定：即使默认加载器不接管，只要路径命中且加载器支持即用（开发者强行设定）
        if (l.supports(path, file, sizeBytes, contentType)) return l;
        return null;
    }
}
