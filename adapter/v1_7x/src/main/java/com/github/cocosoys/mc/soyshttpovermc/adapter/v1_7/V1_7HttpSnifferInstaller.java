package com.github.cocosoys.mc.soyshttpovermc.adapter.v1_7;

import lombok.CustomLog;
import com.github.cocosoys.mc.soyshttpovermc.web.http.sniffer.HttpSnifferDeps;
import com.github.cocosoys.mc.soyshttpovermc.web.http.sniffer.HttpSnifferInstaller;


/**
 * 1.7.x {@link HttpSnifferInstaller} 实现：经 {@code META-INF/services} 注册，
 * core 在 {@code initSniffer} 装配时优先加载（版本判断在本模块，core 无感知）。
 */
@CustomLog
public class V1_7HttpSnifferInstaller implements HttpSnifferInstaller {

    @Override
    public String id() {
        return "v1_7x";
    }

    @Override
    public boolean supported() {
        // 1.7.2+ 内嵌 relocate netty（net.minecraft.util.io.netty.*），支持同端口嗅探
        return true;
    }

    @Override
    public Object install(HttpSnifferDeps deps) throws Exception {
        V1_7HttpSniffer sniffer = new V1_7HttpSniffer(deps);
        Object handle = sniffer.install();
        log.info("[adapter/v1_7] 版本兼容嗅探器已安装（relocate netty 反射桥）");
        return handle;
    }

    @Override
    public void uninstall(Object handle) {
        if (handle instanceof V1_7HttpSniffer) {
            ((V1_7HttpSniffer) handle).uninstall();
        }
    }
}
