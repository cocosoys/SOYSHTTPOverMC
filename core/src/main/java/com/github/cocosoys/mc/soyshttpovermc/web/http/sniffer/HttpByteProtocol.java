package com.github.cocosoys.mc.soyshttpovermc.web.http.sniffer;

import com.github.cocosoys.mc.soyshttpovermc.enums.RequestMethod;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * 版本无关的 HTTP 字节级协议工具：首包三分类（明文 HTTP / TLS / MC）、请求解析、响应辅助。
 *
 * <p>纯 {@code byte[]} 操作，不依赖任何 netty 类型，供以下三方共用：</p>
 * <ul>
 *   <li>core {@link SocketSniffer}（1.12.2 标准 netty pipeline）；</li>
 *   <li>v1_7x 嗅探器（relocate netty 反射桥，读 relocate ByteBuf 后转 byte[]）；</li>
 *   <li>v1_6x 嗅探器（连接级接入，直接读原生 Socket 流）。</li>
 * </ul>
 *
 * <p>协议分类/解析/响应构建属版本无关逻辑，集中在本类维护一份；
 * 传输层与连接操作（pipeline 注入 / 阻塞 socket 接管）由各版本模块自行实现。</p>
 */
public final class HttpByteProtocol {

    private HttpByteProtocol() {
    }

    /**
     * 首包分类结果（与 {@code SnifferChannelState} 语义一致，但独立于 core 业务枚举以便低版本复用）。
     */
    public enum State {
        UNKNOWN, HTTP_PLAIN, HTTP_TLS, MC
    }

    /**
     * 解析出的 HTTP 请求（不依赖 netty，纯字节）。
     */
    public static final class ParsedRequest {
        public String method;
        public String path;
        public String version;
        public Map<String, String> headers = new HashMap<>();
        public byte[] body;
    }

    private static final String[] METHODS = RequestMethod.toList();

    /**
     * 嗅探分类：依据首包前几个字节判断为明文 HTTP / TLS / MC。
     *
     * @param buf        首包字节（可含更多）
     * @param len        有效字节数（&le; buf.length）
     * @param tlsEnabled 是否启用 TLS（false 时 0x16 0x03 不再判 TLS，直接按 MC 处理）
     */
    public static State classify(byte[] buf, int len, boolean tlsEnabled) {
        if (buf == null || len <= 0) {
            return State.UNKNOWN;
        }
        byte b0 = buf[0];

        if (tlsEnabled && b0 == 0x16) {
            if (len < 3) {
                return State.UNKNOWN;
            }
            byte b1 = buf[1];
            byte b2 = buf[2];
            if (b1 == 0x03 && (b2 == 0x01 || b2 == 0x02 || b2 == 0x03)) {
                return State.HTTP_TLS;
            }
        }

        if (b0 < 'A' || b0 > 'Z') {
            return State.MC;
        }

        int i = 0;
        StringBuilder tok = new StringBuilder();
        boolean tokenComplete = false;
        for (; i < len && i < 16; i++) {
            byte b = buf[i];
            if (b == ' ') {
                tokenComplete = true;
                break;
            }
            if (b < 'A' || b > 'Z') {
                return State.MC;
            }
            tok.append((char) b);
        }
        if (!tokenComplete) {
            return State.UNKNOWN;
        }
        boolean known = false;
        for (String m : METHODS) {
            if (m.equals(tok.toString())) {
                known = true;
                break;
            }
        }
        if (!known) {
            return State.MC;
        }

        int j = i + 1;
        int k = j;
        int limit = Math.min(len, j + 200);
        boolean foundNewline = false;
        for (; k < limit; k++) {
            if (buf[k] == '\n') {
                foundNewline = true;
                break;
            }
        }
        String line = new String(buf, j, Math.max(0, k - j), StandardCharsets.US_ASCII);
        if (line.contains(" HTTP/")) {
            return State.HTTP_PLAIN;
        }
        if (foundNewline) {
            return State.MC;
        }
        return State.UNKNOWN;
    }

    /**
     * 尝试从缓冲解析完整 HTTP 请求；不完整返回 null。
     *
     * @param buf    缓冲（可含多请求/后续字节）
     * @param offset 起始偏移
     * @param len    有效字节数
     */
    public static ParsedRequest tryParseHttp(byte[] buf, int offset, int len) {
        int idx = offset;
        int end = offset + len;
        int headerEnd = indexOf(buf, idx, end, new byte[]{'\r', '\n', '\r', '\n'});
        int sepLen;
        if (headerEnd >= 0) {
            sepLen = 4;
        } else {
            headerEnd = indexOf(buf, idx, end, new byte[]{'\n', '\n'});
            if (headerEnd < 0) {
                return null;
            }
            sepLen = 2;
        }
        int headerLen = headerEnd - idx;
        if (headerLen < 0) {
            return null;
        }
        String headerText = new String(buf, idx, headerLen, StandardCharsets.US_ASCII);
        String[] lines = headerText.split("\r\n");
        if (lines.length == 0) {
            return null;
        }
        String[] reqLine = lines[0].split(" ");
        if (reqLine.length < 3) {
            return null;
        }
        String method = reqLine[0];
        String path = reqLine[1];
        String version = reqLine[2];

        Map<String, String> headers = new HashMap<>();
        int contentLength = 0;
        for (int l = 1; l < lines.length; l++) {
            int colon = lines[l].indexOf(':');
            if (colon > 0) {
                String k = lines[l].substring(0, colon).trim();
                String v = lines[l].substring(colon + 1).trim();
                headers.put(k, v);
                if (k.equalsIgnoreCase("Content-Length")) {
                    try {
                        contentLength = Integer.parseInt(v);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        int bodyStart = headerEnd + sepLen;
        int available = end - bodyStart;
        if (available < contentLength) {
            return null;
        }
        if (contentLength < 0) {
            contentLength = 0;
        }
        byte[] body = new byte[contentLength];
        if (contentLength > 0) {
            System.arraycopy(buf, bodyStart, body, 0, contentLength);
        }

        ParsedRequest r = new ParsedRequest();
        r.method = method;
        r.path = path;
        r.version = version;
        r.headers = headers;
        r.body = body;
        return r;
    }

    /**
     * 字节序列查找（返回命中起点，未命中返回 -1）。
     */
    public static int indexOf(byte[] buf, int from, int to, byte[] seq) {
        int sl = seq.length;
        if (sl == 0) {
            return from;
        }
        for (int i = from; i + sl <= to; i++) {
            boolean ok = true;
            for (int j = 0; j < sl; j++) {
                if (buf[i + j] != seq[j]) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                return i;
            }
        }
        return -1;
    }

    /**
     * HTTP 状态码文本。
     */
    public static String statusText(int code) {
        switch (code) {
            case 200:
                return "OK";
            case 304:
                return "Not Modified";
            case 400:
                return "Bad Request";
            case 401:
                return "Unauthorized";
            case 403:
                return "Forbidden";
            case 413:
                return "Payload Too Large";
            case 426:
                return "Upgrade Required";
            case 429:
                return "Too Many Requests";
            case 500:
                return "Internal Server Error";
            case 502:
                return "Bad Gateway";
            case 503:
                return "Service Unavailable";
            default:
                return "Status";
        }
    }

    /**
     * 是否为可压缩的响应内容类型（二进制媒体如 png/jpg/woff 已自带压缩，不重复压缩）。
     */
    public static boolean isCompressible(String contentType) {
        if (contentType == null) {
            return false;
        }
        String c = contentType.toLowerCase();
        if (c.startsWith("text/")) {
            return true;
        }
        if (c.startsWith("application/javascript") || c.startsWith("application/json")
                || c.startsWith("application/xml") || c.startsWith("application/atom+xml")
                || c.startsWith("application/ld+json") || c.startsWith("application/x-javascript")
                || c.startsWith("image/svg+xml")) {
            return true;
        }
        return false;
    }

    /**
     * gzip 压缩（失败时返回原字节）。
     */
    public static byte[] gzip(byte[] data) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length);
            try (GZIPOutputStream gz = new GZIPOutputStream(bos)) {
                gz.write(data);
            }
            return bos.toByteArray();
        } catch (IOException e) {
            return data;
        }
    }

    /**
     * 实体摘要（SHA-256 十六进制），用作 ETag 基准（基于压缩前原文，避免编码不一致）。
     */
    public static String sha256hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(data);
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte x : d) {
                sb.append(String.format("%02x", x & 0xFF));
            }
            return sb.toString();
        } catch (Exception e) {
            // 兜底：简单长度+hash，保证 304 仍可工作
            int h = java.util.Arrays.hashCode(data);
            return String.format("%08x", h);
        }
    }

    /**
     * 是否 keep-alive：HTTP/1.1 默认复用，除非客户端显式 Connection: close；HTTP/1.0 需显式 keep-alive。
     */
    public static boolean isKeepAlive(String version, Map<String, String> headers) {
        String conn = headers == null ? null : headers.get("Connection");
        boolean connClose = conn != null && conn.toLowerCase().contains("close");
        if (connClose) {
            return false;
        }
        boolean connKeep = conn != null && conn.toLowerCase().contains("keep-alive");
        boolean http11 = version != null && version.contains("HTTP/1.1");
        return http11 || connKeep;
    }

    /**
     * 缓存策略：静态资源可公开缓存；API / 鉴权端点禁止缓存。
     */
    public static String cacheControlFor(String path, String contentType) {
        String p = path == null ? "" : path;
        if (p.startsWith("/api/") || p.startsWith("/auth/")) {
            return "no-store";
        }
        if (contentType != null && contentType.startsWith("application/json")) {
            return "no-store";
        }
        return "public, max-age=300";
    }
}
