package com.github.cocosoys.mc.soyshttpovermc.spring.impl;

import com.github.cocosoys.mc.soyshttpovermc.spring.entity.LatencyEntity;
import com.github.cocosoys.mc.soyshttpovermc.spring.entity.RecentRequestEntity;
import com.github.cocosoys.mc.soyshttpovermc.spring.entity.RequestCountEntity;
import com.github.cocosoys.mc.soyshttpovermc.spring.entity.StatusEntity;
import com.github.cocosoys.mc.soyshttpovermc.spring.service.IStatusService;
import com.github.cocosoys.mc.soyshttpovermc.web.RequestStats;

import java.util.ArrayList;
import java.util.List;

/**
 * 隧道状态 Service 实现（仿 MyBatis-Plus 的 {@code XxxServiceImpl extends ServiceImpl implements XxxService}）：
 * <b>业务逻辑集中于此</b>，控制器只调用接口方法。数据来源为隧道统计 {@link RequestStats}。
 */
public class StatusServiceImpl extends BaseServiceImpl<StatusEntity> implements IStatusService {

    private final RequestStats stats;
    private final int port;
    private final String botName;

    public StatusServiceImpl(RequestStats stats, int port, String botName) {
        this.stats = stats;
        this.port = port;
        this.botName = botName == null ? "" : botName;
    }

    @Override
    public StatusEntity getStatus() {
        long up = System.currentTimeMillis() - stats.getStartTime();

        StatusEntity status = new StatusEntity();
        status.setOnline(true);
        status.setPort(port);
        status.setBot(botName);
        status.setUptimeMillis(up);
        status.setUptime(formatUptime(up));

        RequestCountEntity requests = new RequestCountEntity();
        requests.setTotal(stats.getTotal());
        requests.setGet(stats.getGetCount());
        requests.setPost(stats.getPostCount());
        requests.setOther(stats.getOtherCount());
        status.setRequests(requests);

        LatencyEntity latency = new LatencyEntity();
        latency.setAvgMs(nonNegative(stats.getAvgLatencyMs()));
        latency.setMaxMs(nonNegative(stats.getMaxLatencyMs()));
        status.setLatency(latency);

        status.setRecent(buildRecent());
        return status;
    }

    @Override
    public List<RecentRequestEntity> getRecentRequests() {
        return buildRecent();
    }

    private List<RecentRequestEntity> buildRecent() {
        List<RecentRequestEntity> recent = new ArrayList<>();
        for (RequestStats.RecentReq r : stats.getRecent()) {
            RecentRequestEntity item = new RecentRequestEntity();
            item.setMethod(r.method);
            item.setPath(r.path);
            item.setCode(r.code);
            item.setMs(nonNegative(r.latencyMs()));
            recent.add(item);
        }
        return recent;
    }

    /** 负值（无样本）→ null，否则数值（面板兼容 toFixed） */
    private static Double nonNegative(double v) {
        return v < 0 ? null : v;
    }

    private static String formatUptime(long ms) {
        long s = ms / 1000;
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        if (h > 0) return h + "h" + m + "m";
        if (m > 0) return m + "m" + sec + "s";
        return sec + "s";
    }
}
