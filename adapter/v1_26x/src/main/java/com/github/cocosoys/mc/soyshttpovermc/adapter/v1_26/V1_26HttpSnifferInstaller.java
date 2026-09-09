package com.github.cocosoys.mc.soyshttpovermc.adapter.v1_26;

import com.github.cocosoys.mc.soyshttpovermc.web.http.sniffer.HttpSnifferDeps;
import com.github.cocosoys.mc.soyshttpovermc.web.http.sniffer.HttpSnifferInstaller;
import lombok.CustomLog;

/**
 * 26.x {@link HttpSnifferInstaller} 实现（初始化骨架）。
 *
 * <p>当前 install 返回 null，让 core 回退到内置 SocketSniffer。</p>
 */
@CustomLog
public class V1_26HttpSnifferInstaller implements HttpSnifferInstaller {

    @Override
    public String id() {
        return "v1_26x";
    }

    @Override
    public boolean supported() {
        return true;
    }

    @Override
    public Object install(HttpSnifferDeps deps) throws Exception {
        log.infoT("log.adapter.v126.installer-skip", "[adapter/v1_26] 使用内置 SocketSniffer（版本特化嗅探器待实现）");
        return null;
    }

    @Override
    public void uninstall(Object handle) {
    }
}
