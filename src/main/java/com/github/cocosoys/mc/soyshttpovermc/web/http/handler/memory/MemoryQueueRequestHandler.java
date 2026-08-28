package com.github.cocosoys.mc.soyshttpovermc.web.http.handler.memory;

import com.github.cocosoys.mc.soyshttpovermc.proxy.ServerRegistry;
import com.github.cocosoys.mc.soyshttpovermc.util.HttpFrames;
import com.github.cocosoys.mc.soyshttpovermc.web.WebFrontendHandler;
import com.github.cocosoys.mc.soyshttpovermc.web.http.AbstractHttpRequestHandler;
import com.github.cocosoys.mc.soyshttpovermc.web.proto.FrameProto;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 内存队列模式：请求提交到有界 ArrayBlockingQueue，worker 线程从队列取任务处理。
 *
 * <p>类似于 RequestScheduler 的工作模式，但不经过 Bot 隧道。请求被封装为 Task 提交到队列，
 * worker 线程（默认 4 个）从队列取任务，调用 WebFrontendHandler.handle()，通过 CompletableFuture 返回结果。
 *
 * <p>延迟略高于 Netty EventLoop 模式（~2-5ms），但支持背压（队列满时返回 503），适用于高并发场景。支持跨服路由。</p>
 */
public class MemoryQueueRequestHandler extends AbstractHttpRequestHandler {

    private final ArrayBlockingQueue<Task> queue;
    private final ExecutorService workers;
    private volatile boolean running = true;

    private static class Task {
        final String method;
        final String path;
        final Map<String, String> headers;
        final byte[] body;
        final CompletableFuture<FrameProto.HttpResponseFrame> future;

        Task(String method, String path, Map<String, String> headers, byte[] body,
             CompletableFuture<FrameProto.HttpResponseFrame> future) {
            this.method = method;
            this.path = path;
            this.headers = headers;
            this.body = body;
            this.future = future;
        }
    }

    public MemoryQueueRequestHandler(WebFrontendHandler web, ServerRegistry registry, String localServerName) {
        this(web, registry, localServerName, 1024, 4);
    }

    public MemoryQueueRequestHandler(WebFrontendHandler web, ServerRegistry registry,
                                       String localServerName, int queueCapacity, int workerThreads) {
        super(web, registry, localServerName);
        this.queue = new ArrayBlockingQueue<>(Math.max(1, queueCapacity));
        this.workers = Executors.newFixedThreadPool(Math.max(1, workerThreads), r -> {
            Thread t = new Thread(r, "HTTP-Over-MC-Queue-Worker");
            t.setDaemon(true);
            return t;
        });
        for (int i = 0; i < Math.max(1, workerThreads); i++) {
            workers.execute(this::workerLoop);
        }
    }

    private void workerLoop() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                Task task = queue.take();
                try {
                    FrameProto.HttpResponseFrame resp = web.handle(task.method, task.path, task.headers, task.body);
                    task.future.complete(resp);
                } catch (Throwable t) {
                    task.future.completeExceptionally(t);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    @Override
    protected FrameProto.HttpResponseFrame handleLocal(String method, String path,
                                                          Map<String, String> headers, byte[] body)
            throws Exception {
        CompletableFuture<FrameProto.HttpResponseFrame> future = new CompletableFuture<>();
        Task task = new Task(method, path, headers, body, future);
        if (!queue.offer(task)) {
            return HttpFrames.jsonError(503, "Service Unavailable (queue full)");
        }
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } catch (ExecutionException e) {
            throw (Exception) e.getCause();
        }
    }

    @Override
    public void shutdown() {
        running = false;
        workers.shutdownNow();
    }

    @Override
    public String name() {
        return "memory-queue";
    }
}
