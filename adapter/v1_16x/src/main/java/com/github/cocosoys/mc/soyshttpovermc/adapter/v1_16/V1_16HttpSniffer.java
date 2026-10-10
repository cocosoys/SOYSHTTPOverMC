package com.github.cocosoys.mc.soyshttpovermc.adapter.v1_16;

import com.github.cocosoys.mc.soyshttpovermc.web.http.sniffer.AbstractNettyHttpSniffer;
import com.github.cocosoys.mc.soyshttpovermc.web.http.sniffer.HttpSnifferDeps;

import java.util.List;

/**
 * 1.16.x netty 反射桥同端口嗅探器。
 *
 * <p>1.16 使用标准 {@code io.netty}（非 relocate）。通用注入/分流/HTTP 处理逻辑见
 * {@link AbstractNettyHttpSniffer}；本类只提供前缀与父 ServerChannel 定位
 * （复用 {@link V1_16SocketSnifferAdapter}）。</p>
 */
public class V1_16HttpSniffer extends AbstractNettyHttpSniffer {

    private static final String NETTY = "io.netty.";

    public V1_16HttpSniffer(HttpSnifferDeps deps) {
        super(deps);
    }

    @Override
    protected String nettyPackagePrefix() {
        return NETTY;
    }

    @Override
    protected String logTag() {
        return "v1_16";
    }

    @Override
    protected List<?> locateListenerChannels() {
        return new V1_16SocketSnifferAdapter().locateListenerChannels();
    }
}
