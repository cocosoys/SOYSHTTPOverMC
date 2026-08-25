package com.github.cocosoys.mc.soyshttpovermc.web;

import java.io.File;

/**
 * 大文件加载抽象：为超过大文件阈值的静态资源提供可插拔的加载方式。
 *
 * <p>默认实现 {@link DefaultLargeFileLoader} 采用流式分块读取（不写入 Web 内容缓存、
 * 每次请求独立加载，避免大文件长期占用内存）；第三方插件可注册自定义加载器
 * （如内存映射、对象存储/远端拉取、专有压缩格式），并经门面
 * {@code api.getWebPage().registerLargeFileLoader(...) / setLargeFileLoader(...) / setDefaultLargeFileLoader(...)}
 * 注册、按路径强制切换与全局切换。
 */
public interface LargeFileLoader {

    /** 加载器唯一名称（注册/切换用）。 */
    String name();

    /**
     * 是否接管该资源的加载。命中任一 loader 即由该 loader 加载（默认实现按
     * 文件大小超过阈值判定；开发者自定义 loader 可按路径/类型自行判断）。
     *
     * @param path       请求路径（以 / 开头）
     * @param file       磁盘文件（jar 内资源为 null，sizeBytes 为 -1）
     * @param sizeBytes  文件大小（未知为 -1）
     * @param contentType 推断出的 Content-Type（可能为 null）
     */
    boolean supports(String path, File file, long sizeBytes, String contentType);

    /**
     * 加载字节内容。当前响应模型下返回完整字节数组；
     * 默认实现为流式分块读取（不缓存、不驻留内存）。
     */
    byte[] load(String path, File file) throws Exception;
}
