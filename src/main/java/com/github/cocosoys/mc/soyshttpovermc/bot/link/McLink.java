package com.github.cocosoys.mc.soyshttpovermc.bot.link;

import com.github.cocosoys.mc.soyshttpovermc.bot.InternalBot;
import com.github.cocosoys.mc.soyshttpovermc.web.proto.FrameProto;

import com.google.protobuf.ByteString;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 客户端侧（Bot 内）消息总线：替代 GeyserLink。
 * 负责 requestId 多路复用 + 大消息分片/重组。
 * 与服务端约定：通道名一致；单条帧过大时按 total_fragments / fragment_index 切分。
 */
public class McLink {

    private final InternalBot bot;
    private final String channel;
    private final AtomicLong idGen = new AtomicLong(1);
    private final Map<Long, Pending> pending = new ConcurrentHashMap<>();
    private static final int MAX_PAYLOAD = 32000;

    public McLink(InternalBot bot, String channel) {
        this.bot = bot;
        this.channel = channel;
    }

    public long nextRequestId() {
        return idGen.getAndIncrement();
    }

    public CompletableFuture<FrameProto.HttpResponseFrame> request(FrameProto.HttpRequestFrame frame) {
        long id = frame.getRequestId();
        List<byte[]> chunks = split(frame.toByteArray());
        int total = chunks.size();
        CompletableFuture<FrameProto.HttpResponseFrame> future = new CompletableFuture<>();
        pending.put(id, new Pending(total, future));
        for (int i = 0; i < total; i++) {
            FrameProto.HttpRequestFrame chunk = FrameProto.HttpRequestFrame.newBuilder()
                    .setRequestId(id)
                    .setMethod(frame.getMethod())
                    .setPath(frame.getPath())
                    .putAllHeaders(frame.getHeadersMap())
                    .setBody(ByteString.copyFrom(chunks.get(i)))
                    .setFragmentIndex(i)
                    .setTotalFragments(total)
                    .build();
            bot.sendChannelMessage(channel, chunk.toByteArray());
        }
        return future;
    }

    /** 由 InternalBot（主通道 httpproxy:main）或跨服枢纽（httpproxy:fwd-resp）在收到服务端 PluginMessage 时回调。
     *  按 request_id 多路复用，故不限定通道——同源同服响应与跨服回程响应共用同一 pending 表。 */
    public void onRawMessage(String ch, byte[] data) {
        if (data == null) return;
        try {
            FrameProto.HttpResponseFrame f = FrameProto.HttpResponseFrame.parseFrom(data);
            long id = f.getRequestId();
            Pending p = pending.get(id);
            if (p == null) {
                return;
            }
            p.buffers[f.getFragmentIndex()] = f.getBody().toByteArray();
            if (++p.received == f.getTotalFragments()) {
                byte[] full = join(p.buffers);
                // full 是服务端响应帧(HttpResponseFrame)的序列化结果，直接解析得到真正的响应帧，
                // 其 body 字段才是应用层响应体（可读回显文本）。不能把 full 再塞进新帧的 body，
                // 否则 servlet 会写出整段 protobuf 而非实际响应体。
                FrameProto.HttpResponseFrame fullFrame = FrameProto.HttpResponseFrame.parseFrom(full);
                pending.remove(id);
                p.future.complete(fullFrame);
            }
        } catch (Exception ignored) {
            // 忽略无法解析的杂散消息
        }
    }

    private static List<byte[]> split(byte[] data) {
        if (data.length <= MAX_PAYLOAD) {
            return Arrays.asList(data);
        }
        int n = (data.length + MAX_PAYLOAD - 1) / MAX_PAYLOAD;
        java.util.ArrayList<byte[]> list = new java.util.ArrayList<byte[]>(n);
        for (int i = 0; i < n; i++) {
            int start = i * MAX_PAYLOAD;
            int end = Math.min(data.length, start + MAX_PAYLOAD);
            list.add(Arrays.copyOfRange(data, start, end));
        }
        return list;
    }

    private static byte[] join(byte[][] parts) {
        int len = 0;
        for (byte[] p : parts) {
            len += p.length;
        }
        byte[] out = new byte[len];
        int off = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, off, p.length);
            off += p.length;
        }
        return out;
    }

    private static class Pending {
        final byte[][] buffers;
        int received = 0;
        final CompletableFuture<FrameProto.HttpResponseFrame> future;

        Pending(int total, CompletableFuture<FrameProto.HttpResponseFrame> future) {
            this.buffers = new byte[total][];
            this.future = future;
        }
    }
}
