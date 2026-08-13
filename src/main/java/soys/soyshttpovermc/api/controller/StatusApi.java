package soys.soyshttpovermc.api.controller;

import soys.soyshttpovermc.api.util.AjaxResult;
import soys.soyshttpovermc.api.annotations.ApiName;
import soys.soyshttpovermc.api.annotations.ApiPermission;
import soys.soyshttpovermc.api.annotations.GetMapping;
import soys.soyshttpovermc.api.entity.LatencyEntity;
import soys.soyshttpovermc.api.entity.RecentRequestEntity;
import soys.soyshttpovermc.api.entity.RequestCountEntity;
import soys.soyshttpovermc.api.entity.StatusEntity;
import soys.soyshttpovermc.web.RequestStats;

import java.util.ArrayList;
import java.util.List;

/**
 * 隧道状态 API（注解式书写 + 实体类返回）：
 * GET /status → 实际路由为 /api/status（auth 开启时自动加 /api 前缀）
 * → AjaxResult {code,msg,data:{online,port,bot,uptime,requests,latency,recent}}
 *
 * <p>data 为 {@link StatusEntity}（继承 BaseEntity，实现 Serializable），
 * 结构与原版平铺 JSON 一致（面板可读），延迟字段为数值/空（null）。
 */
public class StatusApi {

    private final RequestStats stats;
    private final int port;
    private final String botName;

    public StatusApi(RequestStats stats, int port, String botName) {
        this.stats = stats;
        this.port = port;
        this.botName = botName == null ? "" : botName;
    }

    @ApiName("隧道状态")
    @ApiPermission("soyshttp:api:status")
    @GetMapping("/status")
    public AjaxResult status() {
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

        List<RecentRequestEntity> recent = new ArrayList<>();
        for (RequestStats.RecentReq r : stats.getRecent()) {
            RecentRequestEntity item = new RecentRequestEntity();
            item.setMethod(r.method);
            item.setPath(r.path);
            item.setCode(r.code);
            item.setMs(nonNegative(r.latencyMs()));
            recent.add(item);
        }
        status.setRecent(recent);

        return AjaxResult.success(status);
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
