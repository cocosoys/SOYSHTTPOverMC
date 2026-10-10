package com.github.cocosoys.mc.soyshttpovermc.adapter.v1_16;

import com.github.cocosoys.mc.soyshttpovermc.web.http.sniffer.HttpSnifferDeps;
import com.github.cocosoys.mc.soyshttpovermc.web.http.sniffer.HttpSnifferInstaller;
import lombok.CustomLog;

/**
 * 1.16.x {@link HttpSnifferInstaller}：标准 io.netty 反射桥同端口嗅探。
 */
@CustomLog
public class V1_16HttpSnifferInstaller implements HttpSnifferInstaller {

    @Override
    public String id() {
        return "v1_16x";
    }

    @Override
    public boolean supported() {
        return true;
    }

    @Override
    public Object install(HttpSnifferDeps deps) throws Exception {
        V1_16HttpSniffer sniffer = new V1_16HttpSniffer(deps);
        Object handle = sniffer.install();
        log.infoT("log.adapter.v116.installer-installed", "[adapter/v1_16] 版本兼容嗅探器已安装（io.netty 反射桥）");
        return handle;
    }

    @Override
    public void uninstall(Object handle) {
        if (handle instanceof V1_16HttpSniffer) {
            ((V1_16HttpSniffer) handle).uninstall();
        }
    }
}
