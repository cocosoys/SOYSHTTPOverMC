package com.github.cocosoys.mc.soyshttpovermc.web.http.sniffer;

import lombok.CustomLog;

import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link HttpSnifferInstaller} 门面：通过 ServiceLoader 加载各版本 adapter 注册的安装器，
 * 返回第一个 {@code supported()} 的实现；无命中时返回 {@link HttpSnifferInstaller.Unsupported}。
 *
 * <p>与 {@code Platforms.find()} 同款手法：使用 {@code HttpSnifferInstallers.class.getClassLoader()}
 * 加载——在 fat jar（v1_6x/v1_7x）内，本类与 services 由同一插件 classloader 加载，必然可见。</p>
 */
@CustomLog
public final class HttpSnifferInstallers {
    private static volatile HttpSnifferInstaller cached;

    private HttpSnifferInstallers() {
    }

    /**
     * 返回当前服务端可用的版本兼容嗅探器安装器；无命中返回 {@link HttpSnifferInstaller.Unsupported}。
     */
    public static HttpSnifferInstaller find() {
        HttpSnifferInstaller c = cached;
        if (c != null) {
            return c;
        }
        try {
            ServiceLoader<HttpSnifferInstaller> sl = ServiceLoader.load(
                    HttpSnifferInstaller.class, HttpSnifferInstallers.class.getClassLoader());
            HttpSnifferInstaller first = null;
            for (HttpSnifferInstaller inst : sl) {
                if (first == null) {
                    first = inst;
                }
                if (inst.supported()) {
                    cached = inst;
                    log.infoT("log.sniffer.hit", "HttpSnifferInstaller 命中: {0}", inst.id());
                    return inst;
                }
                log.infoT("log.sniffer.unsupported", "HttpSnifferInstaller 不支持: {0}", inst.id());
            }
            if (first != null) {
                log.infoT("log.sniffer.scanned-none", "HttpSnifferInstaller 已扫描但均不支持: {0}", first.getClass().getName());
            } else {
                log.infoT("log.sniffer.no-services", "HttpSnifferInstaller 无可用实现（未注册 services）");
            }
        } catch (Throwable t) {
            log.warnT("log.sniffer.load-failed", "HttpSnifferInstaller 加载失败", t);
        }
        return new HttpSnifferInstaller.Unsupported("no supported installer");
    }
}
