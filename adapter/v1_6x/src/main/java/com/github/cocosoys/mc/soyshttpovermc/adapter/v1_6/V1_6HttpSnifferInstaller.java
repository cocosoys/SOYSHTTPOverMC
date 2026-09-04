package com.github.cocosoys.mc.soyshttpovermc.adapter.v1_6;

import lombok.CustomLog;
import com.github.cocosoys.mc.soyshttpovermc.web.http.sniffer.HttpSnifferDeps;
import com.github.cocosoys.mc.soyshttpovermc.web.http.sniffer.HttpSnifferInstaller;


/**
 * 1.6.x {@link HttpSnifferInstaller} 实现：经 {@code META-INF/services} 注册，
 * core 在 {@code initSniffer} 装配时优先加载（版本判断在本模块，core 无感知）。
 *
 * <p>1.6.4 无 Netty，采用「连接级接入」：反射替换 {@code DedicatedServerConnectionThread}
 * 的监听 ServerSocket，在 accept 侧做首包嗅探分流（明文 HTTP + MC；TLS 暂缓）。</p>
 */
@CustomLog
public class V1_6HttpSnifferInstaller implements HttpSnifferInstaller {

    @Override
    public String id() {
        return "v1_6x";
    }

    @Override
    public boolean supported() {
        return true;
    }

    @Override
    public Object install(HttpSnifferDeps deps) throws Exception {
        V1_6HttpSniffer sniffer = new V1_6HttpSniffer(deps);
        Object handle = sniffer.install();
        log.info("[adapter/v1_6] 版本兼容嗅探器已安装（连接级接入）");
        return handle;
    }

    @Override
    public void uninstall(Object handle) {
        if (handle instanceof V1_6HttpSniffer) {
            ((V1_6HttpSniffer) handle).uninstall();
        }
    }
}
