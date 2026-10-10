package com.github.cocosoys.mc.soyshttpovermc.adapter.v1_20;

import com.github.cocosoys.mc.soyshttpovermc.web.http.sniffer.AbstractNettyHttpSniffer;
import com.github.cocosoys.mc.soyshttpovermc.web.http.sniffer.HttpSnifferDeps;

import java.util.List;

/**
 * 1.20.x netty 反射桥同端口嗅探器。
 *
 * <p>1.20 使用标准 {@code io.netty}。通用注入/分流/HTTP 处理逻辑见
 * {@link AbstractNettyHttpSniffer}；父 ServerChannel 定位复用
 * {@link V1_20SocketSnifferAdapter}（1.17+ 无版本包前缀，方法名/字段类型双通道回退）。</p>
 */
public class V1_20HttpSniffer extends AbstractNettyHttpSniffer {

    private static final String NETTY = "io.netty.";

    public V1_20HttpSniffer(HttpSnifferDeps deps) {
        super(deps);
    }

    @Override
    protected String nettyPackagePrefix() {
        return NETTY;
    }

    @Override
    protected String logTag() {
        return "v1_20";
    }

    @Override
    protected List<?> locateListenerChannels() {
        return new V1_20SocketSnifferAdapter().locateListenerChannels();
    }
}
