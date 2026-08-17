package soys.soyshttpovermc.web;

import soys.soyshttpovermc.log.LogKit;

import java.io.File;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Web 内容存活缓存：缓解「大量 web 资源长期驻留内存」。
 * <ul>
 *   <li><b>常驻（pinned）</b>：{@code web.cache.pinned} 配置的路径/前缀直接进入常驻内存缓存，
 *       不参与淘汰、<b>不占用</b> {@code web.cache.max-bytes} 配额（用于高频/必须常驻的资源）；</li>
 *   <li><b>LRU 存活缓存</b>：其余资源按 accessOrder LRU 缓存，受
 *       {@code maxEntries}(默认 1024) / {@code maxBytes}(默认 16MB) / {@code ttlSeconds}(默认 60)
 *       三重约束——条目在 TTL 内无人再次访问即失效，超容量按最久未用淘汰；</li>
 *   <li><b>大文件</b>：超过 {@code web.large-file-threshold}（默认 = max-bytes）的资源不写入缓存，
 *       由 {@link LargeFileLoaderRegistry} 的加载器流式加载（开发者可注册/切换自定义加载方式）。</li>
 * </ul>
 * 线程安全（同步区粒度小）；热替换：磁盘文件按 {@link File#lastModified()} 失效。
 */
public class WebContentCache {

    /** LRU 缓存条目。 */
    private static final class Cached {
        final byte[] bytes;
        final long cachedAt;
        final long fileStamp; // 磁盘文件 lastModified（热替换失效用；-1=无文件）

        Cached(byte[] bytes, long fileStamp) {
            this.bytes = bytes;
            this.cachedAt = System.currentTimeMillis();
            this.fileStamp = fileStamp;
        }

        boolean expired(long now, long ttlMillis, long currentFileStamp) {
            if (fileStamp >= 0 && currentFileStamp >= 0 && fileStamp != currentFileStamp) return true; // 热替换
            return now - cachedAt > ttlMillis;
        }
    }

    private final int maxEntries;
    private final long maxBytes;
    private final long ttlMillis;
    /** 常驻路径/前缀（不淘汰、不计入 maxBytes） */
    private final Set<String> pinnedPatterns;
    /** 常驻缓存：path -> bytes（常驻内容直接进内存） */
    private final Map<String, byte[]> pinnedBytes = new ConcurrentHashMap<>();
    /** LRU（accessOrder） */
    private final Map<String, Cached> lru = new LinkedHashMap<String, Cached>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Cached> eldest) {
            return false; // 手动淘汰（受 maxEntries/maxBytes/TTL 约束）
        }
    };
    private long lruBytes = 0;
    private final LargeFileLoaderRegistry largeLoaders;

    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();

    public WebContentCache(int maxEntries, long maxBytes, long ttlSeconds,
                           Set<String> pinned, LargeFileLoaderRegistry largeLoaders) {
        this.maxEntries = Math.max(1, maxEntries);
        this.maxBytes = Math.max(0, maxBytes);
        this.ttlMillis = Math.max(0, ttlSeconds) * 1000L;
        this.pinnedPatterns = pinned == null ? Collections.emptySet() : new LinkedHashSet<>(pinned);
        this.largeLoaders = largeLoaders;
    }

    /** 某路径是否命中常驻列表（精确匹配或前缀匹配）。 */
    public boolean isPinned(String path) {
        if (path == null || pinnedPatterns.isEmpty()) return false;
        for (String p : pinnedPatterns) {
            if (p == null || p.isEmpty()) continue;
            if (p.endsWith("/") ? path.startsWith(p) : path.equals(p)) return true;
        }
        return false;
    }

    /**
     * 统一取字节入口（磁盘静态资源）：pinned → 大文件加载器 → LRU。
     *
     * @param path     请求路径（/ 开头）
     * @param file     磁盘文件（null 时不做大文件/热替换判定）
     * @param loader   实际读取提供者（miss 时调用）
     */
    public byte[] bytes(String path, File file, Supplier<byte[]> loader) {
        long stamp = file == null ? -1 : (file.isFile() ? file.lastModified() : -1);
        long size = file == null ? -1 : (file.isFile() ? file.length() : -1);
        if (isPinned(path)) {
            byte[] b = pinnedBytes.get(path);
            if (b != null) {
                hits.incrementAndGet();
                return b;
            }
            b = safeLoad(loader);
            if (b != null) {
                pinnedBytes.put(path, b);
                LogKit.info("[HTTP-Over-MC] Web 缓存：常驻资源已加载 " + path + " (" + b.length + " B，不计入 max-bytes)");
            }
            misses.incrementAndGet();
            return b;
        }
        // 大文件：不写缓存，交给加载器（默认流式分块；开发者可注册/切换自定义加载方式）
        LargeFileLoader large = largeLoaders.resolve(path, file, size, null);
        if (large != null) {
            misses.incrementAndGet();
            try {
                return large.load(path, file);
            } catch (Exception e) {
                LogKit.warn("[HTTP-Over-MC] Web 缓存：大文件加载失败 path=" + path + " loader=" + large.name() + ": " + e);
                return null;
            }
        }
        return lruGet(path, stamp, loader);
    }

    /**
     * 统一取字节入口（无磁盘文件来源：WebRegistry 注册页 / jar 资源）。
     * pinned → LRU；大文件判定仅对磁盘文件生效（jar 内资源大小不可预知，按常规缓存）。
     */
    public byte[] bytes(String path, Supplier<byte[]> loader) {
        return bytes(path, null, loader);
    }

    private byte[] lruGet(String path, long stamp, Supplier<byte[]> loader) {
        long now = System.currentTimeMillis();
        synchronized (this) {
            Cached c = lru.get(path);
            if (c != null) {
                if (c.expired(now, ttlMillis, stamp)) {
                    lru.remove(path);
                    lruBytes -= c.bytes.length;
                } else {
                    hits.incrementAndGet();
                    return c.bytes;
                }
            }
        }
        misses.incrementAndGet();
        byte[] b = safeLoad(loader);
        if (b == null) return null;
        synchronized (this) {
            Cached old = lru.put(path, new Cached(b, stamp));
            if (old != null) lruBytes -= old.bytes.length;
            lruBytes += b.length;
            evictLocked(now);
        }
        return b;
    }

    /** 容量/条数/TTL 淘汰（必须在持有锁时调用）。 */
    private void evictLocked(long now) {
        if (lru.isEmpty()) return;
        Iterator<Map.Entry<String, Cached>> it = lru.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Cached> e = it.next();
            if (e.getValue().expired(now, ttlMillis, -1)) {
                lruBytes -= e.getValue().bytes.length;
                it.remove();
            }
        }
        // 超条数：按 accessOrder 最久未用淘汰
        while (lru.size() > maxEntries) {
            Iterator<Map.Entry<String, Cached>> it2 = lru.entrySet().iterator();
            if (!it2.hasNext()) break;
            Map.Entry<String, Cached> eldest = it2.next();
            lruBytes -= eldest.getValue().bytes.length;
            it2.remove();
        }
        // 超字节：同样淘汰最久未用
        while (lruBytes > maxBytes && !lru.isEmpty()) {
            Iterator<Map.Entry<String, Cached>> it3 = lru.entrySet().iterator();
            if (!it3.hasNext()) break;
            Map.Entry<String, Cached> eldest = it3.next();
            lruBytes -= eldest.getValue().bytes.length;
            it3.remove();
        }
    }

    private static byte[] safeLoad(Supplier<byte[]> loader) {
        try {
            return loader == null ? null : loader.get();
        } catch (Throwable t) {
            LogKit.warn("[HTTP-Over-MC] Web 缓存：资源加载异常: " + t);
            return null;
        }
    }

    /** 手动淘汰：供定时任务/接口调用（无需线程常驻，仅在写入时自动淘汰）。 */
    public void sweep() {
        synchronized (this) {
            evictLocked(System.currentTimeMillis());
        }
    }

    public long hits() { return hits.get(); }
    public long misses() { return misses.get(); }

    /** 当前 LRU 占用字节（不含常驻）。 */
    public synchronized long cachedBytes() { return lruBytes; }
    public synchronized int cachedEntries() { return lru.size(); }
    public long pinnedBytes() {
        long n = 0;
        for (byte[] b : pinnedBytes.values()) n += b.length;
        return n;
    }
    public int pinnedEntries() { return pinnedBytes.size(); }
}
