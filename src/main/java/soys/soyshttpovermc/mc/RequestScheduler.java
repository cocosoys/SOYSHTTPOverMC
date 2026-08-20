package soys.soyshttpovermc.mc;
import lombok.CustomLog;

import soys.soyshttpovermc.bot.BotTier;
import soys.soyshttpovermc.i18n.I18n;
import soys.soyshttpovermc.log.LogKit;
import soys.soyshttpovermc.proto.FrameProto;
import soys.soyshttpovermc.util.HttpFrames;
import soys.soyshttpovermc.web.WebFrontendHandler;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.google.protobuf.ByteString;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 按 Bot 队列优先级处理 API 请求：单物理 Bot 隧道 + 多逻辑队列（tier）。
 * <ul>
 *   <li>每个 tier 一个<b>有界队列</b>（容量可配），过载即背压；</li>
 *   <li>worker 线程池优先 drain ADMIN 队列（高优先级），再 drain COMMON，避免 admin 被饿死；</li>
 *   <li>队列满 → 立即经隧道回 503（非阻塞，不拖入站/netty/主线程）；</li>
 *   <li>重活（web.handle / 业务 handler）在 worker 线程执行，<b>不阻塞</b>入站与主线程；
 *       仅响应写回（轻量）调度回主线程，确保 sendPluginMessage 线程安全。</li>
 * </ul>
 */
@CustomLog
public class RequestScheduler {

    private final JavaPlugin plugin;
    private final String channel;
    private final WebFrontendHandler web;
    private final Map<BotTier, ArrayBlockingQueue<Task>> queues = new EnumMap<>(BotTier.class);
    private final ExecutorService pool;

    public RequestScheduler(JavaPlugin plugin, String channel, WebFrontendHandler web,
                            int commonCap, int adminCap, int workers) {
        this.plugin = plugin;
        this.channel = channel;
        this.web = web;
        queues.put(BotTier.COMMON, new ArrayBlockingQueue<>(commonCap));
        queues.put(BotTier.ADMIN, new ArrayBlockingQueue<>(adminCap));
        int n = Math.max(1, workers);
        this.pool = Executors.newFixedThreadPool(n, r -> {
            Thread t = new Thread(r, "HTTP-Over-MC-Worker");
            t.setDaemon(true);
            return t;
        });
        for (int i = 0; i < n; i++) {
            pool.execute(this::loop);
        }
        log.info(I18n.t("log.mc.scheduler-started", "请求调度器已启动: workers={0} commonCap={1} adminCap={2}", n, commonCap, adminCap));
    }

    /** 提交一次已重组完成的请求到对应 tier 队列；队列满则背压回 503。 */
    public void submit(BotTier tier, Player player, long requestId,
                       String method, String path, Map<String, String> headers, byte[] body) {
        BotTier t = tier == null ? BotTier.COMMON : tier;
        Task task = new Task(player, requestId, method, path, headers, body);
        if (!queues.get(t).offer(task)) {
            log.warn(I18n.t("log.mc.queue-full-503", "tier={0} 队列已满，背压返回 503: {1} {2}", t, method, path));
            writeResponse(player, buildStatus(requestId, 503, "Service Unavailable (queue full)"));
        }
    }

    /** 空闲时 worker 阻塞在 ADMIN 上的最长等待（毫秒）；COMMON 到达最多延迟此时长后被兜底轮询。 */
    private static final long PARK_MS = 50L;

    private void loop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                // 高优先级 ADMIN 队列优先；COMMON 仅作机会性轮询，<b>绝不</b>调用 COMMON.take() 阻塞，
                // 否则 worker 会永久停在 COMMON 队列的 take() 上，ADMIN 任务到达也无法唤醒 -> 死锁/饥饿（已修复）。
                Task task = queues.get(BotTier.ADMIN).poll();
                if (task == null) {
                    task = queues.get(BotTier.COMMON).poll();
                }
                if (task == null) {
                    // 两队列皆空：阻塞在 ADMIN（高优先级）上等待唤醒；COMMON 到达最多延迟 PARK_MS 后被兜底轮询（可接受）。
                    task = queues.get(BotTier.ADMIN).poll(PARK_MS, TimeUnit.MILLISECONDS);
                }
                if (task == null) {
                    // 仍为 null：回到循环顶部重查（ADMIN.poll 立即返回 null，紧接着 COMMON.poll 兜底），不空转占用 CPU。
                    continue;
                }
                process(task);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable e) {
                log.warn(I18n.t("log.mc.worker-err", "调度器 worker 异常: {0}", e));
            }
        }
    }

    private void process(Task task) {
        try {
            FrameProto.HttpResponseFrame resp = web.handle(task.method, task.path, task.headers, task.body);
            final FrameProto.HttpResponseFrame out = resp.toBuilder().setRequestId(task.requestId).build();
            final Player p = task.player;
            Bukkit.getScheduler().runTask(plugin, () -> writeResponse(p, out));
        } catch (Throwable e) {
            log.warn(I18n.t("log.mc.process-err", "请求处理异常 method={0} path={1}: {2}", task.method, task.path, e));
            writeResponse(task.player, buildStatus(task.requestId, 500, "Internal Server Error"));
        }
    }

    /** 把响应帧按 32000 字节分片，经 Bot 通道回写（应在主线程调用）。 */
    private void writeResponse(Player player, FrameProto.HttpResponseFrame resp) {
        if (player == null || !player.isOnline()) {
            return;
        }
        try {
            byte[] data = resp.toByteArray();
            final int MAX = 32000;
            int n = (data.length <= MAX) ? 1 : (data.length + MAX - 1) / MAX;
            for (int i = 0; i < n; i++) {
                int s = i * MAX;
                int e = Math.min(data.length, s + MAX);
                FrameProto.HttpResponseFrame c = resp.toBuilder()
                        .clearBody()
                        .setBody(ByteString.copyFrom(Arrays.copyOfRange(data, s, e)))
                        .setFragmentIndex(i)
                        .setTotalFragments(n)
                        .build();
                player.sendPluginMessage(plugin, channel, c.toByteArray());
            }
        } catch (Throwable t) {
            log.warn(I18n.t("log.mc.write-response-err", "响应写回异常 id={0}: {1}", resp.getRequestId(), t));
        }
    }

    private static FrameProto.HttpResponseFrame buildStatus(long requestId, int code, String msg) {
        // 统一 JSON 信封（{code,msg,data}），与全局响应格式一致，无 text/plain 例外
        return HttpFrames.jsonError(code, msg).toBuilder().setRequestId(requestId).build();
    }

    /** 停止 worker 线程池。 */
    public void shutdown() {
        try {
            pool.shutdownNow();
        } catch (Throwable ignored) {
        }
    }

    private static class Task {
        final Player player;
        final long requestId;
        final String method;
        final String path;
        final Map<String, String> headers;
        final byte[] body;

        Task(Player player, long requestId, String method, String path, Map<String, String> headers, byte[] body) {
            this.player = player;
            this.requestId = requestId;
            this.method = method;
            this.path = path;
            this.headers = headers;
            this.body = body;
        }
    }
}
