package com.github.cocosoys.mc.soyshttpovermc.adapter.v1_16;

import com.github.cocosoys.mc.soyshttpovermc.web.http.sniffer.HttpSnifferDeps;
import com.github.cocosoys.mc.soyshttpovermc.web.http.sniffer.HttpSnifferInstaller;
import lombok.CustomLog;

/**
 * 1.16.x {@link HttpSnifferInstaller} 实现（初始化骨架）。
 *
 * <p>当前 install 返回 null，让 core 回退到内置 SocketSniffer（1.16+ 使用标准 io.netty，
 * 与 1.12.2 结构相近，内置嗅探器可直接工作）。后续可替换为版本特化实现。</p>
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
        log.infoT("log.adapter.v116.installer-skip", "[adapter/v1_16] 使用内置 SocketSniffer（版本特化嗅探器待实现）");
        return null;
    }

    @Override
    public void uninstall(Object handle) {
    }
}
