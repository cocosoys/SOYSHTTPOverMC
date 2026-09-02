package com.github.cocosoys.mc.soyshttpovermc.web;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 隧道 HTTP 请求统计：累计计数、GET/POST 拆分、端到端延迟（微秒）、近期请求环形缓冲。
 * 由 SocketSniffer 在每次隧道往返后统一记录（含真实延迟与响应码），避免重复计数。
 */
public class RequestStats {

    private final long startTime = System.currentTimeMillis();

    private final AtomicLong total = new AtomicLong(0);
    private final AtomicLong getCount = new AtomicLong(0);
    private final AtomicLong postCount = new AtomicLong(0);
    private final AtomicLong otherCount = new AtomicLong(0);

    private final AtomicLong latSumUs = new AtomicLong(0);
    private final AtomicLong latCount = new AtomicLong(0);
    private final AtomicLong latMaxUs = new AtomicLong(0);

    private final ConcurrentLinkedDeque<RecentReq> recent = new ConcurrentLinkedDeque<>();
    private static final int MAX_RECENT = 25;

    /**
     * 记录一次已完成（无论成功/失败）的 HTTP 请求隧道往返
     */
    public void recordRequest(String method, String path, int code, long latencyMicros) {
        total.incrementAndGet();
        String m = method == null ? "" : method.toUpperCase();
        if ("GET".equals(m)) getCount.incrementAndGet();
        else if ("POST".equals(m)) postCount.incrementAndGet();
        else otherCount.incrementAndGet();

        if (latencyMicros >= 0) {
            latSumUs.addAndGet(latencyMicros);
            latCount.incrementAndGet();
            long cur = latMaxUs.get();
            while (latencyMicros > cur && !latMaxUs.compareAndSet(cur, latencyMicros)) {
                cur = latMaxUs.get();
            }
        }
        recent.addLast(new RecentReq(m, path, code, latencyMicros));
        while (recent.size() > MAX_RECENT) recent.pollFirst();
    }

    public long getStartTime() {
        return startTime;
    }

    public long getTotal() {
        return total.get();
    }

    public long getGetCount() {
        return getCount.get();
    }

    public long getPostCount() {
        return postCount.get();
    }

    public long getOtherCount() {
        return otherCount.get();
    }

    /**
     * 平均端到端延迟（毫秒），无样本返回 -1
     */
    public double getAvgLatencyMs() {
        long c = latCount.get();
        return c == 0 ? -1 : (latSumUs.get() / (double) c) / 1000.0;
    }

    /**
     * 最大端到端延迟（毫秒）
     */
    public double getMaxLatencyMs() {
        long m = latMaxUs.get();
        return m <= 0 ? -1 : m / 1000.0;
    }

    public List<RecentReq> getRecent() {
        return new ArrayList<>(recent);
    }

    public static final class RecentReq implements java.io.Serializable {
        private static final long serialVersionUID = 1L;

        public final String method;
        public final String path;
        public final int code;
        public final long latencyMicros;

        RecentReq(String method, String path, int code, long latencyMicros) {
            this.method = method;
            this.path = path;
            this.code = code;
            this.latencyMicros = latencyMicros;
        }

        public double latencyMs() {
            return latencyMicros < 0 ? -1 : latencyMicros / 1000.0;
        }
    }
}
