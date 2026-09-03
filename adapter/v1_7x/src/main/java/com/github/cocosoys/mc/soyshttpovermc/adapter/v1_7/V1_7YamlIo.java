package com.github.cocosoys.mc.soyshttpovermc.adapter.v1_7;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * 1.7.x 专用 YAML UTF-8 读写（v1_7x 模块内自实现，不与 v1_6x 共享）。
 *
 * <p><b>背景</b>：1.7.10 的 {@code YamlConfiguration.loadConfiguration(File)} 内部用
 * {@code InputStreamReader(InputStream, Charset.defaultCharset())} 读取、{@code save(File)}
 * 用 {@code OutputStreamWriter(defaultCharset)} 写出——Windows 默认 GBK 下中文配置读写双端乱码；
 * 且 1.7.10 不存在 {@code loadConfiguration(Reader)} / {@code save(Writer)} 这类显式编码入口。
 * 因此本实现只走全版本均存在的 {@code loadFromString(String)} / {@code saveToString()}，
 * 文件字节显式按 UTF-8 解码 / 编码，从根上绕开平台默认编码。</p>
 *
 * <p><b>容错语义</b>（与 1.7.10 {@code YamlConfiguration.loadConfiguration(File)} 对齐）：
 * 文件不存在 / 读取或解析失败时返回空配置，不抛异常。</p>
 */
final class V1_7YamlIo {

    private V1_7YamlIo() {
    }

    /**
     * 以 UTF-8 显式读取 YAML 文件并解析。
     *
     * @param file 目标文件；不存在时返回空配置
     */
    static YamlConfiguration loadUtf8(File file) {
        YamlConfiguration cfg = new YamlConfiguration();
        if (file == null || !file.isFile()) {
            return cfg;
        }
        String content;
        try {
            content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return cfg;
        }
        try {
            cfg.loadFromString(content == null ? "" : content);
        } catch (InvalidConfigurationException | IllegalArgumentException e) {
            return new YamlConfiguration();
        }
        return cfg;
    }

    /**
     * 以 UTF-8 显式写出 YAML 文件（自动创建父目录）。
     *
     * @throws IOException 写入失败
     */
    static void saveUtf8(YamlConfiguration cfg, File file) throws IOException {
        if (cfg == null) {
            throw new IOException("配置为空，无法保存: " + file);
        }
        String text = cfg.saveToString();
        byte[] bytes = (text == null ? "" : text).getBytes(StandardCharsets.UTF_8);
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("无法创建目录: " + parent);
        }
        Files.write(file.toPath(), bytes);
    }
}
