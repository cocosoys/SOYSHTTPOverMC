package com.github.cocosoys.mc.soyshttpovermc.adapter.v1_21;

import com.github.cocosoys.mc.soyshttpovermc.web.http.sniffer.HttpSnifferDeps;
import com.github.cocosoys.mc.soyshttpovermc.web.http.sniffer.HttpSnifferInstaller;
import lombok.CustomLog;

/**
 * 1.21.x {@link HttpSnifferInstaller}：标准 io.netty 反射桥同端口嗅探。
 */
@CustomLog
public class V1_21HttpSnifferInstaller implements HttpSnifferInstaller {

    @Override
    public String id() {
        return "v1_21x";
    }

    @Override
    public boolean supported() {
        return true;
    }

    @Override
    public Object install(HttpSnifferDeps deps) throws Exception {
        V1_21HttpSniffer sniffer = new V1_21HttpSniffer(deps);
        Object handle = sniffer.install();
        log.infoT("log.adapter.v121.installer-installed", "[adapter/v1_21] 版本兼容嗅探器已安装（io.netty 反射桥）");
        return handle;
    }

    @Override
    public void uninstall(Object handle) {
        if (handle instanceof V1_21HttpSniffer) {
            ((V1_21HttpSniffer) handle).uninstall();
        }
    }
}
