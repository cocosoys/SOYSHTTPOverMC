package soys.soyshttpovermc.web;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;

/**
 * 默认大文件加载器：流式分块读取（64KB 块），不写入 Web 内容缓存——
 * 大文件每次请求独立加载，避免长期驻留内存；同时为将来演进为
 * 「分块边读边写响应」预留语义。
 */
public class DefaultLargeFileLoader implements LargeFileLoader {

    public static final String NAME = "stream";

    private final long thresholdBytes;

    public DefaultLargeFileLoader(long thresholdBytes) {
        this.thresholdBytes = Math.max(0, thresholdBytes);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean supports(String path, File file, long sizeBytes, String contentType) {
        // 仅接管磁盘文件（大小可知）；jar 内资源由调用方走常规缓存路径
        if (file == null) return false;
        long size = sizeBytes < 0 ? (file.isFile() ? file.length() : 0) : sizeBytes;
        return thresholdBytes > 0 && size > thresholdBytes;
    }

    @Override
    public byte[] load(String path, File file) throws Exception {
        try (InputStream in = new FileInputStream(file);
             ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.min(file.length(), 1024L * 1024L))) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }
}
